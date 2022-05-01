use std::fs::read_dir;
use std::path::Path;
use std::sync::mpsc::{Receiver, Sender};
use std::time::Duration;
use std::{io, thread};

use ev3dev_lang_rust::{Attribute, Ev3Error};
use log::debug;
use notify::poll::PollWatcherConfig;
use notify::{Event, PollWatcher, RecursiveMode, Watcher};

use crate::hal::{
    Hal, HalAttribute, HalAttributeType, HalDevice, HalDeviceType, HalError, HalResult, WatchHandle,
};

pub struct HalEv3 {}

impl HalEv3 {
    fn find_devices_by_sysfs_class(
        &self,
        sysfs_class: &str,
    ) -> io::Result<Vec<Box<dyn HalDevice>>> {
        let mut results = Vec::<Box<dyn HalDevice>>::new();
        for entry in (read_dir(format!("/sys/class/{}", sysfs_class))?).flatten() {
            if let Some(device_name) = entry.file_name().to_str() {
                results.push(Box::new(HalDeviceEv3 {
                    sysfs_class: sysfs_class.to_owned(),
                    full_device_path: format!("/sys/class/{}/{}", sysfs_class, device_name)
                        .to_owned(),
                }));
            }
        }

        Ok(results)
    }

    fn find_device_by_address(&self, address: &str) -> io::Result<Option<Box<dyn HalDevice>>> {
        for sysfs_class in ["tacho-motor", "lego-sensor"] {
            for entry in (read_dir(format!("/sys/class/{}", sysfs_class))?).flatten() {
                if let Some(device_name) = entry.file_name().to_str() {
                    let full_device_path = format!("/sys/class/{}/{}", sysfs_class, device_name);
                    if let Ok(attr) = Attribute::from_path(&format!("{}/address", full_device_path))
                    {
                        if let Ok(value) = attr.get::<String>() {
                            if value == address {
                                return Ok(Some(Box::new(HalDeviceEv3 {
                                    sysfs_class: sysfs_class.to_owned(),
                                    full_device_path: full_device_path.to_owned(),
                                })));
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
        let unmerged_results: HalResult<Vec<_>> = ["tacho-motor", "lego-sensor"]
            .iter()
            .map(|&x| {
                self.find_devices_by_sysfs_class(x)
                    .map_err(|e| HalError::InternalError(e.to_string()))
            })
            .collect();

        let merged: Vec<_> = unmerged_results?.into_iter().flatten().collect();
        Ok(merged)
    }

    fn by_driver(&self, driver: &str) -> HalResult<Vec<Box<dyn HalDevice>>> {
        Ok(self
            .list_devices()?
            .into_iter()
            .filter(|d| d.get_driver_name().as_deref().unwrap_or("") == driver)
            .collect())
    }

    fn by_address(&self, address: &str) -> HalResult<Option<Box<dyn HalDevice>>> {
        self.find_device_by_address(address)
            .map_err(|e| HalError::InternalError(e.to_string()))
    }

    fn watch_devices(&self) -> anyhow::Result<WatchHandle> {
        let paths = ["tacho-motor", "lego-sensor"].map(|path| format!("/sys/class/{}", path));
        watch_paths(&paths, Duration::from_secs(2))
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
            unknown => Err(HalError::InternalError(format!(
                "Unknown sysfs class: {}",
                unknown
            ))),
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
            _ => {}
        };

        Ok(result)
    }

    fn get_attribute_str(&self, name: &str) -> HalResult<String> {
        debug!("Reading attribute {}...", name);
        Attribute::from_path(&format!("{}/{}", self.full_device_path, name))
            .and_then(|a| a.get())
            .map_err(convert_to_hal_error)
    }

    fn set_attribute_str(&mut self, name: &str, value: &str) -> HalResult<()> {
        debug!("Writing attribute {}={}...", name, value);
        Attribute::from_path(&format!("{}/{}", self.full_device_path, name))
            .and_then(|a| a.set(value))
            .map_err(convert_to_hal_error)
    }

    fn watch_attributes(&self, names: &[String]) -> anyhow::Result<WatchHandle> {
        let attr_paths = names
            .iter()
            .map(|name| format!("{}/{name}", self.full_device_path))
            .collect::<Vec<_>>();

        watch_paths(&attr_paths, Duration::from_millis(100))
    }
}

fn convert_to_hal_error(err: Ev3Error) -> HalError {
    match err {
        Ev3Error::InternalError { msg } => HalError::InternalError(msg),
        Ev3Error::NotConnected { device, port } => HalError::NotConnected { device, port },
        Ev3Error::MultipleMatches { device, ports } => HalError::InternalError(format!(
            "MultipleMatches: device: {}, ports: {:?}",
            device, ports
        )),
    }
}

fn watch_paths(paths: &[String], poll_interval: Duration) -> anyhow::Result<WatchHandle> {
    let (tx, rx) = std::sync::mpsc::channel();
    let mut watcher = PollWatcher::with_config(
        tx,
        PollWatcherConfig {
            compare_contents: true,
            poll_interval,
        },
    )
    .unwrap();
    for path in paths {
        watcher.watch(Path::new(path), RecursiveMode::NonRecursive)?;
    }
    let (mapped_tx, mapped_rx) = std::sync::mpsc::channel();
    thread::spawn(move || {
        debug!("Spawning new thread for watch_devices...");
        let result = map_watcher_events(rx, mapped_tx);
        debug!("watch_devices thread exiting: {:?}...", result);
    });
    Ok(WatchHandle::new(watcher, mapped_rx))
}

fn map_watcher_events(
    rx: Receiver<notify::Result<Event>>,
    mapped_tx: Sender<()>,
) -> anyhow::Result<()> {
    loop {
        let event = rx.recv()?;
        debug!("Got {:?}", event);
        mapped_tx.send(())?;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_watch_devices_new_directory() {
        let _ = env_logger::builder().is_test(true).try_init();

        let tempdir = tempfile::tempdir().unwrap();
        let tempdir_str = tempdir.path().to_str().unwrap().to_owned();
        let handle = watch_paths(&[tempdir_str], Duration::from_millis(10)).unwrap();

        for i in 0..5 {
            let testdir = tempdir.path().join(format!("testdir-{i}"));
            std::fs::create_dir(testdir).unwrap();
            handle
                .receiver
                .recv_timeout(Duration::from_secs(90))
                .unwrap();
        }
    }

    #[test]
    fn test_drop_causes_cancel() {
        let _ = env_logger::builder().is_test(true).try_init();

        let tempdir = tempfile::tempdir().unwrap();
        let tempdir_str = tempdir.path().to_str().unwrap().to_owned();
        let mut handle = watch_paths(&[tempdir_str], Duration::from_millis(10)).unwrap();

        handle.drop_for_test();

        let result = handle.receiver.recv();
        assert!(result.is_err());
    }
}
