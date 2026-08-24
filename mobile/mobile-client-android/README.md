# Mobile Android Client (spec 032)

Compose + `mobile-client-sdk`. Build requires **Android SDK** and `ANDROID_HOME`.

## W0 target

- Login screen (`server_url`, `username`, `password`, `login_button` test tags)
- On success: `logged_in_label` with user login
- Token in EncryptedSharedPreferences via platform store

## Build

**Recommended (QEMU guest):**

```powershell
.\scripts\qemu-mobile-build-up.ps1
.\scripts\package-mobile-android.ps1
```

**Host build** (local Android SDK): `.\scripts\package-mobile-android.ps1 -ForceHost`

Or from repo root: `.\gradlew.bat :mobile:mobile-client-android:assembleDebug`

Emulator API: use `http://10.0.2.2:18080` (host forwarded QEMU port).

## Maestro

```bash
maestro test ../maestro/w0-login.yaml
```

Engineer: implement per `specs/032-mobile-native-client/design/implementation-blueprint.md`.

SDK dependency: project `mobile-client-sdk` (Gradle include).
