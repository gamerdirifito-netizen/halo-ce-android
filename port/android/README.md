# Android

`ninja android` builds the game for 64-bit ARM Android (arm64-v8a).
`ninja android_apk` makes an app from it:
`port/android/app/build/outputs/apk/debug/app-debug.apk`.

The game shows its graphics with OpenGL ES 3. It plays sound through SDL3
(AAudio). It accepts input from game controllers, for example a PlayStation
5 DualSense on Bluetooth. The app needs Android 9 (API 28) or later. It
operates on 64-bit-only devices, for example the Pixel 9 Pro XL.

The Android build uses the shared engine layer in `port/shared`.
See [shared engine settings](../shared/README.md#settings).

## Requirements

You do not need the Xbox SDK. Install Python, ninja and these items:

- A clang with the `arm64_32` target, for example the clang of the system.
  The option `--android-guest-cc` of `configure.py` selects a different
  compiler.
- The Android NDK. `configure.py` looks for it in `ANDROID_NDK_HOME`, then
  in `$ANDROID_HOME/ndk`, `~/Android/Sdk/ndk` and `/opt/android-sdk/ndk`.
  The option `--android-ndk` selects a different NDK.
- CMake, and a JDK 17 or later for Gradle.
- A network connection for the first build. `configure.py` downloads musl
  1.2.5 and SDL 3.4.16 to `build/android/third_party`. Gradle downloads the
  Android Gradle Plugin.

On Windows, install Git for Windows as well. Its Bash runs the native
build commands; the Windows NDK's clang builds both the guest and host.
Set `ANDROID_HOME` to the SDK folder, including NDK and Android API 35.
The local Gradle cache and Android user files are kept under `build/`.

## Build and install the app

1. Go to the root folder of the repository.
2. Enter `python configure.py`.
3. Enter `ninja android_apk`.
4. Connect the device with adb.
5. Enter `adb install -r port/android/app/build/outputs/apk/debug/app-debug.apk`.

`ninja android` builds only the game image and the native libraries.

## Game data

The game needs the `maps/` folder from an Xbox disc image (`.xiso` or
`.iso`) of any version of the game. The app extracts `maps/` from the disc
image. The app keeps the data in `/sdcard/Android/data/com.halo.decomp/files`.

To install the data with the app:

1. Copy the disc image to the phone.
2. Start the app.
3. Push the button. The file picker of the system opens.
4. Select the disc image.
5. Wait while the app extracts the data (approximately 1.8 GB). Then the
   game starts.
6. You can delete the disc image.

To install the data from a computer:

1. Start the app one time. The app makes its folders.
2. Enter `adb push <folder>/. /sdcard/Android/data/com.halo.decomp/files/`.

| Item | Location in `/sdcard/Android/data/com.halo.decomp/files` |
| --- | --- |
| Saved games (`z:\` and `u:\`) | `save` |
| Log | `debug.txt` |
| Settings | `config.toml` |

To make a copy of the saved games, enter
`adb pull /sdcard/Android/data/com.halo.decomp/files/save`.

The main menu build label shows **FulGer** for all imported disc versions.

## Controls

The game reads controllers through the gamepad functions of SDL3. All the
controllers that Android knows operate. The first controller is player 1.
The other controllers are players 2 to 4 (split screen). The buttons agree
with the positions on the Xbox controller:

| DualSense | Xbox | Function in the game |
| --- | --- | --- |
| left stick, right stick | left stick, right stick | move, look |
| R2 | right trigger | fire |
| L2 | left trigger | throw a grenade |
| Cross | A | jump, accept |
| Circle | B | melee, back |
| Square | X | action, reload |
| Triangle | Y | change the weapon |
| L1 | white | flashlight |
| R1 | black | change the grenade |
| L3, R3 | left and right stick clicks | crouch, zoom |
| D-pad | D-pad | |
| Options | start | pause menu |
| Create | back | |

The controller gets the rumble. The back gesture of Android is the B
button. Bluetooth and USB keyboards also work. The screen
accepts multi-touch input through an on-screen controller for player 1.
The left stick moves; drag anywhere outside it to look around. Looking uses
relative finger displacement directly, with no right stick acceleration.
Action buttons stay held while dragging, so either Fire button can also aim.
A second Fire button on the left lets you shoot while aiming with the right hand.
Use A to accept menu items and B to go back, and the on-screen D-pad to
navigate. Touch **Hide** to hide the controls and **Touch** to show them again.
Hiding controls or leaving the app releases every touch input.

### Touch options

Tap the circular, white-outline **Options** button to open:

- **Edit buttons layout**: drag the buttons and movement stick anywhere
  across the full display, up to its edges. Tap the circular **Save and exit**
  button to save and resume. The game keeps running; pause first to arrange
  the controls during a campaign or match. Existing button positions are retained;
  the old look stick is replaced by the additional Fire button. The circular
  **Export** and **Import** buttons open a confirmation popup followed by
  Android's document picker. Export lets you choose the folder and filename;
  import lets you browse for a `.halolayout` file and applies it immediately.
  Files preserve positions, hidden controls, duplicates, individual sizes,
  look sensitivity, Rumble and Gyroscope settings.
  Invalid files are rejected before changing the current layout.
- **General** contains **Rumble**, **Gyroscope aim (Experimental)**,
  **Hide or add buttons** and **Edit buttons size**.
  Rumble defaults to on and mirrors player 1's controller motor requests to
  the smartphone vibrator, even without a physical controller or when the
  Xbox profile disables controller vibration. Phone effects come directly from
  player 1's rumble update and use Android game audio attributes with a
  perceptible minimum amplitude. It preserves
  controller rumble and stops phone vibration when disabled or the app loses focus.
  Gyroscope aim defaults to off, uses the phone's angular velocity to add yaw
  and pitch to touch aiming with corrected vertical tilt direction, supports
  both landscape orientations, and follows
  look sensitivity. Sensors stop when the app is unfocused; motion does not
  reach the game while Options or the layout editor is open. Hardware-dependent
  toggles are disabled when the device has no vibrator or gyroscope.
  Hide/add lets you show or hide controls, restore a hidden button with
  **Add button**, and **Duplicate** any action button. The movement stick can
  be hidden/restored. **Reset** restores original positions, visibility and
  sizes and removes all copies, after confirmation.
  Edit size lists all controls, including hidden buttons and copies, with
  **Ã¢Ë†â€™**, **+** and a percentage value (50Ã¢â‚¬â€œ200%, in 10-point steps). Drawing,
  hit areas and the movement stick range resize together. Copies inherit the
  source size and can then be resized independently.
- **Look sensitivity**: adjust the slider from 0.25x to 4x. This setting and
  the layout are saved in private app preferences and restored next launch.
- **Cheats**: all ten built-in cheat flags and six cheat actions from Halo CE,
  including the prototype init.txt / cheats.txt commands. Toggle rows become
  green only after the game applies the change; tap again to disable them.
  Instant actions (weapons, powerups, vehicles, camouflage, teleport) can be
  repeated and keep their neutral background, since they are not switches.
  Only enabled toggle cheats have a green row. Available spawned objects
  depend on the current map.
  Controller cheats use prototype button shortcuts when no cheats.txt is installed.
  Cheats require an active player; network clients follow the host's existing rules.

The version 2 `.halolayout` format saves all these settings. Version 1 files
remain supported, with default sizes, Rumble on and Gyroscope off. Positions
use display-relative coordinates while button circles keep their proportions.
Clearing app data also clears touch and General settings. Test gyro response
and phone rumble on actual Android hardware before relying on them.

## Settings

The settings are in `config.toml` in the data folder of the app. To change
them:

1. Enter `adb pull /sdcard/Android/data/com.halo.decomp/files/config.toml`.
2. Change the file.
3. Enter `adb push config.toml /sdcard/Android/data/com.halo.decomp/files/`.

At the first start, the game writes the file with the default values. To
get the default values again, delete the file.

The shared engine settings apply to Android, except desktop window,
mouse and path settings. Refer to [port/shared/README.md](../shared/README.md#settings).
These settings are only for Android:

| Setting | Function |
| --- | --- |
| `display.screen_width` | The number of columns of the 480-line picture. `0` (the default): the shape of the display (1068 on a 20:9 phone). `640`: the 4:3 shape of the Xbox. |
| `debug.sample_seconds` | Refer to "Find problems". |

## Internet play

Internet play uses system link networking and invite links. When the game
hosts a system link game, it puts the invite link on the clipboard and
shows a notice.

To join a game, do one of these steps:

- Open the link. The app is the handler of `halo://join/...` links. If the
  game does not operate, the app starts it. The app writes the link to
  `files/join_link.txt`, and the game reads it.
- Copy the link and go to the game.

On the local network:

- The game uses the address of the Wi-Fi (or of the hotspot of the
  phone), not the address of the mobile data.
- The app holds a Wi-Fi multicast lock while the game operates. Some
  phones otherwise drop the broadcasts that find system link games.

Keep the game in the front during a network game. When the app goes to the
background, Android stops the game. After 15 seconds the other machines
drop it, and when it hosts, its players leave.

## Updates

The app from GitHub Actions checks releases of
[theLlamaNet/halo-ce-android](https://github.com/theLlamaNet/halo-ce-android).
Upstream commits are synchronized automatically after tests and builds;
GitHub tags and releases are created manually. See the root README's
[synchronization and manual release instructions](../../README.md#automatic-upstream-synchronization).
When a newer build is found and you select "Yes":

1. The app downloads the new version.
2. The package installer of Android opens. At the first update, Android asks
   you to let Halo install apps. Allow it.
3. Select "Update". Android replaces the app.
4. Select "Open" to start the new version.

To install over the previous version, each build must have the same
signature. GitHub Actions signs each build with the key in the
`ANDROID_KEYSTORE_BASE64` and `ANDROID_KEYSTORE_PASSWORD` secrets of the
repository. If you installed a build that has a different signature, remove
that build before you install a new build. Removing the app deletes its data
folder: first make a copy of `maps/` and `save/`.

## Widescreen

The game shows 480 lines in the shape of the display, not the 640x480 of
the Xbox:

- The 3D view is wider. The camera keeps the vertical field of view.
- The HUD stays at the edges of the screen.
- The menus, the loading bar and the screens after a game have 640
  columns, at the center of the screen.
- Black bars and fades cover all of the screen, and so do the menus' dims
  and backgrounds (the pause menu's dim, dialogs, the menus' gradient).

The changes are in `#ifdef HALO_ANDROID` in `rasterizer_xbox.c`, `render.c`,
`ui_widget.c`, `cinematics.c`, `main.c` and
`rasterizer_xbox_screen_effect.c`.

## How the port operates

### ILP32 code

The data of the game contains 32-bit pointers. The cache files have the
layout of the Xbox memory. The saved games are copies of the memory.
Direct3D resources contain 32-bit physical addresses. Current Android
devices cannot execute 32-bit ARM code. 64-bit pointers change the layout of
the structures that the game reads from its files.

Thus the game is ILP32 AArch64 code: 64-bit ARM instructions with 32-bit
`int`, `long` and pointers, in a 64-bit app.

### The guest image

The guest is the game, the platform layer and a small runtime:

1. clang compiles the guest for `arm64_32-apple-watchos`, the only ILP32
   AArch64 target of clang. The options `-U__APPLE__` and
   `-fno-define-target-os-macros` hide the Darwin environment.
2. `tools/android_asm_convert.py` changes the Mach-O assembly to ELF
   assembly.
3. The AArch64 assembler makes the objects.
4. `ld.lld` links the objects with `guest/guest.ld` to a static image,
   `build/android/halo_guest.elf`, at a fixed address above the Xbox memory
   (`include/halo_android_abi.h`).

The APK contains the image as an asset.

The C library of the guest is a part of musl for a new `arm64_32`
architecture (`guest/libc/arch/arm64_32`). It has ILP32 types and a 32-bit
`time_t`, as in the MSVC runtime of the game. Its system calls go to the
host (`syscall_arch.h`). `guest/runtime/guest_thread.c` makes the threads
and supplies the thread pointer and TLS.

### The host library

`libmain.so` is an arm64 NDK library. The activity of SDL3 starts it
(`app/.../HaloActivity.java`). The host library:

- Reserves the address space of the guest below 4 GB: the Xbox memory at
  `0x80000000`, the image, and pools for the memory of the guest
  (`host/host_memory.c`).
- Loads the image and fills its import table (`host/host_loader.c`).
- Starts the `main` of the game and each guest thread on a stack in guest
  memory, because ILP32 code keeps stack addresses in 32-bit registers
  (`host/host_thread.c`).
- Gives the audio callback of SDL to a thread with a guest stack
  (`host/host_sdl.c`).
- Does the calls of the guest: system calls (`host/host_syscall.c`), SDL
  (`host/host_sdl.c`), OpenGL ES (`host/host_gl.c`), and the file and socket
  functions of `port/shared/src/posix_*.c`.

The guest calls the host through stubs (`tools/android_imports.py`). The
two ABIs use the same registers for 32-bit integers, floats and pointers.
`tools/android_gl_stubs.py` makes the OpenGL ES stubs from
`port/shared/src/gl.h`. `tools/android_posix_stubs.py` makes the stubs of the
`posix_*` functions, which copy the `errno` of the host.

### OpenGL ES

The renderer (`port/shared/src/d3d8_gl.c`) uses OpenGL ES 3.0, and some
functions of OpenGL ES 3.2 if they are available:

- The vertex shaders flip y and change the depth range from 0..1. The front
  face winding is inverted.
- BGRA textures go to the GPU as RGBA with a swizzle. If the driver has no
  S3TC (Mali GPUs), the CPU decodes the DXT textures.
- The pixel shaders apply the LOD bias of the sampler.
- The upload changes the byte order of `D3DCOLOR` vertex attributes.
- Dynamic vertex and index data goes into a ring of three buffers, one for
  each frame. On Mali, other methods used too much memory.
- On OpenGL ES 3.2, indexed draws use a base vertex. Before 3.2, the CPU
  changes the indices.
- On OpenGL ES 3.1 and later, the visibility tests (lens flares) count
  samples with an atomic counter, as the NV2A did. OpenGL ES 3.0 tells only
  if a sample is visible.

### Calling conventions

Some files of the game declare a function differently from its definition,
or call a function without a prototype. On 32-bit x86, this has no effect.
The guest ABI passes floating-point arguments in their own registers and
variadic arguments on the stack. Thus such a call gives incorrect values.

`tools/android_abi_check.py` compares each declaration with its definition
in the LLVM IR. The problems are repaired:

- `hs.c` declared the red component of the script fades as `long`.
- Some files call `error`, `console_printf` or `terminal_printf` without a
  prototype. `include/halo_android_variadic_prototypes.h` gives the
  prototypes.

`guest/runtime/guest_misc.c` supplies the Darwin library functions that the
target calls (`__sincos_stret`, `__exp10f`). The guest compiles without
floating-point contraction, as on x86.

### Game source changes

The original x86 inline assembly is replaced by portable C.
These changes are in `#ifdef HALO_ANDROID`:

- Seven `#pragma bss_seg(".bss")` lines are removed. The Darwin target does
  not accept them.
- A stack walker follows the AArch64 frame records. Thus the log of an
  assertion (`debug.txt`) shows the call sites.

The musl of the guest uses its C math, not the AArch64 assembly. The only
assembly of the port is necessary:

- The import stubs. A 32-bit guest cannot keep or go to a 64-bit host
  address.
- The symbol aliases in `guest/libc/src_include/features.h`. The Darwin
  target does not accept alias attributes.

## Find problems

- Enter `adb logcat -s halo` to see the log of the port and the errors of
  the game. `files/debug.txt` is the log of the game.
- If the guest code stops, the log shows the registers and the frame chain.
  To find the functions, enter
  `llvm-symbolizer --obj=build/android/halo_guest.elf <address>`.
- Set `sample_seconds = <seconds>` in `[debug]` of `config.toml`. The log
  then shows the program counter and the frame chain of each guest thread
  at this interval. This finds hangs on devices without root access.
- Set `gl_debug = true` in `[debug]` of `config.toml`. The log then shows
  the OpenGL ES errors.

## Limits

- Bink video is not available. The game skips the movies.
- The device must let the app reserve the fixed guest addresses, from
  `0x80000000` to approximately `0x89000000`. If the addresses are not
  available, the app shows a message.
- Kernels with 16 KB pages (a developer option of Android 15) do not
  operate. The Xbox memory uses 4 KB pages.
