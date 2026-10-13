# Throughline Smart TV

Flutter Android app with an Android TV launcher entry, D-pad-friendly dashboard, and persistent global chat.

## Build

```powershell
flutter pub get
flutter build apk --release --dart-define=API_BASE_URL=https://129-213-21-239.sslip.io
```

Install the APK on an Android TV or emulator. TV devices can use a remote to focus and open dashboard actions; use the on-screen keyboard to chat. The production backend is configured as the default; override `API_BASE_URL` only when targeting another server.
"# throughline_smart_tv_android" 
