# Android Studio emulator на Windows host (визуальная разработка)

**Цель:** видеть Korus Android client **на экране** — окно эмулятора на мониторе, не headless lab в QEMU.

| Путь | Окно на экране | Назначение |
|------|----------------|------------|
| **Android Studio AVD (этот doc)** | Да | Ручная разработка, демо, отладка UI |
| QEMU `korus-mobile-build` | Нет | Maestro, conveyor, CI-style smokes |

**WSL не нужен.** Стек API — в QEMU (`127.0.0.1:18080`), как в остальном проекте.

## 1. Установка

1. [Android Studio](https://developer.android.com/studio) (SDK + Platform Tools + Emulator).
2. **SDK Manager:** API 35 platform, build-tools, **Android Emulator**, system image **API 28+** (minSdk 26, targetSdk 35).
3. **Device Manager → Create Virtual Device** (например Pixel 2, API 28 или 34).
4. Переменная окружения (рекомендуется):
   ```powershell
   [Environment]::SetEnvironmentVariable('ANDROID_HOME', "$env:LOCALAPPDATA\Android\Sdk", 'User')
   ```

## 2. WHPX / Hyper-V

Korus QEMU VM и Android Emulator **делят WHPX** на Windows.

- Для «смотреть UI» обычно достаточно: **korus-server** (`:18080`) + **AS emulator** — без `korus-mobile-build`.
- Если эмулятор AS тормозит или QEMU падает — не держите все VM + AVD под полной нагрузкой.

## 3. Самостоятельный запуск (один вход)

```powershell
.\scripts\mobile-android-studio.ps1
```

По умолчанию сам:
1. поднимает QEMU lab API (`:18080`), если down;
2. стартует AVD (окно на экране), если нет `adb device`;
3. собирает debug APK, ставит, открывает `MainActivity`.

Уже собранный APK:

```powershell
.\scripts\mobile-android-studio.ps1 -SkipBuild
```

Не трогать стек / эмулятор:

```powershell
.\scripts\mobile-android-studio.ps1 -NoStartStack -NoLaunchEmulator
```

По умолчанию API в приложении: `http://10.0.2.2:18080` (эмулятор → host `127.0.0.1:18080`).

Тестовый логин (lab): см. `mobile/maestro/w0-login.yaml` или smoke users в `scripts/smoke-mobile-auth.ps1`.

## 4. Android Studio Run ▶

1. Open project root или `mobile/mobile-client-android`.
2. Run configuration: **mobile-client-android**.
3. Target: ваш AVD.

Gradle вручную:

```powershell
.\gradlew.bat :mobile:mobile-client-android:assembleDebug
```

## 5. Maestro на host AVD

```powershell
adb devices
maestro test mobile\maestro\w0-login.yaml
```

Maestro CLI: https://maestro.mobile.dev/

## 6. Troubleshooting

| Симптом | Действие |
|---------|----------|
| `adb: no devices` | `-LaunchEmulator` или Device Manager → Start AVD |
| API connection failed | `curl http://127.0.0.1:18080/api/v1/health` → `qemu-up` |
| Cleartext blocked | URL должен быть `http://10.0.2.2:18080`, не `https` |
| INSTALL_FAILED | `adb uninstall com.avandocmsg.messenger.mobile` и снова install |
| SDK not found | `ANDROID_HOME`, SDK Manager API 35 |

См. также [`README.md`](README.md) — headless QEMU lab для автоматизации.
