use std::path::Path;
use std::sync::mpsc::Receiver;

use lazy_static::lazy_static;
use thiserror::Error;

use crate::hal_ev3::HalEv3;
use crate::hal_mock::HalMock;

const LEGO_PORT_ROOT: &str = "/sys/class/lego-port";

lazy_static! {
  pub static ref HAL: Box<dyn Hal + Sync> = {
    HalFactory::sense_from_environment()
  };
}

pub trait Hal {
  fn list_devices(&self) -> HalResult<Vec<Box<dyn HalDevice>>>;
  fn by_driver(&self, driver: &str) -> HalResult<Vec<Box<dyn HalDevice>>>;
  fn by_address(&self, address: &str) -> HalResult<Option<Box<dyn HalDevice>>>;

  /// Watch for any change such that [`list_devices`] would yield a different result.  Any
  /// emission on the receiver indicates a change.
  fn watch_devices(&self) -> anyhow::Result<Receiver<()>>;
}

#[derive(Error, Debug)]
pub enum HalError {
  #[error("not applicable")]
  NotApplicable,
  #[error("internal error: {0}")]
  InternalError(String),
  #[error("not connected {device} @ {port:?}")]
  NotConnected {
    /// Corresponding device
    device: String,
    /// Device was expected to be on this port (None if no port was specified)
    port: Option<String>,
  },
}

pub type HalResult<T> = Result<T, HalError>;

pub trait HalDevice {
  fn get_type(&self) -> HalResult<HalDeviceType>;
  fn get_driver_name(&self) -> HalResult<String>;
  fn get_address(&self) -> HalResult<String>;
  fn get_applicable_attributes(&self) -> HalResult<Vec<HalAttribute>>;

  fn get_attribute_str(&self, name: &str) -> HalResult<String>;
  fn set_attribute_str(&mut self, name: &str, value: &str) -> HalResult<()>;

  /// Watch for any change such that [`get_attribute_str`] would yield a different result
  /// for any of the provided set of names.  Any emission on the receiver indicates a change.
  fn watch_attributes(&self, names: &[String]) -> anyhow::Result<Receiver<()>>;
}

#[derive(Debug, Copy, Clone)]
pub enum HalDeviceType {
  Sensor,
  Actuator,
}

#[derive(Debug, Clone)]
pub struct HalAttribute {
  pub is_array: bool,
  pub data_type: HalAttributeType,
  pub name: String,
  pub is_readable: bool,
  pub is_writable: bool,
}

impl HalAttribute {
  pub fn new_rw(typ: HalAttributeType, name: &str) -> Self {
    Self { data_type: typ, is_array: false, name: name.to_owned(), is_readable: true, is_writable: true }
  }

  pub fn new_readonly(typ: HalAttributeType, name: &str) -> Self {
    Self { data_type: typ, is_array: false, name: name.to_owned(), is_readable: true, is_writable: false }
  }

  pub fn new_writeonly(typ: HalAttributeType, name: &str) -> Self {
    Self { data_type: typ, is_array: false, name: name.to_owned(), is_readable: false, is_writable: true }
  }

  pub fn new_readonly_array(typ: HalAttributeType, name: &str) -> Self {
    Self { data_type: typ, is_array: true, name: name.to_owned(), is_readable: true, is_writable: false }
  }
}

#[derive(Debug, Copy, Clone)]
pub enum HalAttributeType {
  Int8,
  Int16,
  Int32,
  Int64,
  UInt8,
  UInt16,
  UInt32,
  UInt64,
  Float32,
  Float64,
  String,
}

pub struct HalFactory;

impl HalFactory {
  fn for_mocking() -> Box<dyn Hal + Sync> {
    Box::new(HalMock::with_hardcoded_devices())
  }

  fn for_ev3() -> Box<dyn Hal + Sync> {
    Box::new(HalEv3 {})
  }

  fn sense_from_environment() -> Box<dyn Hal + Sync> {
    if Path::new(LEGO_PORT_ROOT).exists() {
      log::info!("Detected EV3 environment...");
      Self::for_ev3()
    } else {
      log::info!("Running with mock HAL layer...");
      Self::for_mocking()
    }
  }
}