# MiniCraft — Android port

This branch contains the first native Android prototype and an automated APK build.

## Implemented in this prototype

- Native Android Activity (no WebView).
- OpenGL ES 2.0 rendering surface.
- Procedurally generated small block terrain.
- Basic on-screen movement, jump, break and place controls.
- GitHub Actions workflow that builds a debug APK and uploads it as an artifact.

## Not implemented yet

This is not a complete port of the desktop game. The original `MiniCraft(6).java` uses Swing/AWT and a software voxel raycaster. The prototype currently does not reuse that engine and does not yet include the original chunk system, inventory/crafting, survival/health, mobs, day/night, world persistence, audio, or multiplayer. Those systems need to be ported and tested separately.

## Build

Pushes to `android-port` run `.github/workflows/android-apk.yml`. When the workflow succeeds, download the `MiniCraft-Android-debug` artifact from the Actions run. This debug APK is for testing, not a Play Store release.

Do not enable the Android download button on the website until an APK has been built and tested on a real phone.
