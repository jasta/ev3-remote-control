//! Get information about the possible presentation layouts of the devices available.  This is used
//! by the remote control viewer app to present more convenient controls than just a generic
//! list of detected device controls.
//!
//! # Types
//!
//! ## Type: Layout
//!
//! ### Fields:
//!
//! **name**: type - desc
//!
//! ### Example:
//!
//! Example:
//! ```
//! {
//!   "label": "Main",
//!   "orientation": "landscape",
//!   "canvas": "3x6",
//!   "widgets": {
//!     "name": "trackpad",
//!     "coordinates": "3x3@0,0"
//!     "devices": [
//!       {
//!         "address": "ev3-ports:outA",
//!         "widget_role": {
//!           "name": "position_range",
//!           "data": {
//!             "min_position": -480,
//!             "max_position": 200,
//!             "speed": "75%",
//!           },
//!         },
//!       },
//!       {
//!         "address": "ev3-ports:outD",
//!         "widget_role": {
//!           ...
//!         },
//!       },
//!     ],
//!   },
//!   {
//!     "name": "vertical_slider",
//!     "coordinates": "1x3@3,0",
//!     "devices": [
//!       {
//!         "address": "ev3-ports:outB",
//!         "widget_role": {
//!           "name": "position_range",
//!           "data": {
//!             "min_position": -30,
//!             "max_position": 250,
//!             "speed": "50%",
//!           },
//!         },
//!       },
//!     ],
//!   },
//!   {
//!     "name": "forward_off_reverse_buttons",
//!     "coordinates": "2x1@4,0",
//!     "devices": [
//!       {
//!         "address": "ev3-ports:outC",
//!         "widget_role": {
//!           "name": "forward_off_reverse_role",
//!           "data": {
//!             "duty_cycle": "90%",
//!             "button_labels": [
//!               "Pressurize",
//!               "Off",
//!               "Release",
//!             ],
//!           },
//!         },
//!       },
//!     ],
//!   },
//!   {
//!     "name": "text",
//!     "coordinates": "2x1@4,1",
//!     "devices": [
//!       {
//!         "address": "ev3-ports:in1",
//!         "widget_role": {
//!           "name": "sensor_reading",
//!           "data": {
//!             "mode": "ABS-KPA",
//!             "format": "{value0} kPa",
//!           },
//!         },
//!       },
//!     ],
//!   },
//! }
//! ```
//!
//! # Requests
//!
//! ## GET /layouts
//!
//! List all supports presentation layouts.
//!
//! Response Type: array of Layout
