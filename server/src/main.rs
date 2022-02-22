use std::borrow::Borrow;
use std::collections::{HashMap, LinkedList};
use std::io;
use std::net::{SocketAddr, ToSocketAddrs};
use std::time::{SystemTime, UNIX_EPOCH};

use clap::Parser;
use clap::Subcommand;
use coap::Server;
use coap_lite::{CoapOption, CoapRequest, CoapResponse, ContentFormat, Header, MessageClass, Packet, RequestType, ResponseType};
use coap_lite::link_format::{LINK_ATTR_CONTENT_FORMAT, LINK_ATTR_RESOURCE_TYPE, LinkAttributeWrite};
use multimap::MultiMap;
use querystring::QueryParams;
use serde_json::json;
use tokio::runtime::Runtime;

use time_resource::TimeResource;

use crate::coap_resource_server::{CoapResource, CoapResourceServer, CoapResourceServerBuilder, HandlingError};
use crate::device_resource::DevicesResource;
use crate::uri_query_helper::UriQueryHelper;

mod coap_resource_server;
mod uri_query_helper;
mod time_resource;
mod device_resource;
mod hal;
mod hal_ev3;
mod coap_utils;
mod hal_mock;
mod block_handler;
mod block_value;

#[derive(Parser)]
#[clap(author, version, about, long_about = None)]
struct Opts {
  #[clap(short, long)]
  address: Option<String>,

  #[clap(short, long)]
  port: Option<u16>,
}

fn main() {
  let opts: Opts = Opts::parse();

  env_logger::init();

  let bind_addr = determine_bind_address(opts);
  run_server_forever(bind_addr);
}

fn determine_bind_address(opts: Opts) -> (String, u16) {
  let address = opts.address.unwrap_or("0.0.0.0".to_owned());
  let port = opts.port.unwrap_or(5683);
  (address, port)
}

fn run_server_forever(addr: (String, u16)) {
  Runtime::new().unwrap().block_on(async move {
    let server_handler = CoapResourceServer::builder()
        .add_resource(Box::new(TimeResource {}))
        .add_resource(Box::new(DevicesResource {}))
        .build();

    let mut server = Server::new(addr).unwrap();
    println!("Server up on {:?}", server.socket_addr().unwrap());

    server.run(|request| async {
      server_handler.handle(request)
    }).await.unwrap();
  });
}
