use async_trait::async_trait;
use coap_server::app::request_handler::RequestHandler;
use coap_server::app::{CoapError, Request, Response};
use std::future::Future;
use std::net::SocketAddr;

#[derive(Clone)]
pub struct AnyhowErrorWrapper<F> {
    wrapper: F,
}

// All these bounds are duplicated because rustc gives crappy errors if you don't do it and
// you fail to meet Send or Sync bounds...
impl<F, R> AnyhowErrorWrapper<F>
where
    F: Fn(Request<SocketAddr>) -> R + Clone + Send + Sync + 'static,
    R: Future<Output = anyhow::Result<Response>> + Send,
{
    pub fn new(wrapper: F) -> Self {
        Self { wrapper }
    }
}

#[async_trait]
impl<F, R> RequestHandler<SocketAddr> for AnyhowErrorWrapper<F>
where
    F: Fn(Request<SocketAddr>) -> R + Clone + Send + Sync + 'static,
    R: Future<Output = anyhow::Result<Response>> + Send,
{
    async fn handle(&self, request: Request<SocketAddr>) -> Result<Response, CoapError> {
        (self.wrapper)(request).await.map_err(anyhow_error_mapping)
    }
}

fn anyhow_error_mapping(error: anyhow::Error) -> CoapError {
    match error.downcast_ref::<CoapError>() {
        Some(e) => e.clone(),
        None => CoapError::internal(error),
    }
}
