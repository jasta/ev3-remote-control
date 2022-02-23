//! Generic implementation of RFC 7959.

use std::cmp::min;
use std::ops::Deref;
use std::time::Duration;

use coap_lite::{CoapOption, CoapRequest, MessageClass, Packet};
use log::warn;
use lru_time_cache::LruCache;

use crate::block_value::BlockValue;
use crate::coap_resource_server::HandlingError;
use crate::coap_utils;

/// The maximum amount adding a block2 option to the response could add to the total size.
const BLOCK2_OPTION_MAX_LENGTH: usize = 8;

/// Default taken from RFC 7252.
const DEFAULT_MAX_TOTAL_MESSAGE_SIZE: usize = 1152;

pub struct BlockHandler<Endpoint: Ord + Clone> {
  config: BlockHandlerConfig,

  /// Maintains a block1 and 2 cache for requests that we expect a client to soon follow-up
  /// and ask about.  If this recency requirement is not meant, the system will still work
  /// however consistency of results will suffer.
  states: LruCache<RequestCacheKey<Endpoint>, BlockState>,
}

pub struct BlockHandlerConfig {
  /// Total framed message size to offer to the peer (packet size minus transport
  /// overhead).  In an ideal world this would be computed based on the endpoint MTU or even
  /// part of a more structured Endpoint API but we're pretty far off from that today.  Just make
  /// it configurable then.
  ///
  /// Note this is _not_ the suggested block size as that is referring only to the payload body.
  /// Because we have dynamic overhead for the CoAP message itself (for example if we add
  /// more options), we need to dynamically tune this to meet the physical requirements of the
  /// link rather than just some arbitrary limiting of the payload block size.
  max_total_message_size: usize,

  /// Length of time without interaction for cached responses to live (bumped each time the client
  /// requests some portion of the response).
  cache_expiry_duration: Duration,
}

impl Default for BlockHandlerConfig {
  fn default() -> Self {
    Self {
      max_total_message_size: DEFAULT_MAX_TOTAL_MESSAGE_SIZE,
      cache_expiry_duration: Duration::from_secs(120),
    }
  }
}

impl<Endpoint: Ord + Clone> BlockHandler<Endpoint> {
  pub fn new(config: BlockHandlerConfig) -> Self {
    Self {
      states: LruCache::with_expiry_duration(config.cache_expiry_duration),
      config,
    }
  }

  /// Intercept request after it is accepted as a viable request but before we exhaustively
  /// inspect the payload (e.g. after we know a 4.04 should not be issued in response but before we
  /// parse the full payload and confirm it is correct).
  ///
  /// Returns true if the request requires Block1/2 handling and no further user processing
  /// should occur (the response will be mutated inside the request and should be sent to the peer);
  /// false otherwise and handling should proceed normally.
  pub fn intercept_request(
    &mut self, request: &mut CoapRequest<Endpoint>
  ) -> Result<bool, HandlingError> {
    let state = self.states.entry(request.deref().into()).or_insert(BlockState::default());
    let maybe_block2 = request.message
        .get_first_option_as::<BlockValue>(CoapOption::Block2)
        .and_then(|x| x.ok());
    state.last_request_block2 = maybe_block2.clone();

    if let Some(block2) = maybe_block2 {
      if let Some(ref response) = state.cached_response {
        Self::maybe_serve_cached_response(request, block2, response)?;
        return Ok(true);
      }
    }

    Ok(false)
  }

  fn maybe_serve_cached_response(
    request: &mut CoapRequest<Endpoint>,
    request_block2: BlockValue,
    cached_response: &Packet,
  ) -> Result<(), HandlingError> {
    let response = request.response.as_mut().ok_or_else(HandlingError::not_handled)?;

    Self::packet_clone_limited(&mut response.message, cached_response);

    let cached_payload = &cached_response.payload;

    let request_block_size = request_block2.size();
    let mut chunks = cached_payload
        .chunks(request_block_size)
        .skip(usize::from(request_block2.num));

    let cached_payload_chunk = chunks.next()
        .ok_or_else(|| HandlingError::bad_request(
          format!("num={}, block_size={}", request_block2.num, request_block2.size())))?;

    let response_payload = &mut response.message.payload;
    response_payload.clear();
    response_payload.extend(cached_payload_chunk);

    let response_block2 = BlockValue {
      more: chunks.next().is_some(),
      ..request_block2
    };

    response.message.set_options_as::<BlockValue>(
      CoapOption::Block2,
      [response_block2].into());

    Ok(())
  }

