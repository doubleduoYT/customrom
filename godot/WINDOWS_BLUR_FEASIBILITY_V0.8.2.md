# v0.8.2 — Windows Full Desktop Blur Feasibility Review

Status: **research / not implemented**. This document is intentionally explicit about missing functionality: **the Windows build does not yet have real full-screen background blur**.

## Verified findings (Microsoft documentation)

1. `DwmEnableBlurBehindWindow` is a supported Win32 API, but Microsoft explicitly documents that **starting with Windows 8, calling it no longer results in a blur effect** because of a rendering style change. Do **not** implement this and mislabel a transparent tint as Gaussian blur.
   - https://learn.microsoft.com/en-us/windows/win32/api/dwmapi/nf-dwmapi-dwmenableblurbehindwindow
2. Windows 10 (build 17763)+ supports **Desktop Acrylic** as a Windows app material, and Windows 11 supports Mica. Desktop Acrylic can blur content behind an app window. This fact does **not** mean we have verified Acrylic on this project's existing borderless, topmost, per-pixel layered, click-through overlay.
   - https://learn.microsoft.com/en-us/windows/apps/develop/ui/materials
3. The current helper is a plain Win32 overlay executable using `CreateWindowExW`, `WS_EX_LAYERED`, `WS_EX_TRANSPARENT`, `WS_EX_NOACTIVATE`, and `SetLayeredWindowAttributes` with a parent-process watchdog. The current implementation only draws tinted/masked overlays.
   - Source: `godot/windows-overlay/desktop_overlay.cpp`
4. Android v0.8.1 implements a different OS API, `WindowManager.LayoutParams#setBlurBehindRadius` on Android 12+, which is unrelated to Windows backdrop behavior.

## Proposed safe prototype (separate future branch)

- Create an **isolated Windows 11 test program** using the **documented WinUI 3 DesktopAcrylicBackdrop** interface or another officially supported material path, without modifying the stable helper.
- Confirm the following **on a real Windows 11 desktop** before any merge:
  - Does Acrylic blur *other application windows* through the helper surface when fullscreen and topmost?
  - Can clicks pass through correctly with no keyboard focus theft?
  - Does Acrylic stop immediately when Overuse Controller pauses, exits, or the parent process dies?
  - Does it behave safely on multimonitor, high DPI, HDR and exclusive/fullscreen games?
  - Does it work within the project's current packaging constraints (C++17, Mingw and Godot-only binaries)? If not, assess the maintenance cost of Windows App SDK / MSVC.
- **No** screen capture, OCR, content access, screenshot storage or streaming of other applications.
- If above conditions fail, retain **only the existing tint/mask effects** on Windows and make the UI say Windows blur unavailable.

## Product decision

- **Android 12+ full-screen blur:** experimental opt-in, validated by CI only, device test outstanding.
- **Windows 10/11 full-screen blur:** not supported yet; no public promise of working system-wide blur.
- The preceding statements must be preserved in release notes, GitHub Issues and user-facing UI.
