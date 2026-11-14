# MiniCraft — Android port

The Android edition is being developed separately from the desktop Java game.

The current desktop entry point extends Swing JPanel and depends on AWT graphics/input, Swing windows, and Java Sound. Those desktop APIs cannot simply be packaged into an Android APK.

Porting work required:
- Native Android Activity and rendering surface.
- Platform-neutral world, chunk, collision, inventory, crafting, mob, and save logic.
- Touch controls for movement, camera, jump, breaking/placing blocks, and hotbar.
- Android audio and app-private save storage.
- Multiplayer adaptation and testing on real devices/networks.
- GitHub Actions APK build and device testing.

Status: no playable Android APK has been released yet. The website button remains disabled until a real APK is available. The desktop release is unchanged.
