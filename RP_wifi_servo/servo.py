#!/usr/bin/env python3
"""
Wi-Fi based servo lock controller with:
- Persistent MAC storage (keys.csv)
- Pairing mode via GPIO 21 short press (30 sec)
- FULL RESET via 10-second button hold → clears keys.csv
- 1602A I2C LCD status display (lock state / pairing / reset)
"""

import subprocess
import time
import sys
import threading
import pigpio
from time import sleep
import os

# ===== LCD setup =====
LCD_ENABLED = True
try:
    from RPLCD.i2c import CharLCD
except ImportError:
    print("[WARN] RPLCD not installed, LCD output disabled", file=sys.stderr)
    LCD_ENABLED = False

# Change this if i2cdetect -y 1 shows a different address (e.g. 0x3F)
LCD_I2C_ADDR = 0x27
LCD_CHIP = "PCF8574"

lcd = None
if LCD_ENABLED:
    try:
        lcd = CharLCD(LCD_CHIP, LCD_I2C_ADDR, cols=16, rows=2)
    except Exception as e:
        print(f"[WARN] Failed to init LCD: {e}", file=sys.stderr)
        LCD_ENABLED = False


def lcd_show(line1: str, line2: str = ""):
    """Write up to 2 lines on the LCD (if enabled)."""
    if not LCD_ENABLED or lcd is None:
        return
    try:
        lcd.clear()
        lcd.write_string(line1[:16])
        if line2:
            lcd.cursor_pos = (1, 0)
            lcd.write_string(line2[:16])
    except Exception as e:
        print(f"[WARN] LCD write failed: {e}", file=sys.stderr)


def lcd_show_locked():
    lcd_show("Door: LOCKED", "No phone nearby")


def lcd_show_unlocked():
    lcd_show("Door: UNLOCKED", "Welcome!")


def lcd_show_pairing():
    lcd_show("PAIRING MODE", "Connect device")


def lcd_show_keys_cleared():
    lcd_show("ALL KEYS ERASED", "Hold 10s = reset")


# ===== Servo setup =====
SERVO_PIN = 18
MIN_PW = 500
MAX_PW = 2500
lock = 0  # 0 = locked, 1 = unlocked

pi = pigpio.pi()
if not pi.connected:
    print("ERROR: Could not connect to pigpio daemon. Run 'sudo pigpiod'.")
    sys.exit(1)


def set_angle(angle: int):
    angle = max(0, min(180, angle))
    pulse_width = MIN_PW + (angle / 180.0) * (MAX_PW - MIN_PW)
    pi.set_servo_pulsewidth(SERVO_PIN, pulse_width)
    print(f"[set_angle] Angle={angle}°, Pulse={pulse_width}µs")


def toggle_servo():
    global lock
    if lock == 0:
        print("[toggle_servo] LOCK → UNLOCK")
        set_angle(90)
        lock = 1
        lcd_show_unlocked()
    else:
        print("[toggle_servo] UNLOCK → LOCK")
        set_angle(0)
        lock = 0
        lcd_show_locked()
    print(f"[toggle_servo] lock={lock}")


# ===== Persistent MAC storage =====
KEYS_FILE = "keys.csv"


def load_keys() -> set:
    if not os.path.exists(KEYS_FILE):
        open(KEYS_FILE, "w").close()
        return set()
    with open(KEYS_FILE, "r") as f:
        return {line.strip().lower() for line in f if line.strip()}


def save_key(mac: str):
    mac = mac.lower()
    with open(KEYS_FILE, "a") as f:
        f.write(mac + "\n")
    print(f"[save_key] Added MAC to keys.csv: {mac}")


def clear_keys():
    """Erase all MACs from CSV and memory."""
    global AUTHORIZED_MACS
    AUTHORIZED_MACS = set()
    open(KEYS_FILE, "w").close()
    print("\n==============================")
    print("🟥 ALL MAC KEYS ERASED")
    print("keys.csv wiped, authorized list cleared.")
    print("==============================\n")
    lcd_show_keys_cleared()


AUTHORIZED_MACS = load_keys()


# ===== Wi-Fi scanning =====
WLAN_IFACE = "wlan0"
POLL_INTERVAL_SEC = 2


