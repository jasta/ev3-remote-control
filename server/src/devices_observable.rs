use async_trait::async_trait;
use coap_server::app::{ObservableResource, Observers, ObserversHolder};
use std::thread;
use crate::hal;

#[derive(Default)]
pub struct HalWatchDevices {
}

#[async_trait]
impl ObservableResource for HalWatchDevices {
  async fn on_active(&self, observers: Observers) -> Observers {
    let hal = &hal::HAL;
    let holder = ObserversHolder::new();
    let attached = holder.attach(observers).await;

    // notify-rs doesn't use async, so we'll just use regular threads...
    match hal.watch_devices() {
      Ok(handle) => {
        let holder_clone = holder.clone();
        let rx = handle.receiver;
        thread::spawn(move || {
          let rt = tokio::runtime::Builder::new_current_thread()
              .enable_all()
              .build()
              .unwrap();
          for _ in rx {
            rt.block_on(holder_clone.notify_change())
          }
        });
        attached.stay_active().await;

        // The handle will be dropped here which will cause the spawned thread to hit an error
        // reading from `rx` and then exit.
      }
      Err(e) => {
        // Fall through and detach...
        log::error!("Cannot watch devices: {e:?}");
      }
    }
    attached.detach().await
  }
}
