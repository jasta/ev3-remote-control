use std::borrow::{Borrow, BorrowMut};
use std::collections::HashMap;
use std::error;
use std::fmt::{Display, Formatter};
use std::hash::Hasher;
use std::net::SocketAddr;
use std::string::FromUtf8Error;
use std::sync::{Arc, Mutex};

use coap_lite::link_format::*;
use coap_lite::{CoapOption, CoapRequest, CoapResponse, ContentFormat, MessageClass, ResponseType};
use coap_lite::option_value::OptionValueString;
use log::info;
use log::Level::*;
use crate::block_handler::{BlockHandler, BlockHandlerConfig};

use crate::coap_utils;

type CoapResourceType = dyn CoapResource + Send + Sync;

pub trait CoapResource {
  fn relative_path(&self) -> &str;
  fn debug_name(&self) -> &str;
  fn is_discoverable(&self) -> bool;
  fn write_attributes<'a, 'b>(
      &self,
      writer: LinkAttributeWrite<'a, 'b, String>
  ) -> LinkAttributeWrite<'a, 'b, String>;

  /// Handle an incoming client request.
  ///
  /// `remaining_path`, if non-empty, contains the path elements at the end of the URL that was not
  /// matched by the resource handler.  For example, if the handler is matched at `/foo`, but the
  /// incoming path was `/foo/bar/baz`, then dangling_path would contain `["bar", "baz"]`.  This can
  /// be very useful when implementing REST applications.  The vec is empty is the path was matched
  /// exactly.
  fn handle(&self, request: &mut CoapRequest<SocketAddr>, remaining_path: &[String]) -> Result<(), HandlingError>;
}

#[derive(Debug, Clone)]
pub struct HandlingError {
  code: Option<ResponseType>,
  message: String
}

impl Display for HandlingError {
  fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
    f.write_str(format!("Handling error {:?}: {}", self.code, self.message).as_str())
  }
}

impl std::error::Error for HandlingError {
}

impl HandlingError {
  pub fn not_handled() -> Self {
    Self { code: None, message: "Not handled".to_owned() }
  }

  pub fn not_found() -> Self { Self::with_code(ResponseType::NotFound, "Not found") }

  pub fn bad_request<T: ToString>(e: T) -> Self {
    Self::with_code(ResponseType::BadRequest, e)
  }

  pub fn internal<T: ToString>(e: T) -> Self {
    Self::with_code(ResponseType::InternalServerError, e)
  }

  pub fn method_not_supported() -> Self {
    Self::with_code(ResponseType::MethodNotAllowed, "Method not supported")
  }

  pub fn with_code<T: ToString>(code: ResponseType, e: T) -> Self {
    Self { code: Some(code), message: e.to_string() }
  }
}

pub struct CoapResourceNode {
  full_path: Vec<String>,
  resource: Box<CoapResourceType>,
}

impl CoapResourceNode {
  fn from_resource(resource: Box<CoapResourceType>) -> Self {
    let full_path: Vec<_> = Self::split_path(resource.relative_path())
        .collect();
    Self { full_path, resource }
  }

  fn from_parent_resource(base_path_stem: &str, resource_node: CoapResourceNode) -> Self {
    let full_path: Vec<_> = Self::split_path(base_path_stem)
        .chain(resource_node.full_path)
        .collect();
    let resource = resource_node.resource;
    CoapResourceNode { full_path, resource }
  }

  fn split_path(path: &str) -> impl Iterator<Item=String> + '_ {
    path
        .split('/')
        .map(|x| String::from(x))
  }
}

pub struct CoapResourceServerBuilder {
  core_discovery: bool,
  suppress_empty_core_reply: bool,
  resources: Vec<CoapResourceNode>,
}

impl CoapResourceServerBuilder {
  pub fn new() -> Self {
    Self {
      core_discovery: true,
      suppress_empty_core_reply: true,
      resources: Vec::new()
    }
  }

  pub fn set_suppress_empty_core_reply(mut self, suppress: bool) -> Self {
    self.suppress_empty_core_reply = suppress;
    self
  }

  pub fn set_core_discovery(mut self, is_enabled: bool) -> Self {
    self.core_discovery = is_enabled;
    self
  }

  pub fn add_resource(mut self, resource: Box<CoapResourceType>) -> Self {
    self.resources.push(CoapResourceNode::from_resource(resource));
    self
  }

  pub fn add_child_resources(mut self, base_path_stem: &str, registry: CoapResourceServerBuilder) -> Self {
    let as_children = registry.resources.into_iter().map(|node| {
      CoapResourceNode::from_parent_resource(base_path_stem, node)
    });
    self.resources.extend(as_children);
    self
  }

