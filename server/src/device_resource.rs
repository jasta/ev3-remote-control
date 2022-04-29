//! General hardware access APIs.  Some of the nomenclature is in terms of the Lego EV3 Mindstorm
//! however this should be generalizable to any robotics platform, and even adaptable to one using
//! ROS.
//!
//! # Types
//!
//! ## Type: Device
//!
//! ### Fields:
//!
//! **type_name**: string - actuator | sensor
//! **driver_name**: string - hardware-specific name that defines the characteristics of how to work with
//!    the device
//! **address**: string - where this device is physically connected
//! **attributes**: array of Attribute - list of applicable attributes
//!
//! ### Example:
//!
//! Example:
//! ```
//! {
//!   "type_name": "actuator",
//!   "driver_name": "lego-ev3-l-motor",
//!   "address": "ev3-ports:outA",
//!   "attributes": [
//!     {
//!       "type": "int8",
//!       "name": "duty_cycle_sp",
//!     },
//!     ...
//!   ],
//! }
//! ```
//!
//! ## Type: Attribute
//!
//! ### Fields:
//!
//! **type_name**: string - uint/int8/16/32/64 | float | double | string | `[<type_name>]`
//! **name**: string - attribute name
//! **is_readable**: bool - can this be read from?
//! **is_writable**: bool - can this be written to?
//!
//! ### Example:
//! ```
//! {
//!   "type": "int8",
//!   "name": "duty_cycle_sp",
//!   "is_readable": true,
//!   "is_writable": true,
//! }
//! ```
//!
//! ## Type: AttributeValue
//!
//! ### Fields:
//!
//! **name**: string - attribute name
//! **value**: mixed - field value encoded as JSON.  All integer types will be JSON numbers; null is
//! supported.
//!
//! ### Example:
//!
//! ```
//! {
//!   "name": "duty_cycle_sp",
//!   "value": 10,
//! }
//! ```
//!
//! # Requests
//!
//! ## GET /devices
//!
//! List all devices available.
//!
//! Response Type: array of Device
//!
//! ## GET /devices/by_driver/<driver_name>
//!
//! List all devices with the given driver name.
//!
//! Response Type: array of Device
//!
//! ## GET /device/<address>
//!
//! Similar to /devices but looks up a single device at a specific address.
//!
//! Response type: Device
//!
//! ## GET /device/<address>/attributes
//!
//! Read all attribute values
//!
//! Response Type: array of AttributeValue
//!
//! ## PUT /device/<address>/attributes
//!
//! Write multiple attribute values
//!
//! Request Type: array of AttributeValue
//!
//! ## GET /device/<address>/attributes/<attribute>
//!
//! Read a specific attribute value.
//!
//! Response Type: AttributeValue
//!
//! ## GET /device/<address>/attributes/<attribute1>,<attribute2>,...
//!
//! Read a set of attribute values.
//!
//! Response Type: array of AttributeValue

use std::collections::HashSet;
use std::net::SocketAddr;
use anyhow::anyhow;
use coap_lite::{ContentFormat, MessageClass, RequestType, ResponseType};
use coap_lite::link_format::{LINK_ATTR_CONTENT_FORMAT, LINK_ATTR_RESOURCE_TYPE};
use coap_server::app;
use coap_server::app::{CoapError, Request, ResourceBuilder, Response};
use serde::Deserialize;
use serde::Serialize;
use crate::anyhow_error_wrapper::AnyhowErrorWrapper;
use crate::attributes_observable::HalWatchAttributes;
use crate::devices_observable::HalWatchDevices;

use crate::hal;
use crate::hal::{HalAttribute, HalAttributeType, HalDevice, HalDeviceType, HalError, HalResult};

pub fn device_resources() -> Vec<ResourceBuilder<SocketAddr>> {
  let watch_attributes = HalWatchAttributes::default();
  let watch_attributes_for_handler = watch_attributes.clone();
  [
    app::resource("devices")
      .link_attr(LINK_ATTR_RESOURCE_TYPE, "devices")
      .link_attr(LINK_ATTR_CONTENT_FORMAT, ContentFormat::ApplicationJSON)
      .observable(HalWatchDevices::default())
      .get(AnyhowErrorWrapper::new(handle_list_devices)),
    app::resource("device")
      .link_attr(LINK_ATTR_RESOURCE_TYPE, "device")
      .link_attr(LINK_ATTR_CONTENT_FORMAT, ContentFormat::ApplicationJSON)
      .observable(watch_attributes)
      .default_handler(
        AnyhowErrorWrapper::new(
          move |req| handle_single_device(req, watch_attributes_for_handler.clone()))),
  ].into_iter().collect()
}

