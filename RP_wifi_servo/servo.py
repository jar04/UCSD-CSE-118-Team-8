"""
Use Wi-Fi association to toggle a door servo.

Logic:
- Poll `iw dev wlan0 station dump` every few seconds.
- If the phone's MAC is seen and the door is LOCKED -> unlock (servo to 90°).
- If the phone's MAC is NOT seen and the door is UNLOCKED -> lock (servo to 0°).
"""

import subprocess
import time
import sys

import pigpio
from time import sleep

# ===== Servo setup =====
SERVO_PIN = 18      # GPIO pin for servo
MIN_PW = 500        # pulse width for 0°
MAX_PW = 2500       # pulse width for 180°

# 0 = locked position (0°), 1 = unlocked position (90°)
lock = 0

# Connect to pigpio daemon
pi = pigpio.pi()
if not pi.connected:
    print("ERROR: Could not connect to pigpio daemon. "
          "Did you run 'sudo pigpiod'?", file=sys.stderr)
    sys.exit(1)


def set_angle(angle: int):
    """Move the servo to a specified angle (0-180 degrees)."""
    global pi
    angle = max(0, min(180, angle))  # clamp
    pulse_width = MIN_PW + (angle / 180.0) * (MAX_PW - MIN_PW)
    pi.set_servo_pulsewidth(SERVO_PIN, pulse_width)
    print(f"[set_angle] Angle: {angle}°, Pulse: {pulse_width}µs")


def toggle_servo():
    """
    Toggle servo between 0° (locked) and 90° (unlocked)
    based on the global 'lock' variable.
    """
    global lock
    if lock == 0:
        print("[toggle_servo] Currently LOCKED → moving to 90° (UNLOCK)")
        set_angle(90)
        lock = 1
    else:
        print("[toggle_servo] Currently UNLOCKED → moving to 0° (LOCK)")
        set_angle(0)
        lock = 0
    print(f"[toggle_servo] New lock state = {lock}")


# ===== Wi-Fi / station dump setup =====
WLAN_IFACE = "wlan0"
TARGET_MAC = "6a:e6:17:8c:f4:29".lower()  # <-- your phone's MAC
POLL_INTERVAL_SEC = 2                     # seconds between checks


def run_iw_station_dump() -> str:
    """Run 'iw dev wlan0 station dump' and return its stdout as a string."""
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
    Parse 'iw dev wlan0 station dump' and return a set of
    MAC addresses of connected stations.
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
    global lock

    print("[INFO] Starting Wi-Fi servo lock controller")
    print(f"[INFO] Watching MAC: {TARGET_MAC} on {WLAN_IFACE}")
    print(f"[INFO] Initial lock state (0=locked,1=unlocked): {lock}")

    # Ensure we start physically in the locked position
    set_angle(0)
    lock = 0

    while True:
        stations = get_connected_stations()
        phone_present = TARGET_MAC in stations

        print(f"[DEBUG] Connected stations: {stations}")
        print(f"[DEBUG] phone_present={phone_present}, lock={lock}")

        if lock == 0:
            # Door locked: if phone shows up → unlock
            if phone_present:
                print("[EVENT] Phone detected → UNLOCK door")
                toggle_servo()
        else:
            # Door unlocked: if phone disappears → lock
            if not phone_present:
                print("[EVENT] Phone gone → LOCK door")
                toggle_servo()

        time.sleep(POLL_INTERVAL_SEC)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n[INFO] Exiting on Ctrl+C")
    finally:
        # Clean up servo and pigpio
        pi.set_servo_pulsewidth(SERVO_PIN, 0)
        pi.stop()
        print("[INFO] pigpio cleaned up")