  pub fn build(self) -> CoapResourceServer {
    let mut full_path_mapping: HashMap<_, _> = self.resources.into_iter().map(|x| {
      (x.full_path, Arc::from(x.resource))
    }).collect();

    if self.core_discovery {
      let core_node = CoapResourceNode::from_resource(
          Box::new(CoreCoapResource {
            suppress_empty_response: self.suppress_empty_core_reply,
            resources: full_path_mapping.clone()
          }));
      full_path_mapping.insert(core_node.full_path, Arc::from(core_node.resource));
    }

    let block_handler = Mutex::new(BlockHandler::new(BlockHandlerConfig::default()));
    CoapResourceServer { block_handler, full_path_mapping }
  }
}

pub struct CoapResourceServer {
  block_handler: Mutex<BlockHandler<SocketAddr>>,
  full_path_mapping: HashMap<Vec<String>, Arc<CoapResourceType>>,
}

impl CoapResourceServer {
  pub fn builder() -> CoapResourceServerBuilder {
    CoapResourceServerBuilder::new()
  }

  pub fn handle(&self, mut request: CoapRequest<SocketAddr>) -> Option<CoapResponse> {
    let path = coap_utils::request_get_path_as_vec(&request).unwrap_or_default();
    let matched_handler_result = self.find_most_specific_handler(&path);
    if log::log_enabled!(Info) {
      info!(
        "Got {:?} {}, matched handler {}",
        request.get_method(),
        request.get_path(),
        matched_handler_result.as_ref().map_or("<internal>", |(_, r)| r.debug_name()));
    }
    let final_result = match matched_handler_result {
      Some((matched_path_depth, resource)) => {
        self.maybe_dispatch_to_handler(resource, &mut request, &path[matched_path_depth..])
      },
      None => Err(HandlingError::not_found()),
    };

    let desired_response = match final_result {
      Ok(_) => request.response,
      Err(err) => {
        let response_issued = Self::apply_response_from_error(&mut request, err);

        // If the error happens to need block2 handling, let's do that here...
        if response_issued {
          let _result = self.block_handler.lock().unwrap().intercept_response(&mut request);
          request.response
        } else {
          None
        }
      }
    };

    desired_response
  }

  fn find_most_specific_handler(&self, path: &[String]) -> Option<(usize, Arc<CoapResourceType>)> {
    for search_depth in (0 .. path.len() + 1).rev() {
      let search_path = &path[0..search_depth];
      if let Some(handler) = self.full_path_mapping.get(search_path) {
        return Some((search_depth, handler.clone()));
      }
    }
    None
  }

  fn maybe_dispatch_to_handler(
    &self,
    resource: Arc<CoapResourceType>,
    request: &mut CoapRequest<SocketAddr>,
    remaining_path: &[String],
  ) -> Result<(), HandlingError> {
    if !self.block_handler.lock().unwrap().intercept_request(request)? {
      resource.handle(request, remaining_path)?;
      self.block_handler.lock().unwrap().intercept_response(request)?;
    }

    Ok(())
  }

  fn apply_response_from_error(
      request: &mut CoapRequest<SocketAddr>,
      error: HandlingError
  ) -> bool {
    if let Some(reply) = &mut request.response {
      if let Some(code) = error.code {
        let message = &mut reply.message;
        message.header.code = MessageClass::Response(code);
        message.set_content_format(ContentFormat::TextPlain);
        message.payload = error.message.into_bytes();
        return true;
      }
    }
    false
  }
}

struct CoreCoapResource {
  suppress_empty_response: bool,
  resources: HashMap<Vec<String>, Arc<CoapResourceType>>
}

impl CoapResource for CoreCoapResource {
  fn relative_path(&self) -> &str {
    ".well-known/core"
  }

  fn debug_name(&self) -> &str {
    "CoRE"
  }

  fn is_discoverable(&self) -> bool {
    false
  }

  fn write_attributes<'a, 'b>(&self, writer: LinkAttributeWrite<'a, 'b, String>) -> LinkAttributeWrite<'a, 'b, String> {
    writer
  }

  fn handle(&self, request: &mut CoapRequest<SocketAddr>, _remaining_path: &[String]) -> Result<(), HandlingError> {
    let queries = coap_utils::request_get_queries(request);
    let mut reply = request.response.as_mut().ok_or(HandlingError::not_handled())?;

    let all_resources = Self::format_links(&self.resources);
    let final_reply = if !queries.is_empty() {
      // This inefficiency (formatting all, then parsing+filtering, then formatting again) is
      // an unfortunate side effect of our write_attributes API.  In hindsight, this should be a structured
      // map that's returned and then formatted on demand.  Oh well :\
      let matched_resources = self.filter_by_query(all_resources, queries)
          .map_err(|e| HandlingError::internal(format!("{:?}", e)))?;
      Self::format_links(&matched_resources)
    } else {
      all_resources
    };

    if final_reply.is_empty() && self.suppress_empty_response {
      // This is crucial for multicast as we should not reply to multicast filter requests
      // that we didn't match.
      return Err(HandlingError::not_handled());
    }

    reply.message.set_content_format(ContentFormat::ApplicationLinkFormat);
    reply.message.payload = final_reply.into_bytes();
    Ok(())
  }
}