def run_iw_station_dump() -> str:
    try:
        result = subprocess.run(
            ["iw", "dev", WLAN_IFACE, "station", "dump"],
            capture_output=True, text=True, check=True
        )
        return result.stdout
    except Exception as e:
        print(f"[WARN] iw station dump failed: {e}", file=sys.stderr)
        return ""


def get_connected_stations() -> set:
    output = run_iw_station_dump()
    stations = set()
    for line in output.splitlines():
        if line.strip().startswith("Station "):
            mac = line.split()[1].lower()
            stations.add(mac)
    return stations


# ===== GPIO Button Logic =====
PAIR_BUTTON_GPIO = 21
PAIR_WINDOW_SEC = 10
RESET_HOLD_SEC = 10  # hold button 10 seconds to clear keys

pairing_mode = False
pairing_end_time = 0

pi.set_mode(PAIR_BUTTON_GPIO, pigpio.INPUT)
pi.set_pull_up_down(PAIR_BUTTON_GPIO, pigpio.PUD_UP)


def start_pairing_mode():
    global pairing_mode, pairing_end_time
    pairing_mode = True
    pairing_end_time = time.time() + PAIR_WINDOW_SEC
    print("\n🔵 PAIRING MODE STARTED (30 seconds)")
    print("Connect device to Wi-Fi to authorize.\n")
    lcd_show_pairing()


def stop_pairing_mode():
    global pairing_mode
    pairing_mode = False
    print("\n🟢 Pairing mode ended.")
    print(f"Authorized MACs: {AUTHORIZED_MACS}\n")
    # After pairing ends, show current lock state
    if lock == 0:
        lcd_show_locked()
    else:
        lcd_show_unlocked()


def button_watcher():
    """Detect short press (pair) and long press (reset keys)."""
    global AUTHORIZED_MACS, lock

    while True:
        if pi.read(PAIR_BUTTON_GPIO) == 0:  # button pressed
            start_time = time.time()

            # Wait until released OR long hold triggers reset
            while pi.read(PAIR_BUTTON_GPIO) == 0:
                held = time.time() - start_time

                if held >= RESET_HOLD_SEC:
                    # Long press → full reset
                    print("🟥 LONG PRESS DETECTED (10 seconds)")
                    clear_keys()
                    # return to locked position
                    set_angle(0)
                    lock = 0
                    lcd_show_locked()
                    # prevent also triggering pairing mode
                    time.sleep(1)
                    break

                time.sleep(0.1)

            else:
                # Button was released before 10 seconds → short press
                print("🔵 SHORT PRESS → Enter pairing mode")
                start_pairing_mode()

            time.sleep(0.5)  # debounce

        time.sleep(0.05)


# Start button watcher thread
threading.Thread(target=button_watcher, daemon=True).start()


# ===== Main Wi-Fi Logic =====
def main():
    global lock, pairing_mode

    print("[INFO] Wi-Fi Servo Lock System Running")
    print(f"[INFO] Loaded authorized MACs: {AUTHORIZED_MACS}\n")

    # On startup, lock door and show status
    set_angle(0)
    lock = 0
    lcd_show_locked()

    while True:
        stations = get_connected_stations()

        if pairing_mode:
            if time.time() >= pairing_end_time:
                stop_pairing_mode()
            else:
                for mac in stations:
                    if mac not in AUTHORIZED_MACS:
                        AUTHORIZED_MACS.add(mac)
                        save_key(mac)
                        print(f"[PAIR] Authorized new MAC: {mac}")
        else:
            any_present = any(mac in stations for mac in AUTHORIZED_MACS)

            if lock == 0 and any_present:
                print("[EVENT] Authorized device detected → UNLOCK")
                toggle_servo()

            elif lock == 1 and not any_present:
                print("[EVENT] No authorized device → LOCK")
                toggle_servo()

        time.sleep(POLL_INTERVAL_SEC)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n[INFO] Exiting...")
    finally:
        pi.set_servo_pulsewidth(SERVO_PIN, 0)
        pi.stop()
        print("[INFO] pigpio stopped.")
        if LCD_ENABLED and lcd is not None:
            try:
                lcd_show("Shutting down", "")
                time.sleep(1)
                lcd.clear()
            except Exception:
                pass
