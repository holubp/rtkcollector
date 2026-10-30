"""Check the foreground-service declaration without requiring Android native tools."""

from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


class AlarmManifestTest(unittest.TestCase):
    def test_connected_device_eligibility_survives_usb_permission_revocation(self):
        """The owner-approved normal qualifier must not rely on an attached USB device."""
        manifest = Path(__file__).resolve().parents[1] / "app/src/main/AndroidManifest.xml"
        root = ET.parse(manifest).getroot()
        name = "{http://schemas.android.com/apk/res/android}name"
        node = "{http://schemas.android.com/tools}node"
        permissions = {element.get(name) for element in root.findall("uses-permission") if element.get(node) != "remove"}
        self.assertIn("android.permission.CHANGE_NETWORK_STATE", permissions)
        self.assertIn("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK", permissions)
        self.assertNotIn("android.permission.FOREGROUND_SERVICE_DATA_SYNC", permissions)
        self.assertNotIn("android.permission.ACCESS_NETWORK_STATE", permissions)
        self.assertTrue(any(element.get(name) == "android.permission.ACCESS_NETWORK_STATE"
                            and element.get(node) == "remove" for element in root.findall("uses-permission")))
        service = next(element for element in root.findall("application/service")
                       if element.get(name) == ".recording.RecordingForegroundService")
        types = service.get("{http://schemas.android.com/apk/res/android}foregroundServiceType").split("|")
        self.assertEqual({"connectedDevice", "mediaPlayback"}, set(types))


if __name__ == "__main__":
    unittest.main()
