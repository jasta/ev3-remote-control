use std::error::Error;
use std::fs::read_dir;
use std::io;
use std::str::FromStr;
use ev3dev_lang_rust::{Attribute, Device, Driver, Ev3Error};
use ev3dev_lang_rust::sensors::{ColorSensor, Sensor};
use crate::hal::{Hal, HalAttribute, HalAttributeType, HalDevice, HalDeviceType, HalError, HalResult};

pub struct HalEv3 {
}

impl HalEv3 {
  fn find_devices_by_sysfs_class(&self, sysfs_class: &str) -> io::Result<Vec<Box<dyn HalDevice>>> {
    let mut results = Vec::<Box<dyn HalDevice>>::new();
    for entry_result in read_dir(format!("/sys/class/{}", sysfs_class))? {
      if let Ok(entry) = entry_result {
        if let Some(device_name) = entry.file_name().to_str() {
          results.push(Box::new(HalDeviceEv3 {
            sysfs_class: sysfs_class.to_owned(),
            full_device_path: format!("/sys/class/{}/{}", sysfs_class, device_name).to_owned(),
          }));
        }
      }
    }

    Ok(results)
  }

  fn find_device_by_address(&self, address: &str) -> io::Result<Option<Box<dyn HalDevice>>> {
    for sysfs_class in ["tacho-motor", "lego-sensor"] {
      for entry_result in read_dir(format!("/sys/class/{}", sysfs_class))? {
        if let Ok(entry) = entry_result {
          if let Some(device_name) = entry.file_name().to_str() {
            let full_device_path = format!("/sys/class/{}/{}", sysfs_class, device_name);
            if let Ok(attr) = Attribute::from_path(&format!("{}/address", full_device_path)) {
              if let Ok(value) = attr.get::<String>() {
                if value == address {
                  return Ok(Some(Box::new(HalDeviceEv3 {
                    sysfs_class: sysfs_class.to_owned(),
                    full_device_path: full_device_path.to_owned()
                  })));
                }
              }
            }
          }
        }
      }
    }
    Ok(None)
  }
}

impl Hal for HalEv3 {
  fn list_devices(&self) -> HalResult<Vec<Box<dyn HalDevice>>> {
    let unmerged_results: HalResult<Vec<_>> = ["tacho-motor", "lego-sensor"].iter()
        .map(|&x| {
          self.find_devices_by_sysfs_class(x)
              .map_err(|e| HalError::InternalError(e.to_string()))
        })
        .collect();

    let merged: Vec<_> = unmerged_results?.into_iter()
        .flatten()
        .collect();
    Ok(merged)
  }

  fn by_driver(&self, driver: &str) -> HalResult<Vec<Box<dyn HalDevice>>> {
    Ok(self.list_devices()?.into_iter()
        .filter(|d| {
          d.get_driver_name().as_deref().unwrap_or("") == driver
        })
        .collect())
  }

  fn by_address(&self, address: &str) -> HalResult<Option<Box<dyn HalDevice>>> {
    self.find_device_by_address(address)
        .map_err(|e| HalError::InternalError(e.to_string()))
  }
}

pub struct HalDeviceEv3 {
  sysfs_class: String,
  full_device_path: String,
}

impl HalDevice for HalDeviceEv3 {
  fn get_type(&self) -> HalResult<HalDeviceType> {
    match self.sysfs_class.as_str() {
      "tacho-motor" => Ok(HalDeviceType::Actuator),
      "lego-sensor" => Ok(HalDeviceType::Sensor),
      unknown => Err(HalError::InternalError(format!("Unknown sysfs class: {}", unknown))),
    }
  }

  fn get_driver_name(&self) -> HalResult<String> {
    self.get_attribute_str("driver_name")
  }

  fn get_address(&self) -> HalResult<String> {
    self.get_attribute_str("address")
  }

  fn get_applicable_attributes(&self) -> HalResult<Vec<HalAttribute>> {
    let mut result = Vec::with_capacity(64);

    result.extend([
      HalAttribute::new_readonly(HalAttributeType::String, "address"),
      HalAttribute::new_writeonly(HalAttributeType::String, "command"),
      HalAttribute::new_readonly_array(HalAttributeType::String, "commands"),
      HalAttribute::new_readonly(HalAttributeType::String, "driver_name"),
      HalAttribute::new_readonly(HalAttributeType::String, "fw_version"),
    ]);

    match self.sysfs_class.as_str() {
      "tacho-motor" => result.extend([
        HalAttribute::new_readonly(HalAttributeType::Int32, "count_per_rot"),
        HalAttribute::new_readonly(HalAttributeType::Int8, "duty_cycle"),
        HalAttribute::new_rw(HalAttributeType::Int8, "duty_cycle_sp"),
        HalAttribute::new_readonly(HalAttributeType::Int32, "position"),
        HalAttribute::new_rw(HalAttributeType::Int32, "position_sp"),
        HalAttribute::new_rw(HalAttributeType::Int32, "ramp_down_sp"),
        HalAttribute::new_rw(HalAttributeType::Int32, "ramp_up_sp"),
        HalAttribute::new_readonly(HalAttributeType::Int32, "speed"),
        HalAttribute::new_rw(HalAttributeType::Int32, "speed_sp"),
        HalAttribute::new_readonly(HalAttributeType::String, "state"),
        HalAttribute::new_rw(HalAttributeType::String, "stop_action"),
        HalAttribute::new_readonly_array(HalAttributeType::String, "stop_actions"),
      ]),
      "lego-sensor" => result.extend([
        HalAttribute::new_rw(HalAttributeType::String, "mode"),
        HalAttribute::new_readonly_array(HalAttributeType::String, "modes"),
        HalAttribute::new_readonly(HalAttributeType::UInt8, "num_values"),
        HalAttribute::new_readonly(HalAttributeType::Int32, "value0"),
        HalAttribute::new_readonly(HalAttributeType::Int32, "value1"),
        HalAttribute::new_readonly(HalAttributeType::Int32, "value2"),
        HalAttribute::new_readonly(HalAttributeType::Int32, "value3"),
        HalAttribute::new_readonly(HalAttributeType::Int32, "value4"),
        HalAttribute::new_readonly(HalAttributeType::Int32, "value5"),
        HalAttribute::new_readonly(HalAttributeType::Int32, "value6"),
        HalAttribute::new_readonly(HalAttributeType::Int32, "value7"),
      ]),
      _ => {},
    };

    Ok(result)
  }

  fn get_attribute_str(&self, name: &str) -> HalResult<String> {
    log::debug!("Reading attribute {}...", name);
    Attribute::from_path(&format!("{}/{}", self.full_device_path, name))
        .and_then(|a| a.get())
        .map_err(convert_to_hal_error)
  }

  fn set_attribute_str(&mut self, name: &str, value: &str) -> HalResult<()> {
    log::debug!("Writing attribute {}={}...", name, value);
    Attribute::from_path(&format!("{}/{}", self.full_device_path, name))
        .and_then(|a| a.set(value))
        .map_err(convert_to_hal_error)
  }
}

fn convert_to_hal_error(err: Ev3Error) -> HalError {
  match err {
    Ev3Error::InternalError { msg } => HalError::InternalError(msg),
    Ev3Error::NotConnected { device, port } => HalError::NotConnected { device, port },
    Ev3Error::MultipleMatches { device, ports } => HalError::InternalError(
      format!("MultipleMatches: device: {}, ports: {:?}", device, ports)
    ),
  }
}
