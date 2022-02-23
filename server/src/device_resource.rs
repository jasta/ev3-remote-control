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

use std::net::SocketAddr;
use std::ops::Deref;

use coap_lite::{CoapRequest, ContentFormat};
use coap_lite::link_format::{LINK_ATTR_CONTENT_FORMAT, LINK_ATTR_RESOURCE_TYPE, LinkAttributeWrite};

use serde::Deserialize;
use serde::Serialize;

use crate::coap_resource_server::{CoapResource, HandlingError};
use crate::{coap_utils, hal};
use crate::hal::{HalAttribute, HalAttributeType, HalDevice, HalDeviceType, HalError, HalResult};

pub struct DevicesResource {
}

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
    let mut reply = request.response.as_mut().ok_or(HandlingError::not_handled())?;

    let hal = &hal::HAL;

    let mut path_iter = remaining_path.iter();
    let matches = match path_iter.next() {
      None => hal.list_devices(),
      Some(path) if path == "by_driver" => {
        let driver = path_iter.next()
            .ok_or(HandlingError::bad_request("Missing driver name"))?;
        hal.by_driver(driver)
      },
      _ => return Err(HandlingError::not_found())
    }
        .map_err(|e| HandlingError::internal(e))?;

    let matches_result: Result<Vec<_>, _> = matches.into_iter()
        .map(|d| Device::from_hal(d))
        .collect();
    let matches_json = matches_result.map_err(|e| HandlingError::internal(e))?;

    reply.message.set_content_format(ContentFormat::ApplicationJSON);
    let payload = serde_json::to_string(&matches_json)
        .map_err(|e| HandlingError::internal(e))?;
    reply.message.payload = payload.into_bytes();
    Ok(())
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
  fn from_hal(hal: Box<dyn HalDevice>) -> HalResult<Self> {
    let type_name = match hal.get_type()? {
      HalDeviceType::Sensor => "sensor",
      HalDeviceType::Actuator => "actuator",
    }.to_owned();
    let attributes = hal.get_applicable_attributes()?.into_iter()
        .map(|a| Attribute::from_hal(a))
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
  fn from_hal(hal: HalAttribute) -> Self {
    let data_type = match hal.data_type {
      HalAttributeType::Int8 => "int8",
      HalAttributeType::Int16 => "int16",
      HalAttributeType::Int32 => "int32",
      HalAttributeType::Int64 => "int64",
      HalAttributeType::UInt8 => "int8",
      HalAttributeType::UInt16 => "int16",
      HalAttributeType::UInt32 => "int32",
      HalAttributeType::UInt64 => "int64",
      HalAttributeType::Float => "float",
      HalAttributeType::Doable => "double",
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