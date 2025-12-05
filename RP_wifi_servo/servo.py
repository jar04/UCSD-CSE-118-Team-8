#!/usr/bin/env python3
"""
Monitor Wi-Fi clients on wlan0 and toggle the door servo using
the UARTDevice class from ServoToggle.py whenever the target
phone connects / disconnects.
"""

import subprocess
import time
import sys

# Import your existing servo / BLE code
from ServoToggle import UARTDevice   # <-- your class in ServoToggle.py

# ====== CONFIG ======
WLAN_IFACE = "wlan0"
TARGET_MAC = "6a:e6:17:8c:f4:29".lower()   # phone's MAC address
POLL_INTERVAL_SEC = 2                      # how often to check Wi-Fi (seconds)


def run_iw_station_dump() -> str:
    """Run 'iw dev wlan0 station dump' and return stdout as a string."""
    try:
        result = subprocess.run(
            ["iw", "dev", WLAN_IFACE, "station", "dump"],
            capture_output=True,
            text=True,
            check=True,
        )
        return result.stdout
    except subprocess.CalledProcessError as e:
        print(f"[ERROR] 'iw' command failed: {e}", file=sys.stderr)
        return ""


def get_connected_stations() -> set[str]:
    """
    Parse the output of 'iw dev wlan0 station dump' and
    return a set of MAC addresses of connected stations.
    """
    output = run_iw_station_dump()
    stations: set[str] = set()

    # Lines look like: "Station 6a:e6:17:8c:f4:29 (on wlan0)"
    for line in output.splitlines():
        line = line.strip()
        if line.startswith("Station "):
            parts = line.split()
            if len(parts) >= 2:
                mac = parts[1].lower()
                stations.add(mac)

    return stations


def main():
    # In your ServoToggle.py: 0 = locked, 1 = unlocked
    print("[INFO] Starting Wi-Fi servo monitor")
    print(f"[INFO] Target MAC: {TARGET_MAC}")
    print(f"[INFO] Initial lock state (0=locked,1=unlocked): {UARTDevice.lock}")

    while True:
        stations = get_connected_stations()
        phone_present = TARGET_MAC in stations

        print(f"[DEBUG] Stations: {stations}")
        print(f"[DEBUG] Phone_present={phone_present}, lock={UARTDevice.lock}")

        if UARTDevice.lock == 0:
            # Currently locked: unlock when phone is present
            if phone_present:
                print("[EVENT] Phone detected → unlocking (toggle_servo)")
                UARTDevice.toggle_servo()
        else:
            # Currently unlocked: lock when phone disappears
            if not phone_present:
                print("[EVENT] Phone gone → locking (toggle_servo)")
                UARTDevice.toggle_servo()

        time.sleep(POLL_INTERVAL_SEC)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n[INFO] Exiting on Ctrl+C")