async fn handle_list_devices(request: Request<SocketAddr>) -> anyhow::Result<Response> {
  let hal = &hal::HAL;

  let mut path_iter = request.unmatched_path.iter();
  let matches = match path_iter.next() {
    None => hal.list_devices()?,
    Some(path) if path == "by_driver" => {
      let driver = path_iter.next()
          .ok_or_else(|| CoapError::bad_request("Missing driver name"))?;
      hal.by_driver(driver)?
    },
    _ => Err(CoapError::not_found())?,
  };

  let matches_result: Result<Vec<_>, _> = matches.into_iter()
      .map(Device::from_hal)
      .collect();
  let matches_json = matches_result?;

  let mut reply = request.new_response();
  reply.message.set_content_format(ContentFormat::ApplicationJSON);
  let payload = serde_json::to_string(&matches_json)?;
  reply.message.payload = payload.into_bytes();
  Ok(reply)
}

async fn handle_single_device(request: Request<SocketAddr>, watch: HalWatchAttributes) -> anyhow::Result<Response> {
  let method = *request.original.get_method();
  let hal = &hal::HAL;

  let unmatched_path_for_iter = request.unmatched_path.clone();
  let mut path_iter = unmatched_path_for_iter.into_iter();
  let device = match path_iter.next() {
    None => Err(CoapError::bad_request("Missing address"))?,
    Some(address) => {
      hal.by_address(&address)?.ok_or_else(CoapError::not_found)?
    },
  };

  match method {
    RequestType::Get => handle_single_device_get(device, request, path_iter.as_slice()),
    RequestType::Put => {
      let unmatched_path_flat = request.unmatched_path.join("/");
      let put_result = handle_single_device_put(device, request, path_iter.as_slice());

      // Must spawn another task here because rustc is too basic to understand that
      // device is dropped already and it doesn't matter if it's Send or not:
      // https://rust-lang.github.io/async-book/07_workarounds/03_send_approximation.html
      tokio::spawn(async move {
        watch.observers.notify_change_for_path(&unmatched_path_flat).await;
      });

      put_result
    },
    _ => Err(CoapError::method_not_allowed())?
  }
}

fn handle_single_device_get(
    device: Box<dyn HalDevice>,
    request: Request<SocketAddr>,
    remaining_path: &[String]
) -> anyhow::Result<Response> {
  let mut path_iter = remaining_path.iter();
  let payload = match path_iter.next() {
    None => {
      serde_json::to_string(&Device::from_hal(device)?)?
    },
    Some(path) if path == "attributes" => {
      match path_iter.next() {
        None => {
          let values: Result<Vec<_>, _> = device.get_applicable_attributes()?.into_iter()
              .map(|a| AttributeValue::from_hal(&device, &a))
              .collect();
          serde_json::to_string(&values?)?
        },
        Some(attributes) if attributes.contains(',') => {
          let attributes_vec: HashSet<_> = attributes.split(',').collect();
          let values: Result<Vec<_>, _> = device.get_applicable_attributes()?.into_iter()
              .filter(|a| attributes_vec.contains(&a.name.as_str()))
              .map(|a| AttributeValue::from_hal(&device, &a))
              .collect();
          serde_json::to_string(&values?)?
        },
        Some(attribute) => {
          let attribute = device.get_applicable_attributes()?.into_iter()
              .find(|a| &a.name == attribute)
              .ok_or_else(CoapError::not_found)?;
          let value = AttributeValue::from_hal(&device, &attribute)?;
          serde_json::to_string(&value)?
        }
      }
    },
    _ => Err(CoapError::not_found())?,
  };

  let mut reply = request.new_response();
  reply.message.set_content_format(ContentFormat::ApplicationJSON);
  reply.message.payload = payload.into_bytes();
  Ok(reply)
}

fn handle_single_device_put(
    mut device: Box<dyn HalDevice>,
    request: Request<SocketAddr>,
    remaining_path: &[String],
) -> anyhow::Result<Response> {
  let mut path_iter = remaining_path.iter();
  match path_iter.next() {
    Some(path) if path == "attributes" => {
      let payload_str = String::from_utf8(request.original.message.payload.clone())?;
      let values = serde_json::from_str::<Vec<AttributeValue>>(&payload_str)?;

      for value in &values {
        // TODO: We really need to yield errors for each write, not just abort the
        // whole thing with no reasonable rollback.
        device.set_attribute_str(&value.name, &value.to_hal_value_str()?)?;
      }
    },
    _ => Err(CoapError::not_found())?
  }

  let mut reply = request.new_response();
  reply.message.header.code = MessageClass::Response(ResponseType::Changed);
  reply.message.payload.clear();
  Ok(reply)
}

