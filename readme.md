# JRT Distance Measure

App for using M8xx-JRT distance sensors in Android over USB connection (using UART-USB converter board). The supported board types can be found in device_filter.xml under xml resources directory.


**Note**: This is a legacy project updated to work with modern Android tooling using Claude AI. 
Only the gradle files are updated, the actual code is in its original state.

## Fixed Issues (2024 Update)
- ✅ Replaced deprecated JCenter with Maven Central
- ✅ Updated to modern Android Gradle Plugin 8.x
- ✅ Updated USB-serial library to 3.7.0
- ✅ Updated all dependencies to compatible versions

## Hardware Requirements
- JRT M8xx laser rangefinder module
- USB-OTG capable Android device
- USB cable

## Original Features
- USB serial communication with JRT sensor
- Distance measurement display
- Real-time measurement updates