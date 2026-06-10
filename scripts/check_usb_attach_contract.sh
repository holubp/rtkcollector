#!/bin/sh
set -eu

manifest="app/src/main/AndroidManifest.xml"
filter="app/src/main/res/xml/usb_device_filter.xml"

grep -q 'android.hardware.usb.action.USB_DEVICE_ATTACHED' "$manifest"
grep -q '@xml/usb_device_filter' "$manifest"
test -f "$filter"
grep -q 'vendor-id="1027"' "$filter"
grep -q 'product-id="24597"' "$filter"