impl CoreCoapResource {
  fn format_links(resources: &HashMap<Vec<String>, Arc<CoapResourceType>>) -> String {
    let mut buffer = String::new();
    let mut write = LinkFormatWrite::new(&mut buffer);

    for (path, resource) in resources {
      let full_path = "/".to_owned() + &path.join("/");
      let mut attr = write.link(full_path.as_str());
      attr = resource.write_attributes(attr);
      if let Err(e) = attr.finish() {
        log::warn!("Error writing attributes for {}: {:?}", full_path, e);
      }
    }

    buffer
  }

  fn filter_by_query(&self, all_links: String, queries: HashMap<String, String>) -> Result<HashMap<Vec<String>, Arc<CoapResourceType>>, ErrorLinkFormat> {
    let mut matches = HashMap::<Vec<String>, Arc<CoapResourceType>>::new();

    let parsed = LinkFormatParser::new(&all_links)
        .collect::<Result<Vec<_>, _>>()?;
    for (path, attributes) in parsed {
      let attrs_map: HashMap<_, _> = attributes
          .map(|(key, value)| {
            (key.to_string(), value.to_cow().to_string())
          })
          .collect();
      if Self::is_query_match(&attrs_map, &queries) {
        let path_vec: Vec<String> = path
            .split('/')
            .filter(|x| !x.is_empty())
            .map(|x| x.to_owned())
            .collect();
        let resource = self.resources[&path_vec].clone();
        matches.insert(path_vec, resource);
      }
    }

    Ok(matches)
  }

  fn is_query_match(attributes: &HashMap<String, String>, queries: &HashMap<String, String>) -> bool {
    for (required_key, required_value) in queries {
      match attributes.get(required_key) {
        Some(attribute_value) if attribute_value == required_value => {},
        _ => return false,
      }
    }
    true
  }
}

#[cfg(test)]
mod tests {
  use std::net::SocketAddr;

  use coap_lite::{CoapRequest, CoapResponse, Header, MessageClass, Packet, ResponseType};
  use coap_lite::link_format::{LINK_ATTR_RESOURCE_TYPE, LinkAttributeWrite};

  use crate::coap_resource_server::{CoapResource, CoapResourceServerBuilder, HandlingError};

  #[test]
  fn simple_echo_request() {
    let mut handler = CoapResourceServerBuilder::new()
        .add_resource(Box::new(TestEchoResource {}))
        .build();

    let mut request = CoapRequest::new();
    let ref mut message = request.message;
    let ref mut header = message.header;
    let test_payload = b"Echo test".to_vec();
    message.payload = test_payload.clone();
    request.set_path("/echo");
    request.response = CoapResponse::new(&Packet::new());

    let actual = handler.handle(request);

    let mut expected = CoapResponse::new(&Packet::new()).unwrap();
    expected.message.header.code = MessageClass::Response(ResponseType::Content);
    expected.message.payload = test_payload.clone();

    assert_eq!(actual.unwrap().message.payload, expected.message.payload);
  }

  #[test]
  fn core_discovery() {
    let mut handler = CoapResourceServerBuilder::new()
        .set_core_discovery(true)
        .add_resource(Box::new(TestEchoResource {}))
        .build();

    let mut request = CoapRequest::new();
    let ref mut message = request.message;
    let ref mut header = message.header;
    request.set_path("/.well-known/core");
    request.response = CoapResponse::new(&Packet::new());

    let actual = handler.handle(request);

    let mut expected = CoapResponse::new(&Packet::new()).unwrap();
    expected.message.header.code = MessageClass::Response(ResponseType::Content);
    expected.message.payload = b"</echo>;rt=echo".to_vec();

    assert_eq!(String::from_utf8(actual.unwrap().message.payload), String::from_utf8(expected.message.payload));
  }

  struct TestEchoResource {
  }

  impl CoapResource for TestEchoResource {
    fn relative_path(&self) -> &str {
      "echo"
    }

    fn debug_name(&self) -> &str {
      "EchoResource"
    }

    fn is_discoverable(&self) -> bool {
      true
    }

    fn write_attributes<'a, 'b>(&self, writer: LinkAttributeWrite<'a, 'b, String>) -> LinkAttributeWrite<'a, 'b, String> {
      writer.attr(LINK_ATTR_RESOURCE_TYPE, "echo")
    }

    fn handle(
        &self,
        request: &mut CoapRequest<SocketAddr>,
        _remaining_path: &[String]
    ) -> Result<(), HandlingError> {
      let mut reply = request.response.as_mut().ok_or(HandlingError::not_handled())?;
      reply.message.payload = request.message.payload.clone();
      Ok(())
    }
  }
}