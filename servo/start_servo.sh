#!/bin/bash

# Give Bluetooth and pigpio time to come up
sleep 8

# Start pigpio daemon
sudo pigpiod

# Run BLE servo script
python3 /home/cse118/Desktop/UCSD-CSE-118-Team-8/servo/ServoToggle.py
