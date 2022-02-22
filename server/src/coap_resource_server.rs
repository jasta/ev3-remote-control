use std::borrow::Borrow;
use std::collections::HashMap;
use std::hash::Hasher;
use std::net::SocketAddr;
use std::string::FromUtf8Error;
use std::sync::Arc;

use coap_lite::{CoapOption, CoapRequest, CoapResponse, ContentFormat, link_format, MessageClass, ResponseType};
use coap_lite::link_format::LinkFormatWrite;
use link_format::*;
use log::info;
use log::Level::*;

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
  fn handle(&self, request: &mut CoapRequest<SocketAddr>, remaining_path: Vec<String>) -> Result<(), HandlingError>;
}

#[derive(Debug, Clone)]
pub struct HandlingError {
  code: Option<ResponseType>,
  message: String
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
  resources: Vec<CoapResourceNode>,
}

impl CoapResourceServerBuilder {
  pub fn new() -> Self {
    Self {
      core_discovery: true,
      resources: Vec::new()
    }
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
          Box::new(CoreCoapResource { resources: full_path_mapping.clone() }));
      full_path_mapping.insert(core_node.full_path, Arc::from(core_node.resource));
    }

    CoapResourceServer { full_path_mapping }
  }
}

pub struct CoapResourceServer {
  full_path_mapping: HashMap<Vec<String>, Arc<CoapResourceType>>,
}

impl CoapResourceServer {
  pub fn builder() -> CoapResourceServerBuilder {
    CoapResourceServerBuilder::new()
  }

  pub fn handle(&self, request: CoapRequest<SocketAddr>) -> Option<CoapResponse> {
    let path = coap_utils::request_get_path_as_vec(&request).unwrap_or(vec![]);
    let matched_handler_result = self.find_most_specific_handler(&path);
    if log::log_enabled!(Info) {
      info!(
        "Got {:?} {}, matched handler {}",
        request.get_method(),
        request.get_path(),
        matched_handler_result.map_or("<internal>", |(_, r)| r.debug_name()));
    }
    match matched_handler_result {
      Some((matched_path_depth, resource)) => {
        Self::dispatch_handling(resource, request, (&path[matched_path_depth..]).to_vec())
      },
      None => Self::prepare_response_without_handling(request),
    }
  }

  fn find_most_specific_handler(&self, path: &Vec<String>) -> Option<(usize, &Arc<CoapResourceType>)> {
    for search_depth in (0 .. path.len() + 1).rev() {
      let search_path = &path[0..search_depth];
      if let Some(handler) = self.full_path_mapping.get(search_path) {
        return Some((search_depth, handler));
      }
    }
    None
  }

  fn dispatch_handling(
    resource: &Arc<CoapResourceType>,
    mut request: CoapRequest<SocketAddr>,
    remaining_path: Vec<String>
  ) -> Option<CoapResponse> {
    match resource.handle(&mut request, remaining_path) {
      Ok(_) => request.response,
      Err(e) => {
        info!("Error generated within {}: {:?}", resource.debug_name(), e);
        match request.response {
          Some(mut reply) => {
            let ref mut message = reply.message;
            message.header.code = MessageClass::Response(
                e.code.unwrap_or(ResponseType::InternalServerError));
            message.set_content_format(ContentFormat::TextPlain);
            message.payload = e.message.into_bytes();
            Some(reply)
          },
          None => None,
        }
      }
    }
  }

  fn prepare_response_without_handling(request: CoapRequest<SocketAddr>) -> Option<CoapResponse> {
    match request.response {
      Some(mut reply) => {
        reply.message.header.code = MessageClass::Response(ResponseType::NotFound);
        reply.message.payload = b"No handler found".to_vec();
        Some(reply)
      },
      _ => None
    }
  }
}

struct CoreCoapResource {
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

  fn handle(&self, request: &mut CoapRequest<SocketAddr>, _: Vec<String>) -> Result<(), HandlingError> {
    let mut reply = request.response.as_mut().ok_or(HandlingError::not_handled())?;
    let mut buffer = String::new();
    let mut write = LinkFormatWrite::new(&mut buffer);

    let x: &HashMap<Vec<String>, Arc<CoapResourceType>> = self.resources.borrow();
    for (path, resource) in x {
      let full_path = "/".to_owned() + &path.join("/");
      let mut attr = write.link(full_path.as_str());
      attr = resource.write_attributes(attr);
      if let Err(e) = attr.finish() {
        log::warn!("Error writing attributes for {}: {:?}", full_path, e);
      }
    }

    reply.message.set_content_format(ContentFormat::ApplicationLinkFormat);
    reply.message.payload = buffer.into_bytes();
    Ok(())
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
    let handler = CoapResourceServerBuilder::new()
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
    let handler = CoapResourceServerBuilder::new()
        .set_core_discovery(true)
        .add_resource(Box::new(TestEchoResource {}))
        .build();

    let mut request = CoapRequest::new();
    let ref mut message = request.message;
    let ref mut header = message.header;
    request.set_path("/.well_known/core");
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
        _remaining_path: Vec<String>
    ) -> Result<(), HandlingError> {
      let mut reply = request.response.as_mut().ok_or(HandlingError::not_handled())?;
      reply.message.payload = request.message.payload.clone();
      Ok(())
    }
  }
}