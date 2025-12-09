#!/bin/bash

# Start pigpio daemon
sudo pigpiod

# Allow daemon to fully initialize
sleep 2

# Run the servo script
python3 servo.py
