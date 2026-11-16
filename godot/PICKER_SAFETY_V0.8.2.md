# Overuse Controller v0.8.2 alpha — Picker Persistence & Blur Safety Review

Status: experimental, do not merge or distribute as stable before actual Android tests. Base: v0.8.1 app picker & Android 12+ full-screen blur.

## Implemented changes

### Android app picker UX
- Searches in the list of launchable apps now preserve the existing multi-selection state, even while changing the filter text.
- When the list is loaded, apps already saved in the target package list are checked.
- The button **체크한 앱 목록 적용** merges checked launchable apps into the list and removes launchable apps explicitly unchecked by the user.
- Manual package entries that do not exist in the launchable-app list are preserved, allowing special non-launchable targets.
- **앱 선택 체크 모두 해제** lets the user quickly clear launchable-app selections; apply to commit. This action does not clear the existing input field until Apply.
- These behaviors are tested in `godot/tests/app_picker_test.gd` and CI.

### Android blur
- Android 12+ `FLAG_BLUR_BEHIND` blur remains user opt-in, disabled by default, with stepwise radii. Unsupported devices use the existing color/mask fallback.
- The service removes the overlay and blur when monitoring is paused/stopped, device locked, overlay permission revoked, or app-selection policy rejects the foreground.
- Emergency calls / dialer / Settings / maps are protected by the prior allowlist but need physical-device verification on each OEM.
- **WARNING:** Native blur appearance, tap-through input, battery use and emergency handling are not proved by CI.

### Windows
- The Windows UI explicitly states that real whole-screen Gaussian blur is **not supported yet**; color/mask overlays continue working.
- Research document: [WINDOWS_BLUR_FEASIBILITY_V0.8.2.md](WINDOWS_BLUR_FEASIBILITY_V0.8.2.md).
- Microsoft's `DwmEnableBlurBehindWindow` does **not** generate blur on Windows 8+; this branch does not attempt to fake blur by relabeling translucency as Gaussian blur.

## Regression & device test plan

- [ ] App picker initially checks saved targets, preserves checks when searching, and unchecking one removes it after Apply.
- [ ] Nonlaunchable manual package names are unaffected by picker Apply.
- [ ] Android Usage Access rejected → selected-app mode does not show overlay.
- [ ] Android selected app → blurs only on chosen apps and only at nonzero stage.
- [ ] Android protected phone, SMS, Settings, maps and overlays → blur immediately clears.
- [ ] Changing blur radius while active updates native params without a crash.
- [ ] Android 11 and power-saver mode behave safely and show unsupported status.
- [ ] Notification pause/stop removes blur and CPU limits.
- [ ] Windows 10/11 existing color/mask and CPU controls remain unchanged.
- [ ] All CI jobs pass (Godot smoke/recovery/picker, Java selection/blur policy, Android ARM64, Windows x64).

## Follow-on v0.8.3
- Android permission/foreground transition latency logging (only anonymized durations, no package history).
- A one-tap safety pause from the UI & notification verified under emergency conditions.
- Windows compositing proof-of-concept, isolated and never misrepresented as a working full-desktop blur.
