# Universal Streaming Remote — Android v0.3.0

Native Android app written in Kotlin and Jetpack Compose. This repository is **source code**, not a pre-built APK. It has NOT been compiled or tested on a physical TV in the current environment.

## What works in code

- A dark, high-tech Compose interface with selectable saved devices.
- A **Fusion Control Deck** that routes TV volume/power keys to one device and navigation/media keys to another.
- Two full remote panels on one vertically scrollable screen.
- Roku External Control Protocol (ECP) over LAN: discover through SSDP, verify device on TCP 8060, send supported key commands, query installed apps, launch apps, and send text to focused text fields. Roku must have network control enabled and share the phone's local network. Some controls depend on Roku model and settings.
- Samsung and LG legacy IR command profiles for common basic TV keys. IR works **only on an Android device with an actual built-in IR transmitter**; a standard Pixel phone does not include one. Codes vary by TV model and are not guaranteed to match every model.
- Persistent saved devices and selected combinations.

## Not yet implemented

- Fire TV / Fire Stick network ADB authorization and command driver.
- onn / Google TV Android TV Remote v2 secure pairing and command driver.
- Wi-Fi drivers for proprietary Samsung Tizen, LG webOS, Sony, Hisense and Vizio TVs.
- Universal device-code import, Bluetooth remotes, HDMI-CEC control, and third-party USB IR adapters.

These device families appear in the device picker for layout planning only and are **not advertised as functional**.

## Build an installable APK in Android Studio on Windows

1. Install Android Studio from https://developer.android.com/studio and the Android SDK 35.
2. Extract the ZIP and open the `UniversalStreamingRemote` directory in Android Studio (the directory containing `settings.gradle.kts`).
3. Let Android Studio sync Gradle from the internet. Android Studio may prompt to install Android SDK components; accept.
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Find the debug APK at `app/build/outputs/apk/debug/app-debug.apk`.
6. Transfer it to your Android phone and install it, approving installation from that source if Android asks.

To run directly from Android Studio, enable USB debugging on the phone and press Run.

## Test Roku on a real device

1. Connect your phone and Roku to the same local network. Avoid guest networks that isolate clients.
2. On Roku, check **Settings > System > Advanced system settings > Control by mobile apps > Network access** (names vary by Roku OS version); enable mobile-device control.
3. Open the app, tap the plus button, choose Roku, and use **Find Roku on Wi-Fi**. If discovery fails, get the Roku's IP in Settings > Network > About and enter it manually.
4. Select Roku as Main Remote and test Home, directional keys and OK.
5. To create a combined remote, add a second usable device and switch on **Combined Remotes**. Set TV/SOUND and STREAMING targets independently.

## Implementation and security notes

- Roku network calls are HTTP to RFC1918/link-local IPv4 addresses only, with brief timeouts and no remote Internet endpoints.
- Device configurations use local SharedPreferences. No telemetry or cloud sync.
- Manifest requests Internet, Wi-Fi state, multicast reception and vibration; IR hardware is optional.
- Android `usesCleartextTraffic=true` is required for Roku ECP's HTTP service; do not use this app over untrusted Wi-Fi.
- Local network discovery can fail on some access points or Android devices; manual IP entry is available.

## Versions

- v0.2: original native Roku and multi-panel layout.
- v0.3: scrollable Fusion layout, built-in Samsung/LG IR profiles, IP validation, and clarified unsupported devices.
