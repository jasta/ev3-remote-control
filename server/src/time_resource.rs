use std::net::SocketAddr;
use std::time::{SystemTime, UNIX_EPOCH};

use coap_lite::{CoapRequest, ContentFormat, RequestType, ResponseType};
use coap_lite::link_format::{LINK_ATTR_CONTENT_FORMAT, LINK_ATTR_RESOURCE_TYPE, LinkAttributeWrite};

use crate::coap_resource_server::{CoapResource, HandlingError};
use crate::uri_query_helper::UriQueryHelper;

use serde_json::json;

pub struct TimeResource {}

impl CoapResource for TimeResource {
  fn relative_path(&self) -> &str {
    "time"
  }

  fn debug_name(&self) -> &str {
    "TimeResource"
  }

  fn is_discoverable(&self) -> bool {
    true
  }

  fn write_attributes<'a, 'b>(&self, writer: LinkAttributeWrite<'a, 'b, String>) -> LinkAttributeWrite<'a, 'b, String> {
    writer
        .attr_quoted(LINK_ATTR_RESOURCE_TYPE, "time")
        .attr_u32(LINK_ATTR_CONTENT_FORMAT, usize::from(ContentFormat::TextPlain) as u32)
  }

  fn handle(
      &self,
      request: &mut CoapRequest<SocketAddr>,
      _: Vec<String>
  ) -> Result<(), HandlingError> {
    if *request.get_method() != RequestType::Get {
      return Err(HandlingError::bad_request("Unsupported method"));
    }
    let mut reply = request.response.as_mut().ok_or(HandlingError::not_handled())?;
    let now_unixtime = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_err(|e| HandlingError::internal(e))?;

    let uri_query = UriQueryHelper::from(&request.message);
    let format = uri_query.get("format").unwrap_or("text");
    match format.to_ascii_lowercase().as_str() {
      "json" => {
        let now_as_u64 = u64::try_from(now_unixtime.as_millis())
            .map_err(|e| HandlingError::internal(e))?;
        reply.message.set_content_format(ContentFormat::ApplicationJSON);
        reply.message.payload = json!({
          "value": now_as_u64,
          "clock": "realtime",
          "unit": "milliseconds_since_epoch",
        }).to_string().into_bytes();
      },
      "text" => {
        reply.message.set_content_format(ContentFormat::TextPlain);
        reply.message.payload = format!("{}", now_unixtime.as_millis()).into_bytes();
      },
      _ => {
        return Err(
            HandlingError::with_code(
                ResponseType::BadRequest,
                format!("Unknown format {}", format)));
      },
    }

    Ok(())
  }
}
