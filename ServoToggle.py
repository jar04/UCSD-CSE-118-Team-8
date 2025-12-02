import pigpio
from time import sleep

# Connect to pigpio daemon
pi = pigpio.pi()

SERVO_PIN = 18  # PWM pin

# Pulse width limits (us)
MIN_PW = 500   # 0°
MAX_PW = 2500  # 180°

def set_angle(angle):
    angle = max(0, min(180, angle))
    pulse_width = MIN_PW + (angle / 180.0) * (MAX_PW - MIN_PW)
    pi.set_servo_pulsewidth(SERVO_PIN, pulse_width)
    print(f"Angle: {angle}°, Pulse: {pulse_width}µs")

# Test
while True:
	set_angle(0)
	sleep(1)
	set_angle(90)
	sleep(1)


# Stop PWM
pi.set_servo_pulsewidth(SERVO_PIN, 0)
pi.stop()
