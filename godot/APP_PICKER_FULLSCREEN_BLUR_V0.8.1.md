# Overuse Controller v0.8.1-alpha — Installed App Picker + Full-screen Blur

Status: experimental feature branch, requires Android real-device verification before merging or distributing. Base: v0.8.0 selective-intervention branch. Neither main nor the previous feature branches are changed.

## 1. Installed app picker (Android)

- Adds **"설치된 앱 불러오기"** in the Godot settings screen, searchable by **display name / package name**.
- Multi-select entries and press **"선택한 앱을 대상 목록에 추가"**. The app list is only loaded when the user explicitly presses the button.
- The manual package-name editor is still available. Saved target packages are normalized by the existing AppSelectionPolicy (max 30).
- The app list is limited to **launchable apps**, discovered through ACTION_MAIN + CATEGORY_LAUNCHER with a scoped `<queries>` manifest block.
- The APK does **not** declare QUERY_ALL_PACKAGES and does **not** send the installed-app list to a server.
- Known protected system/dialer/SMS/map packages and the current controller are excluded. Manufacturer-specific or nonlaunchable app packages can still require manual setup.

## 2. Full-screen background blur (Android 12+, opt-in)

- A toggle and a maximum blur-radius slider (5–40 dp, default 26 dp) appear in Screen Effects on Android.
- **Default OFF.** At nonzero stages, the radius increases linearly from 1/5 of the chosen maximum (stage 1) to the maximum (stage 5).
- The Android system window uses `FLAG_BLUR_BEHIND` and `WindowManager.LayoutParams#setBlurBehindRadius()` on API 31+. It blurs **the whole screen behind the non-touchable overlay**, not just the inside of the Godot UI.
- The existing click/touch-through behavior, the color tint, masks, stage recovery, pause and stop continue to work.
- Stage 0, screen locked, permission revoked, paused, stopped, protected apps, off-target apps, or unknown foreground → overlay removed or disabled, thereby removing blur.
- GPU constraints, power-saving mode and ROM settings may disable cross-window blur at runtime. The service checks `isCrossWindowBlurEnabled()` and falls back to existing tint/masks. This is **not** a screenshot-based or screen-recording feature.
- Android 11 or earlier cannot activate genuine system cross-window blur, so the blur toggle safely falls back to the existing overlay and the UI indicates the unsupported version.
- **Windows:** real full-desktop blur is not implemented in v0.8.1; the current Windows overlay remains unchanged. An actual per-desktop blur requires separate composition and compatibility research. We intentionally do not claim an acrylic/translucent tint is true Gaussian blur.

## 3. Build/testing

The CI workflow for this branch includes:
- Java selected-app protection tests.
- Pure Java blur radius boundary and monotonicity tests.
- Android native bridge compilation and Godot headless smoke/recovery tests.
- Windows x64 binary packaging and Android ARM64 APK export (build success ≠ device proof).

## 4. Mandatory manual verification before merge

- [ ] On Samsung Android 12+ and AOSP Android 12+, launchable app list appears; labels and package IDs are correct.
- [ ] Search filters by package or app name, select 2 apps, save; both appear in the manual package editor and selection remains after app restart.
- [ ] Protected phone/SMS/Settings/Maps apps are absent from the picker and never blurred even if their package is entered manually.
- [ ] Opt-in blur OFF preserves v0.8.0 behavior.
- [ ] On a device supporting cross-window blur: stage 0 = no blur, stage 1 = weak blur, stage 5 = max configured blur.
- [ ] Test blur while switching between selected apps, protected apps and off-target apps; no stuck overlays, and full effect clears after stop/pause.
- [ ] Check blur with power saver ON and GPU blur disabled; app still usable and the fallback tint/masks work.
- [ ] Check Android 11: no crash, no claim of native blur, same existing overlay.
- [ ] Check touch-through usability (unlock, emergency call, Settings) and overlay removal after unexpected process death.
- [ ] Validate Windows v0.8.0 behavior is unchanged.
- [ ] Test on a spare device first; root CPUFreq remains OFF by default.

### Core platform references
- https://source.android.com/docs/core/display/window-blurs
- https://developer.android.com/reference/android/view/WindowManager.LayoutParams
- https://developer.android.com/training/package-visibility/declaring
