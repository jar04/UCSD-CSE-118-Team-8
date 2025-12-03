#!/usr/bin/env python3
import pigpio
from time import sleep

from gi.repository import GLib

# Bluezero modules
from bluezero import adapter
from bluezero import peripheral
from bluezero import device

# === Servo setup ===
SERVO_PIN = 18           # GPIO pin for servo
MIN_PW = 500             # 0°
MAX_PW = 2500            # 180°

pi = pigpio.pi()

def set_angle(angle: int):
    """Move the servo to a specified angle (0-180 degrees)."""
    angle = max(0, min(180, angle))  # clamp
    pulse_width = MIN_PW + (angle / 180.0) * (MAX_PW - MIN_PW)
    pi.set_servo_pulsewidth(SERVO_PIN, pulse_width)
    print(f"Angle: {angle}°, Pulse: {pulse_width}µs")


def toggle_servo():
    """Simple 0° -> 90° toggle motion."""
    print("Toggling servo 0° -> 90°")
    set_angle(0)
    sleep(1)
    set_angle(90)
    sleep(1)


# === BLE Nordic UART Service setup ===
UART_SERVICE       = '6E400001-B5A3-F393-E0A9-E50E24DCCA9E'
RX_CHARACTERISTIC  = '6E400002-B5A3-F393-E0A9-E50E24DCCA9E'  # write from phone
TX_CHARACTERISTIC  = '6E400003-B5A3-F393-E0A9-E50E24DCCA9E'  # notify to phone (optional)


class UARTDevice:
    """
    BLE UART "device" handler.

    - When a central writes to RX, uart_write is called.
    - We echo back to TX (optional) and trigger servo if value == 'toggle'.
    """
    tx_obj = None  # characteristic object used for notifications

    @classmethod
    def on_connect(cls, ble_device: device.Device):
        print("Connected to", ble_device.address)

    @classmethod
    def on_disconnect(cls, adapter_address, device_address):
        print("Disconnected from", device_address)

    @classmethod
    def uart_notify(cls, notifying, characteristic):
        """Called when notifications are enabled/disabled on TX characteristic."""
        if notifying:
            print("TX notifications enabled")
            cls.tx_obj = characteristic
        else:
            print("TX notifications disabled")
            cls.tx_obj = None

    @classmethod
    def update_tx(cls, value: bytes):
        """Send data back to central (if notifications enabled)."""
        if cls.tx_obj:
            print("Sending notification back to central")
            cls.tx_obj.set_value(list(value))

    @classmethod
    def uart_write(cls, value, options):
        """
        Called when central writes to RX characteristic.

        value    : list[int] (byte values)
        options  : dict with write options
        """
        try:
            raw_bytes = bytes(value)
            text = raw_bytes.decode("utf-8").strip()
        except Exception as e:
            print("Error decoding value:", e)
            text = ""

        print("=== BLE RX ===")
        print("raw bytes:", value)
        print("options  :", options)
        print("text     :", repr(text))

        # Echo back whatever was sent (optional)
        cls.update_tx(raw_bytes)

        # Our custom command: toggle servo when text == "toggle"
        if text.lower() == "toggle":
            toggle_servo()
        else:
            print("Command not recognized, ignoring.")


def main():
    # Get adapter address (e.g., your hci0 MAC)
    adapter_address = list(adapter.Adapter.available())[0].address
    print("Using adapter:", adapter_address)

    # Create BLE peripheral with Nordic UART Service
    ble_uart = peripheral.Peripheral(adapter_address, local_name='BLE UART Servo')

    # Add NUS service
    ble_uart.add_service(srv_id=1, uuid=UART_SERVICE, primary=True)

    # RX characteristic (phone -> Pi writes)
    ble_uart.add_characteristic(
        srv_id=1,
        chr_id=1,
        uuid=RX_CHARACTERISTIC,
        value=[],
        notifying=False,
        flags=['write', 'write-without-response'],
        write_callback=UARTDevice.uart_write,
        read_callback=None,
        notify_callback=None
    )

    # TX characteristic (Pi -> phone notifications)
    ble_uart.add_characteristic(
        srv_id=1,
        chr_id=2,
        uuid=TX_CHARACTERISTIC,
        value=[],
        notifying=False,
        flags=['notify'],
        notify_callback=UARTDevice.uart_notify,
        read_callback=None,
        write_callback=None
    )

    # Set connect/disconnect callbacks
    ble_uart.on_connect = UARTDevice.on_connect
    ble_uart.on_disconnect = UARTDevice.on_disconnect

    # Start advertising & GATT server (this blocks)
    print("Publishing BLE UART Servo peripheral...")
    ble_uart.publish()
    # publish() starts the GLib main loop internally, so script will keep running


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("Shutting down...")
    finally:
        # Stop servo PWM and cleanup pigpio
        pi.set_servo_pulsewidth(SERVO_PIN, 0)
        pi.stop()
