use crate::hal;
use crate::hal::WatchHandle;
use anyhow::anyhow;
use async_trait::async_trait;
use coap_server::app::{ObservableResource, Observers, ObserversHolder};
use log::error;
use std::thread;

#[derive(Default, Clone)]
pub struct HalWatchAttributes {
    pub observers: ObserversHolder,
}

#[async_trait]
impl ObservableResource for HalWatchAttributes {
    async fn on_active(&self, observers: Observers) -> Observers {
        let relative_path_flat = observers.relative_path();
        let relative_path_vec: Vec<_> = observers
            .relative_path()
            .split('/')
            .map(|x| x.to_string())
            .collect();
        let attached = self.observers.attach(observers).await;

        match start_watch(&relative_path_vec) {
            Ok(handle) => {
                let holder_clone = self.observers.clone();
                let rx = handle.receiver;
                thread::spawn(move || {
                    let rt = tokio::runtime::Builder::new_current_thread()
                        .enable_all()
                        .build()
                        .unwrap();
                    for _ in rx {
                        rt.block_on(holder_clone.notify_change_for_path(&relative_path_flat))
                    }
                });
                attached.stay_active().await;
            }
            Err(e) => {
                error!("Cannot watch attributes: {e:?}");
            }
        }
        attached.detach().await
    }
}

fn start_watch(relative_path: &[String]) -> anyhow::Result<WatchHandle> {
    let hal = &hal::HAL;

    let mut path_iter = relative_path.iter();
    let address = path_iter
        .next()
        .ok_or_else(|| anyhow!("Missing <address> in path!"))?;
    let device = hal
        .by_address(&address)?
        .ok_or_else(|| anyhow!("No device at <address>"))?;

    path_iter
        .next()
        .filter(|&segment| segment == "attributes")
        .ok_or_else(|| anyhow!("Missing 'attributes' in path!"))?;

    // TODO: We should unify this parsing code with the actual handler...
    let attributes: Vec<_> = match path_iter.next() {
        None => device
            .get_applicable_attributes()?
            .into_iter()
            .map(|a| a.name)
            .collect(),
        Some(path) if path.contains(',') => path.split(',').map(|p| p.to_string()).collect(),
        Some(path) => vec![path.to_owned()],
    };

    device.watch_attributes(&attributes)
}
