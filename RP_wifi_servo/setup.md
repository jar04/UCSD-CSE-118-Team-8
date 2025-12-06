# 1
nmcli device wifi list

# 2
nmcli device wifi connect "YOUR_HOME_WIFI" password "YOUR_PASSWORD"

# 3
nmcli connection show

# 4
nmcli connection add type wifi ifname wlan0 con-name myAP autoconnect yes ssid MyPiAP

# 5
nmcli connection modify myAP \
  802-11-wireless.mode ap \
  ipv4.method shared \
  wifi-sec.key-mgmt wpa-psk wifi-sec.psk "12345678"

# 6
nmcli connection up myAP

# 7
nmcli connection show

# If AP disconnects when STA connects (common issue)
nmcli connection modify myAP 802-11-wireless.channel 0