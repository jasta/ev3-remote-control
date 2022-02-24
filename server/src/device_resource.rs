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
//! ## GET /device/<address>/attribute
//!
//! Read all attribute values
//!
//! Response Type: array of AttributeValue
//!
//! ## PUT /device/<address>/attribute
//!
//! Write multiple attribute values
//!
//! Request Type: array of AttributeValue
//!
//! ## GET /device/<address>/attribute/<attribute>
//!
//! Read a specific attribute value.
//!
//! Response Type: AttributeValue

use std::borrow::Borrow;
use std::error::Error;
use std::net::SocketAddr;
use std::ops::Deref;

use coap_lite::{CoapRequest, ContentFormat, RequestType, ResponseType};
use coap_lite::link_format::{LINK_ATTR_CONTENT_FORMAT, LINK_ATTR_RESOURCE_TYPE, LinkAttributeWrite};

use serde::Deserialize;
use serde::Serialize;

use crate::coap_resource_server::{CoapResource, HandlingError};
use crate::{coap_utils, hal};
use crate::hal::{HalAttribute, HalAttributeType, HalDevice, HalDeviceType, HalError, HalResult};

pub struct DevicesResource;

impl CoapResource for DevicesResource {
  fn relative_path(&self) -> &str {
    "devices"
  }

  fn debug_name(&self) -> &str {
    "DevicesResource"
  }

  fn is_discoverable(&self) -> bool {
    true
  }

  fn write_attributes<'a, 'b>(&self, writer: LinkAttributeWrite<'a, 'b, String>) -> LinkAttributeWrite<'a, 'b, String> {
    writer
        .attr_quoted(LINK_ATTR_RESOURCE_TYPE, "devices")
        .attr_u32(LINK_ATTR_CONTENT_FORMAT, usize::from(ContentFormat::ApplicationJSON) as u32)
  }

  fn handle(&self, request: &mut CoapRequest<SocketAddr>, remaining_path: &[String]) -> Result<(), HandlingError> {
    self.handle_internal(request, remaining_path)
        .map_err(anyhow_error_mapping)
  }
}

impl DevicesResource {
  fn handle_internal(&self, request: &mut CoapRequest<SocketAddr>, remaining_path: &[String]) -> anyhow::Result<()> {
    let method = *request.get_method();
    let mut reply = request.response.as_mut().ok_or_else(HandlingError::not_handled)?;

    let hal = &hal::HAL;

    let mut path_iter = remaining_path.iter();
    let matches = match (method, path_iter.next()) {
      (RequestType::Get, None) => hal.list_devices()?,
      (RequestType::Get, Some(path)) if path == "by_driver" => {
        let driver = path_iter.next()
            .ok_or_else(|| HandlingError::bad_request("Missing driver name"))?;
        hal.by_driver(driver)?
      },
      (RequestType::Get, _) => Err(HandlingError::not_found())?,
      _ => Err(HandlingError::method_not_supported())?,
    };

    let matches_result: Result<Vec<_>, _> = matches.into_iter()
        .map(Device::from_hal)
        .collect();
    let matches_json = matches_result?;

    reply.message.set_content_format(ContentFormat::ApplicationJSON);
    let payload = serde_json::to_string(&matches_json)?;
    reply.message.payload = payload.into_bytes();
    Ok(())
  }
}

pub struct SingleDeviceResource;

impl CoapResource for SingleDeviceResource {
  fn relative_path(&self) -> &str {
    "device"
  }

  fn debug_name(&self) -> &str {
    "DeviceResource"
  }

  fn is_discoverable(&self) -> bool {
    true
  }

  fn write_attributes<'a, 'b>(&self, writer: LinkAttributeWrite<'a, 'b, String>) -> LinkAttributeWrite<'a, 'b, String> {
    writer
        .attr_quoted(LINK_ATTR_RESOURCE_TYPE, "device")
        .attr_u32(LINK_ATTR_CONTENT_FORMAT, usize::from(ContentFormat::ApplicationJSON) as u32)
  }

  fn handle(&self, request: &mut CoapRequest<SocketAddr>, remaining_path: &[String]) -> Result<(), HandlingError> {
    self.handle_internal(request, remaining_path)
        .map_err(anyhow_error_mapping)
  }
}

impl SingleDeviceResource {
  fn handle_internal(&self, request: &mut CoapRequest<SocketAddr>, remaining_path: &[String]) -> anyhow::Result<()> {
    let method = *request.get_method();
    let mut reply = request.response.as_mut().ok_or_else(HandlingError::not_handled)?;

    let hal = &hal::HAL;

    let mut path_iter = remaining_path.iter();
    let device = match (method, path_iter.next()) {
      (RequestType::Get, None) => Err(HandlingError::bad_request("Missing address"))?,
      (RequestType::Get, Some(address)) => {
        hal.by_address(address)?.ok_or_else(HandlingError::not_found)?
      },
      _ => Err(HandlingError::method_not_supported())?
    };

    let payload = match (method, path_iter.next()) {
      (RequestType::Get, None) => {
        serde_json::to_string(&Device::from_hal(device)?)?
      },
      (RequestType::Get, Some(path)) if path == "attribute" => {
        match path_iter.next() {
          None => {
            let values: Result<Vec<_>, _> = device.get_applicable_attributes()?.into_iter()
                .map(|a| AttributeValue::from_hal(&device, &a))
                .collect();
            serde_json::to_string(&values?)?
          },
          Some(attribute) => {
            let attribute = device.get_applicable_attributes()?.into_iter()
                .find(|a| &a.name == attribute)
                .ok_or_else(HandlingError::not_found)?;
            let value = AttributeValue::from_hal(&device, &attribute)?;
            serde_json::to_string(&value)?
          },
        }
      },
      _ => Err(HandlingError::method_not_supported())?,
    };

    reply.message.set_content_format(ContentFormat::ApplicationJSON);
    reply.message.payload = payload.into_bytes();
    Ok(())
  }
}

fn anyhow_error_mapping(error: anyhow::Error) -> HandlingError {
  match error.downcast_ref::<HandlingError>() {
    Some(e) => e.clone(),
    None => HandlingError::internal(error),
  }
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