  /// Equivalent to `dst.clone_from(src)` with the exception of not copying message_id or
  /// payload.
  fn packet_clone_limited(dst: &mut Packet, src: &Packet) {
    dst.header.set_version(src.header.get_version());
    dst.header.set_type(src.header.get_type());
    dst.header.code = src.header.code;
    for (&option, value) in src.options() {
      dst.set_option(CoapOption::from(option), value.clone());
    }
  }

  /// Intercept a request after it has been handled but before it is to be delivered over the
  /// network.  If the payload assigned to the response is too large to be transmitted without
  /// fragmenting into blocks, the block handler will cache the response and serve it out
  /// via subsequent client requests (that in turn must be directed to [`intercept_request`]).
  ///
  /// Returns true if the response has been manipulated and is being handled using Block2
  /// fragmentation; false otherwise
  pub fn intercept_response(&mut self, request: &mut CoapRequest<Endpoint>) -> Result<bool, HandlingError> {
    let state = self.states.entry(request.deref().into()).or_insert(BlockState::default());
    if let Some(ref mut response) = request.response {
      // Don't do anything if the caller appears to be trying to implement this manually.
      if response.message.get_option(CoapOption::Block2).is_none() {
        let required_size = response.message.compute_message_size();
        if let Some(request_block2) = Self::maybe_synthesize_block2_request(
            state,
            required_size,
            response.message.payload.len(),
            self.config.max_total_message_size) {
          let cached_response = response.message.clone();
          Self::maybe_serve_cached_response(request, request_block2, &cached_response)?;
          state.cached_response = Some(cached_response);
          return Ok(true);
        }
      }
    }

    Ok(false)
  }

  /// Returns a synthetic client Block2 request if block-wise transfer is required for a response
  /// payload of size `required_size`.  This synthetic block will be used to serve
  /// the response in the same way that a real Block2 client request would serve from cache.
  fn maybe_synthesize_block2_request(
    state: &BlockState,
    required_size: usize,
    payload_size: usize,
    max_total_message_size: usize
  ) -> Option<BlockValue> {
    if required_size <= max_total_message_size {
      return None;
    }

    let expected_non_payload_size = (required_size + BLOCK2_OPTION_MAX_LENGTH) - payload_size;
    let suggested_block_size = max_total_message_size.checked_sub(expected_non_payload_size);

    let negotiated_block_size = min(
      state.last_request_block2.as_ref()
          .map(|b| b.size())
          .unwrap_or(usize::MAX),
      suggested_block_size.unwrap_or(usize::MAX));

    let block2 = BlockValue::new(
      usize::from(state.last_request_block2.as_ref().map(|b| b.num).unwrap_or(0)) /* num */,
      false /* more */,
      negotiated_block_size);

    match block2 {
      Ok(block2) => Some(block2),
      Err(e) => {
        warn!("Cannot convert block size {} to size_exponent: {}", negotiated_block_size, e);
        None
      }
    }
  }
}

#[derive(Ord, PartialOrd, Eq, PartialEq, Clone)]
struct RequestCacheKey<Endpoint: Ord + Clone> {
  /// Request type as an integer to make it easy to derive Ord.
  request_type_ord: u8,
  path: Vec<String>,
  requester: Option<Endpoint>,
}

impl<Endpoint: Ord + Clone> From<&CoapRequest<Endpoint>> for RequestCacheKey<Endpoint> {
  fn from(request: &CoapRequest<Endpoint>) -> Self {
    Self {
      request_type_ord: u8::from(MessageClass::Request(*request.get_method())),
      path: coap_utils::request_get_path_as_vec(request).unwrap_or_default(),
      requester: request.source.clone(),
    }
  }
}

#[derive(Debug, Clone, Default)]
struct BlockState {
  /// Last client request's block2 value (if any), which can either mean the client's attempt to
  /// suggest a block size or a request that came in after we expired our cache.
  last_request_block2: Option<BlockValue>,

  /// Packet we need to serve from if any future block-wise transfer requests come in.
  cached_response: Option<Packet>,
}

#[cfg(test)]
mod tests {
  use std::collections::LinkedList;
  use coap_lite::option_value::OptionValueString;
  use coap_lite::{CoapResponse, RequestType, ResponseType};
  use super::*;

  #[derive(Ord, PartialOrd, Eq, PartialEq, Clone)]
  enum TestEndpoint {
    TestClient,
  }

