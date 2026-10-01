# Halo: Combat Evolved for Android

An Android-focused fork of [cybersecurity/halo-ce-universal](https://github.com/cybersecurity/halo-ce-universal),
with built-in multi-touch controls and a movable, persistent touch layout.
Repository: [theLlamaNet/halo-ce-android](https://github.com/theLlamaNet/halo-ce-android).

The game runs as an ARM64 app with OpenGL ES 3 and SDL3 audio. It requires
Android 9 (API 28) or later and a 64-bit ARM device. Game data is not included.

## Download and install

Get [the latest Android release](https://github.com/theLlamaNet/halo-ce-android/releases/latest)
or the APK archives from [GitHub Actions](https://github.com/theLlamaNet/halo-ce-android/actions).
Release builds are for playing; debug builds stop on failed game assertions
and are intended for troubleshooting. Release archives contain an APK and
third-party license notices. Install the APK on your device.

On the first launch, select an Xbox Halo: Combat Evolved disc image
(`.iso` or `.xiso`) that you own. The app extracts its `maps/` directory
(approximately 1.8 GB), then starts the game. Copy the image to your device
before opening the file picker. Saved games, maps, logs and `config.toml`
are under `/sdcard/Android/data/com.halo.decomp/files/`.

## Touch controls

You can play without a physical controller. Multiple fingers can hold
movement, aim and action controls at the same time.

| Control | Action |
| --- | --- |
| Left stick | Move |
| Right stick | Look |
| Fire / Grenade | Fire weapon / throw grenade |
| A / Jump | Jump / accept in menus |
| B / Melee | Melee / back in menus |
| X / Reload | Reload / interact |
| Y / Weapon | Switch weapon |
| Crouch / Zoom | Crouch / zoom |
| Light / Gren. type | Flashlight / switch grenade type |
| D-pad | Navigate menus |
| Pause / Back | Controller Start / Back |
| Hide / Touch | Hide / show the gameplay overlay |

Touch controls merge with the first physical controller for player 1.
Bluetooth and USB controllers still work, including additional players.
Touch inputs are released when you hide the controls or leave the app.

### Customize the layout

1. Tap **Edit** at the top right.
2. Drag any action button, D-pad button or either stick to a new position.
3. Tap **Save and exit** to save the layout and resume playing.

Saved positions are restored on the next launch. The layout scales with
the display and respects its safe area and screen cutouts. Moved controls
stay within the display and below the editor toolbar, so **Save and exit**
remains reachable. While editing, touch controls do not send game inputs;
the game itself keeps running, so pause first when needed. Clearing app
data resets the saved layout.

## Build

Install Python, ninja, CMake, JDK 17+, the Android SDK (API 35) and NDK,
and a clang with the `arm64_32` target. The first build downloads musl,
SDL3, Gradle and the Android Gradle Plugin.

On Windows, install Git for Windows too: its Bash runs the native build
commands, and the Windows NDK compiler can build the guest and host.
Set `ANDROID_HOME` to the SDK folder. Linux can also be used as a build host.

```sh
python configure.py --release --pgo=off
ninja android_apk
```

The installable APK is at
`port/android/app/build/outputs/apk/debug/app-debug.apk`. This command uses
the Gradle debug package with a release-mode native engine. For a full
release package matching CI, run:

```sh
python tools/ci_build.py android release
```

Its APK and license notices are collected in `dist/halo-android-release/`.
`python configure.py` builds an engine with assertions; `ninja android`
builds only the native engine and libraries. `ninja` defaults to the APK.
The inherited optimization profile requires clang 22+; `--pgo=off` disables it.

Run the layout regression checks with:

```sh
python tools/test_touch_layout.py
```

## Android-only source layout

The standalone Windows and Linux ports, their build targets and CI jobs
have been removed. `port/shared/` contains the engine compatibility layer,
renderer, networking and game changes that Android needs. Some internal
identifiers retain their upstream `halo_linux` names for compatibility.
The `android_windows_*.py` helpers build Android on a Windows computer;
they do not build a Windows version of the game.

See [Android setup, settings and troubleshooting](port/android/README.md),
[shared engine settings](port/shared/README.md#settings) and
[netcode details](port/shared/NETCODE.md).

## Multiplayer and updates

The inherited system link networking supports LAN play and internet invite
links, and remains compatible with the upstream networking protocol.
Official fork builds check releases of **theLlamaNet/halo-ce-android**.
Local builds without a build number do not check for updates.

GitHub Actions builds Android debug and release packages. Successful builds
of `main` publish a release. Configure the repository's
`ANDROID_KEYSTORE_BASE64` and `ANDROID_KEYSTORE_PASSWORD` secrets to keep a
stable signing key across builds (key alias `halo`). Without them, each CI
runner uses its own debug key, so its APK may not install over an earlier
build. Keep signing keys and game data out of the repository.

## Known limitations

- Bink videos are skipped.
- The native engine requires fixed guest memory addresses below 4 GB.
- Devices using 16 KB kernel pages are currently unsupported.
- A full editor/gameplay test still requires an Android device.

## Credits and license

This fork builds on the Android port in
[cybersecurity/halo-ce-universal](https://github.com/cybersecurity/halo-ce-universal),
which starts from [bnunu/halo-1](https://github.com/bnunu/halo-1), a fork of
[punpckhdq/halo](https://github.com/punpckhdq/halo).
The decompilation is based on Xbox build 2342 (`cachebeta.exe`).
See [LICENSE.md](LICENSE.md) and the notices in `port/third_party/`.
