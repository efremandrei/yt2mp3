# Update Compatibility

yt2mp3 releases must install over the previous version without uninstalling it first.

Rules for every APK release:

- Keep the Android package name unchanged: `com.andre.yt2mp3`.
- Keep signing continuity. Current update-compatible releases use signer certificate SHA-256:
  `6A:FE:63:5F:1E:EB:6A:E2:DE:F1:3B:FD:D6:71:FE:E6:E1:10:3C:B9:CA:09:11:3D:4B:18:26:DE:43:3A:09:2D`.
- Increment `yt2mp3VersionCode` and `yt2mp3VersionName` in `gradle.properties` for every published APK.
- Keep Samsung-compatible `arm64-v8a` APK output unless broader ABI coverage is explicitly requested.
- If local data storage is added later, schema changes must include explicit migrations before release.

Known boundary:

- `v1.1.0`, `v1.1.1`, and `v1.1.2` share the same signer and support in-place updates.
- The original `v1.0.0` APK was signed with a different certificate. Android will not install `v1.1.x` over `v1.0.0`; users on `v1.0.0` must uninstall it before installing the current line. Do not repeat this signing break in future releases.

Verification commands:

```powershell
.\gradlew.bat clean :app:testDebugUnitTest :app:lintDebug packageDebugApks --no-daemon
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\aapt.exe" dump badging .\artifacts\yt2mp3-v<version>-build-<code>-arm64-v8a-debug.apk
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\apksigner.bat" verify --verbose --print-certs .\artifacts\yt2mp3-v<version>-build-<code>-arm64-v8a-debug.apk
```