  #[test]
  fn test_cached_response_with_blocks() {
    let block = "0123456789\n";

    let mut harness = TestServerHarness::new(32);

    let expected_payload = block.repeat(8).into_bytes();
    let delivered_payload = expected_payload.clone();

    let mut sent_req = create_get_request("test", 1, None);
    let mut received_response = harness.exchange_messages(&mut sent_req, move |received_request| {
      let mut sent_response = received_request.response.as_mut().unwrap();
      sent_response.message.header.code = MessageClass::Response(ResponseType::Content);
      sent_response.message.payload = delivered_payload;
      InterceptPolicy::Expected
    }).unwrap();

    let mut received_payload = Vec::<u8>::new();

    let total_blocks = loop {
      received_payload.extend(received_response.message.payload.clone());

      let received_block =
          received_response.message.get_first_option_as::<BlockValue>(CoapOption::Block2).unwrap().unwrap();
      let block_size = received_block.size();
      let block_num = received_block.num;

      if !received_block.more {
        break block_num;
      }

      let sent_block = BlockValue::new(
          usize::from(block_num + 1),
          false /* more */,
          block_size).unwrap();
      let mut next_sent_req = create_get_request(
          "test",
          received_response.message.header.message_id + 1,
          Some(sent_block));

      received_response = harness.exchange_messages_using_cache(&mut next_sent_req).unwrap();

      // Make sure the caching didn't do something clowny like copy the message_id.
      assert_eq!(
          received_response.message.header.message_id,
          next_sent_req.message.header.message_id);
    };

    // Make sure that we _actually_ did block encoding :)
    assert!(total_blocks > 1);

    assert_eq!(
      String::from_utf8(received_payload).unwrap(),
      String::from_utf8(expected_payload).unwrap());
  }

  struct TestServerHarness {
    handler: BlockHandler<TestEndpoint>,
  }

  impl TestServerHarness {
    pub fn new(max_message_size: usize) -> Self {
      TestServerHarness {
        handler: BlockHandler::new(BlockHandlerConfig {
          max_total_message_size: max_message_size,
          cache_expiry_duration: Duration::from_millis(u32::MAX.into()),
        }),
      }
    }

    pub fn exchange_messages_using_cache(
      &mut self,
      sent_request: &mut CoapRequest<TestEndpoint>,
    ) -> Option<CoapResponse> {
      self.exchange_messages_internal(sent_request, true, |_| InterceptPolicy::DoNotInvoke)
    }

    pub fn exchange_messages<F>(
      &mut self,
      sent_request: &mut CoapRequest<TestEndpoint>,
      response_generator: F
    ) -> Option<CoapResponse>
        where F: FnOnce(&mut CoapRequest<TestEndpoint>) -> InterceptPolicy {
      self.exchange_messages_internal(sent_request, false, response_generator)
    }

    fn exchange_messages_internal<F>(
      &mut self,
      sent_request: &mut CoapRequest<TestEndpoint>,
      expect_intercept_request: bool,
      response_generator: F,
    ) -> Option<CoapResponse>
        where F: FnOnce(&mut CoapRequest<TestEndpoint>) -> InterceptPolicy {
      assert_eq!(
        self.handler.intercept_request(sent_request).unwrap(),
        expect_intercept_request);

      let mut received_request = sent_request.clone();
      match response_generator(&mut received_request) {
        InterceptPolicy::DoNotInvoke => {
          sent_request.response.clone()
        },
        policy => {
          assert_eq!(
            self.handler.intercept_response(&mut received_request).unwrap(),
            match policy {
              InterceptPolicy::Expected => true,
              InterceptPolicy::NotExpected => false,
              _ => panic!(),
            });

          received_request.response
        }
      }
    }
  }

  #[derive(Debug, Copy, Clone)]
  enum InterceptPolicy {
    Expected,
    NotExpected,
    DoNotInvoke,
  }

  fn create_get_request(path: &str, mid: u16, block2: Option<BlockValue>) -> CoapRequest<TestEndpoint> {
    let mut packet = Packet::new();
    packet.header.code = MessageClass::Request(RequestType::Get);

    let uri_path: LinkedList<_> = path.split('/')
        .map(|x| OptionValueString(x.to_owned()))
        .collect();
    packet.set_options_as(CoapOption::UriPath, uri_path);

    if let Some(block2) = block2 {
      packet.add_option_as(CoapOption::Block2, block2);
    }

    packet.header.message_id = mid;
    packet.payload = Vec::new();
    CoapRequest::<TestEndpoint>::from_packet(packet, TestEndpoint::TestClient)
  }
}