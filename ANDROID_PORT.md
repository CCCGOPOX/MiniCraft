# MiniCraft — Android port

This folder tracks the Android version separately from the desktop Java build.

## Source compatibility

The desktop entry point `MiniCraft.java` extends Swing's `JPanel` and uses AWT graphics/input, Swing windows, and Java Sound. Those APIs are not available in a normal Android application. The desktop source therefore cannot be copied into an APK or launched inside a WebView.

## Porting plan

1. Create a native Android application module and an Android `Activity`.
2. Move the voxel world, chunk generation, collision, inventory, crafting, mobs, and save logic into platform-neutral Java classes.
3. Replace the desktop renderer (`Graphics2D`, `BufferedImage`, pixel buffer) with an Android rendering surface.
4. Replace keyboard/mouse input with touch controls: movement joystick, look drag, jump, break/place, and hotbar.
5. Replace Java Sound with Android audio APIs and desktop file paths with app-private storage.
6. Adapt networking and test multiplayer on real devices and different networks.
7. Build and test a signed APK with GitHub Actions before enabling the download link on the website.

## Current status

The Android APK is not available yet. The website's Android button is intentionally marked as in development and does not point to a fake or empty APK. The existing desktop game remains unchanged.
