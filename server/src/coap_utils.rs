use std::net::SocketAddr;
use std::string::FromUtf8Error;

use coap_lite::{CoapOption, CoapRequest};

pub fn request_get_path_as_vec<Endpoint>(request: &CoapRequest<Endpoint>) -> Result<Vec<String>, FromUtf8Error> {
  request.message.get_option(CoapOption::UriPath)
      .map_or_else(
        || { Ok(vec![]) },
        |paths| {
          paths.iter()
              .map(|path| String::from_utf8(path.clone()))
              .collect::<Result<_, _>>()
        }
      )
}
