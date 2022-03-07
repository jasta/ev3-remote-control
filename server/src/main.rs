use std::borrow::Borrow;
use std::collections::{HashMap, LinkedList};
use std::future::Future;
use std::io;
use std::net::{IpAddr, Ipv4Addr, SocketAddr, ToSocketAddrs};
use std::time::{SystemTime, UNIX_EPOCH};
use anyhow::anyhow;

use clap::Parser;
use clap::Subcommand;
use coap::Server;
use coap_lite::{CoapRequest, CoapResponse};
use multimap::MultiMap;
use querystring::QueryParams;
use serde_json::json;
use tokio::process::Command;
use tokio::runtime::Runtime;

use time_resource::TimeResource;
use crate::block_handler::{BlockHandler, BlockHandlerConfig};

use crate::coap_resource_server::{CoapResource, CoapResourceServer, CoapResourceServerBuilder, HandlingError};
use crate::device_resource::DevicesResource;
use crate::device_resource::SingleDeviceResource;
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
mod layout_resource;

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
  let address = opts.address.unwrap_or_else(|| "0.0.0.0".to_owned());
  let port = opts.port.unwrap_or(5683);
  (address, port)
}

fn run_server_forever(addr: (String, u16)) {
  Runtime::new().unwrap().block_on(async move {
    let mdns_future = run_mdns_advertisement(addr.1);
    let coap_future = run_coap_server(addr);

    tokio::try_join!(mdns_future, coap_future);
  });
}

async fn run_mdns_advertisement(port: u16) -> anyhow::Result<()> {
  // TODO: Advertised name should be based on use provided configuration.
  let mut child = Command::new("avahi-publish-service")
      .arg("ev3dev")
      .arg("_coap._udp")
      .arg("5683")
      .arg("rt=\"devices\"")
      .spawn()?;

  let status = child.wait().await?;
  Err(anyhow!("Unexpected avahi exit: {:?}", status))
}

async fn run_coap_server(addr: (String, u16)) -> anyhow::Result<()> {
  let server_handler = CoapResourceServer::builder()
      .add_resource(Box::new(TimeResource {}))
      .add_resource(Box::new(DevicesResource {}))
      .add_resource(Box::new(SingleDeviceResource {}))
      .build();

  let mut server = Server::new(addr)?;
  println!("Server up on {:?}", server.socket_addr()?);

  server.run(|request| async {
    server_handler.handle(request)
  }).await?;

  Err(anyhow!("Unexpected CoAP server exit!"))
}
