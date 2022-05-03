use anyhow::anyhow;
use std::sync::Arc;
use std::thread;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use crate::hal::{
    Hal, HalAttribute, HalAttributeType, HalDevice, HalDeviceType, HalError, HalResult, WatchHandle,
};

pub struct HalMock {
    devices: Vec<HalDeviceMock>,
}

impl HalMock {
    pub fn with_hardcoded_devices() -> Self {
        let devices = vec![HalDeviceMock {
            device_type: HalDeviceType::Sensor,
            driver_name: "lego-ev3-ir".to_owned(),
            address: "ev3-ports:in1".to_owned(),
            attributes: vec![
                HalAttribute::new_readonly(HalAttributeType::String, "mode"),
                HalAttribute::new_readonly(HalAttributeType::UInt32, "value0"),
            ],
        }];
        Self { devices }
    }
}

impl Hal for HalMock {
    fn list_devices(&self) -> HalResult<Vec<Box<dyn HalDevice>>> {
        Ok(self
            .devices
            .iter()
            .map(|d| Box::new(d.clone()) as Box<dyn HalDevice>)
            .collect())
    }

    fn by_driver(&self, driver: &str) -> HalResult<Vec<Box<dyn HalDevice>>> {
        Ok(self
            .devices
            .iter()
            .filter(|d| d.driver_name == driver)
            .map(|d| Box::new(d.clone()) as Box<dyn HalDevice>)
            .collect())
    }

    fn by_address(&self, address: &str) -> HalResult<Option<Box<dyn HalDevice>>> {
        Ok(self
            .devices
            .iter()
            .find(|d| d.address == address)
            .map(|d| Box::new(d.clone()) as Box<dyn HalDevice>))
    }

    fn watch_devices(&self) -> anyhow::Result<WatchHandle> {
        let (tx, rx) = std::sync::mpsc::channel();
        let cancel_handle = Arc::new("dummy".to_string());
        let weak_handle = Arc::downgrade(&cancel_handle);
        thread::spawn(move || loop {
            thread::sleep(Duration::from_secs(60));
            if weak_handle.upgrade().is_none() {
                break;
            }
            if tx.send(()).is_err() {
                break;
            }
        });
        Ok(WatchHandle::new(cancel_handle, rx))
    }
}

#[derive(Debug, Clone)]
struct HalDeviceMock {
    device_type: HalDeviceType,
    driver_name: String,
    address: String,
    attributes: Vec<HalAttribute>,
}

impl HalDevice for HalDeviceMock {
    fn get_type(&self) -> HalResult<HalDeviceType> {
        Ok(self.device_type)
    }

    fn get_driver_name(&self) -> HalResult<String> {
        Ok(self.driver_name.clone())
    }

    fn get_address(&self) -> HalResult<String> {
        Ok(self.address.clone())
    }

    fn get_applicable_attributes(&self) -> HalResult<Vec<HalAttribute>> {
        Ok(self.attributes.clone())
    }

    fn get_attribute_str(&self, name: &str) -> HalResult<String> {
        match name {
            "mode" => Ok("IR-PROX".to_owned()),
            "value0" => {
                // Oscillate between 0 and 100, ticking once per second.
                let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap();
                let interval = now.as_secs() % 200;
                let value = if interval > 100 {
                    200 - interval
                } else {
                    interval
                };
                Ok(value.to_string())
            }
            _ => Err(HalError::InternalError(format!(
                "Invalid attribute: name={}",
                name
            ))),
        }
    }

    fn set_attribute_str(&mut self, name: &str, _value: &str) -> HalResult<()> {
        match name {
            _ => Err(HalError::InternalError(format!(
                "Attribute not writable: name={}",
                name
            ))),
        }
    }

    fn watch_attributes(&self, names: &[String]) -> anyhow::Result<WatchHandle> {
        if names.contains(&("value0".to_string())) {
            let (tx, rx) = std::sync::mpsc::channel();
            let cancel_handle = Arc::new("dummy".to_string());
            let weak_handle = Arc::downgrade(&cancel_handle);
            thread::spawn(move || loop {
                thread::sleep(Duration::from_secs(1));
                if weak_handle.upgrade().is_none() {
                    break;
                }
                if tx.send(()).is_err() {
                    break;
                }
            });
            Ok(WatchHandle::new(cancel_handle, rx))
        } else {
            Err(anyhow!("No watchable attribute in {names:?}"))
        }
    }
}