#[derive(Serialize, Deserialize)]
struct Device {
  type_name: String,
  driver_name: String,
  address: String,
  attributes: Vec<Attribute>,
}

impl Device {
  pub fn from_hal(hal: Box<dyn HalDevice>) -> HalResult<Self> {
    let type_name = match hal.get_type()? {
      HalDeviceType::Sensor => "sensor",
      HalDeviceType::Actuator => "actuator",
    }.to_owned();
    let attributes = hal.get_applicable_attributes()?.into_iter()
        .map(Attribute::from_hal)
        .collect();
    Ok(Self {
      type_name,
      driver_name: hal.get_driver_name()?,
      address: hal.get_address()?,
      attributes,
    })
  }
}

#[derive(Serialize, Deserialize)]
struct Attribute {
  type_name: String,
  name: String,
  is_readable: bool,
  is_writable: bool,
}

impl Attribute {
  pub fn from_hal(hal: HalAttribute) -> Self {
    let data_type = match hal.data_type {
      HalAttributeType::Int8 => "int8",
      HalAttributeType::Int16 => "int16",
      HalAttributeType::Int32 => "int32",
      HalAttributeType::Int64 => "int64",
      HalAttributeType::UInt8 => "int8",
      HalAttributeType::UInt16 => "int16",
      HalAttributeType::UInt32 => "int32",
      HalAttributeType::UInt64 => "int64",
      HalAttributeType::Float32 => "float",
      HalAttributeType::Float64 => "double",
      HalAttributeType::String => "string",
    }.to_owned();
    let type_name = if hal.is_array {
      format!("[{}]", data_type)
    } else {
      data_type
    };
    Self {
      type_name,
      name: hal.name,
      is_readable: hal.is_readable,
      is_writable: hal.is_writable,
    }
  }
}

#[derive(Serialize, Deserialize)]
struct AttributeValue {
  name: String,
  value: serde_json::Value,
}

impl AttributeValue {
  pub fn from_hal(
      device: &Box<dyn HalDevice>,
      attr: &HalAttribute,
  ) -> HalResult<Self> {
    let value_str = device.get_attribute_str(attr.name.as_str())?;

    let value = if attr.is_array {
      let values: Result<Vec<_>, _> = value_str.split(' ')
          .map(|v| Self::convert_value(attr, v))
          .collect();
      serde_json::Value::Array(values?)
    } else {
      Self::convert_value(attr, &value_str)?
    };

    Ok(Self { name: attr.name.clone(), value })
  }

  fn to_hal_value_str(&self) -> anyhow::Result<String> {
    match &self.value {
      serde_json::Value::String(s) => Ok(s.clone()),
      serde_json::Value::Number(n) => {
        let num_as_str = if n.is_i64() {
          n.as_i64().map(|x| x.to_string())
        } else if n.is_u64() {
          n.as_u64().map(|x| x.to_string())
        } else if n.is_f64() {
          n.as_f64().map(|x| x.to_string())
        } else {
          None
        };
        num_as_str.ok_or_else(|| anyhow!("n={}", n))
      },
      n => Err(anyhow!("Can't serialize {:?}", n)),
    }
  }

  fn convert_value(attribute: &HalAttribute, value: &str) -> Result<serde_json::Value, HalError> {
    let converted = match attribute.data_type {
      HalAttributeType::Int8 |
          HalAttributeType::Int16 |
          HalAttributeType::Int32 |
          HalAttributeType::Int64 => {
        value.parse::<i64>().ok().map(|x| serde_json::Value::Number(x.into()))
      },
      HalAttributeType::UInt8 |
          HalAttributeType::UInt16 |
          HalAttributeType::UInt32 |
          HalAttributeType::UInt64 => {
        value.parse::<u64>().ok().map(|x| serde_json::Value::Number(x.into()))
      }
      HalAttributeType::Float32 |
          HalAttributeType::Float64 => {
        value.parse::<f64>().ok()
            .and_then(serde_json::Number::from_f64)
            .map(|x| serde_json::Value::Number(x))
      }
      HalAttributeType::String => {
        Some(serde_json::Value::String(value.to_owned()))
      }
    };

    converted.ok_or_else(|| {
      HalError::InternalError(
        format!(
          "unexpected conversion error for {} with value: {}",
          attribute.name,
          value))
    })
  }
}
