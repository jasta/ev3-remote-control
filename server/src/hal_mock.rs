use std::time::{SystemTime, UNIX_EPOCH};

use crate::hal::{Hal, HalAttribute, HalAttributeType, HalDevice, HalDeviceType, HalError, HalResult};

pub struct HalMock {
  devices: Vec<HalDeviceMock>,
}

impl HalMock {
  pub fn with_hardcoded_devices() -> Self {
    let devices = vec![
      HalDeviceMock {
        device_type: HalDeviceType::Sensor,
        driver_name: "mock".to_owned(),
        address: "in1".to_owned(),
        attributes: vec![
          HalAttribute::new_readonly(HalAttributeType::String, "hello"),
          HalAttribute::new_readonly(HalAttributeType::UInt32, "time"),
        ],
      }
    ];
    Self { devices }
  }
}

impl Hal for HalMock {
  fn list_devices(&self) -> HalResult<Vec<Box<dyn HalDevice>>> {
    Ok(self.devices.iter()
        .map(|d| Box::new(d.clone()) as Box<dyn HalDevice>)
        .collect())
  }

  fn by_driver(&self, driver: &str) -> HalResult<Vec<Box<dyn HalDevice>>> {
    Ok(self.devices.iter()
        .filter(|d| d.driver_name == driver)
        .map(|d| Box::new(d.clone()) as Box<dyn HalDevice>)
        .collect())
  }

  fn by_address(&self, address: &str) -> HalResult<Option<Box<dyn HalDevice>>> {
    Ok(self.devices.iter()
        .find(|d| d.address == address)
        .map(|d| Box::new(d.clone()) as Box<dyn HalDevice>))
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
      "hello" => Ok("world".to_owned()),
      "time" => {
        let since_epoch = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap();
        Ok(format!("{:?}", since_epoch.as_millis()))
      },
      _ => Err(HalError::InternalError(format!("Invalid attribute: name={}", name))),
    }
  }

  fn set_attribute_str(&mut self, name: &str, _value: &str) -> HalResult<()> {
    match name {
      _ => Err(HalError::InternalError(format!("Attribute not writable: name={}", name))),
    }
  }
}