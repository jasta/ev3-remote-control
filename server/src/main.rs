use crate::device_resource::device_resources;
use anyhow::anyhow;
use clap::Parser;
use coap_server::{app, CoapServer, UdpTransport};
use log::info;
use tokio::process::Command;
use tokio::runtime::Runtime;

mod anyhow_error_wrapper;
mod attributes_observable;
mod device_resource;
mod devices_observable;
mod hal;
mod hal_ev3;
mod hal_mock;
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
    logging_init();

    let opts: Opts = Opts::parse();

    let bind_addr = determine_bind_address(opts);
    run_server_forever(bind_addr);
}

fn logging_init() {
    env_logger::init();

    #[cfg(feature = "async_debug")]
    console_subscriber::init();
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

        tokio::try_join!(mdns_future, coap_future).unwrap();
    });
}

async fn run_mdns_advertisement(_port: u16) -> anyhow::Result<()> {
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
    let server = CoapServer::bind(UdpTransport::new(addr.clone())).await?;
    info!("Server up on {addr:?}");
    server
        .serve(app::new().resources(device_resources()))
        .await?;
    Err(anyhow!("Unexpected CoAP server exit!"))
}
