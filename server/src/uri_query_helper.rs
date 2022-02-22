use coap_lite::{CoapOption, Packet};

pub struct UriQueryHelper {
  query_params: Vec<(String, String)>
}

impl From<&Packet> for UriQueryHelper {
  fn from(packet: &Packet) -> Self {
    if let Some(query_option) = packet.get_option(CoapOption::UriQuery) {
      let query_params = query_option.iter()
          .fold(Vec::<(String, String)>::new(), |mut acc, option_data| {
            let item_utf8 = String::from_utf8(option_data.to_vec()).unwrap_or("".to_owned());
            let query_params = querystring::querify(item_utf8.as_str());
            acc.extend(query_params.into_iter().map(|x| (x.0.to_string(), x.1.to_string())));
            acc
          });
      Self { query_params }
    } else {
      Self { query_params: Vec::new() }
    }
  }
}

impl UriQueryHelper {
  pub fn get(&self, key: &str) -> Option<&str> {
    let value = self.query_params.iter().find(|x| x.0.eq_ignore_ascii_case(key));
    value.map(|x| x.1.as_str())
  }
}
