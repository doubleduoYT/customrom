# Overuse Controller v0.7.0 — Smart Recovery preview

This is an **experimental feature branch**, not the stable release. The existing v0.6.0 branch and `main` are unchanged.

## What changed

- **Fixed recovery (default):** every configured recovery interval spent resting lowers the current stage by one. This remains the default behavior for backwards compatibility.
- **Smart Recovery (opt-in):** after **3 continuous minutes** of rest, the stage drops by **one additional level once per rest period**. Switching back to active use resets the three-minute streak and the one-time bonus.
- The stage stays between 0 and 5. Reaching stage 0 clears the session's active-use counter, as before.
- The UI now shows **today's active and rest minutes**, counted locally while the controller is running and not paused. Counters reset at local calendar-day change.
- The Android foreground service saves the partial recovery interval, rest streak, one-time bonus flag and daily counters so brief service restarts do not reset the policy. Windows/Godot saves the same local state.
- Manual pause/resume **clears the pending continuous-rest streak and partial recovery interval** on both platforms; paused time earns no recovery credit.
- The existing manual 15-minute pause and Stop controls remain available. Root CPUFreq and Windows per-program CPU caps remain **off by default** and are not required for Smart Recovery.

### Examples (recovery interval: 1 minute; start at stage 5)

| Uninterrupted rest | Fixed recovery | Smart Recovery |
| --- | ---: | ---: |
| 0 minutes | 5 | 5 |
| 1 minute | 4 | 4 |
| 2 minutes | 3 | 3 |
| 3 minutes | 2 | 1 (extra step) |
| 4 minutes | 1 | 0 |
| 5 minutes | 0 | 0 |

Smart Recovery is deliberately a **simple testable rule**, not machine learning and not yet personalized. This mode may or may not reduce overuse; the comparison has not been tested with users.

## Behavioral and safety limitations

- Android counts screen interactive + unlocked as active use, otherwise as rest; this is **not app-level detection**. Windows now uses the **desktop-wide last-input timer from its overlay helper**, not controller-window focus. After **30 seconds without keyboard/mouse input**, time starts accumulating as rest. Thus the 3-minute Smart Recovery bonus normally becomes available after about 3 minutes 30 seconds of continuous input inactivity. The counters are estimates, not system-wide Digital Wellbeing figures.
- **Only monitoring-active, non-paused periods** are added to today's counters. The program does not identify or read the content of messages, videos, or browsing.
- **Windows limitation:** watching a movie, reading a page, or playing a game using a controller without mouse/keyboard input may be incorrectly counted as rest. If the idle-helper process or sample cannot be read, the controller conservatively classifies the interval as active rather than granting an undeserved rest bonus.
- The Smart Recovery toggle affects when stages decrease, **not** CPU performance permissions. Existing performance controls remain opt-in and must be tested on a spare device.
- Emergency phone calls, maps, and essential apps are **not yet automatically exempted** from visual overlays. Before human-subject testing or broad distribution, add exception handling or disable overlays for those cases.
- Build success does not prove functional correctness. Do not claim effectiveness without user-consented testing.

## Automated policy checks

The GitHub Actions workflow runs the Godot headless script `godot/tests/smart_recovery_test.gd` after the regular smoke test. It covers the fixed 5→2 and Smart 5→1→0 paths, one-time bonuses, interrupted usage, pause/resume reset and a missing Windows helper. Android bridge compilation is separately checked, but **the automated simulation does not substitute for device-level behavior tests**.

## Manual regression tests before tagging a release

- [ ] Android (non-root): use normal 10-minute grace, reach stage 1, and check the system overlay is visible outside Godot.
- [ ] Android (non-root): default Smart Recovery OFF; with stage 5 and one-minute recovery, lock for 3 minutes → stage 2.
- [ ] Android (non-root): Smart Recovery ON; lock for 3 minutes → stage 1. A fourth minute → stage 0.
- [ ] Interrupt rest at 2 minutes by unlocking, then lock again; the 3-minute bonus must **not** trigger early.
- [ ] Leave the screen locked for another 3 minutes; bonus must trigger at most once during the same rest streak.
- [ ] Restart the Android service mid-rest; saved streak/bonus must not give a duplicate bonus.
- [ ] Windows: verify the same step changes after ~30 seconds input-idle grace plus configured rest time, even while the controller window is focused. Confirm keyboard/mouse activity interrupts the streak and both EXE helpers still work.
- [ ] Windows: verify passive video watching is not accidentally interpreted as a successful overuse reduction; document the known input-only measurement limitation.
- [ ] Pause monitoring after 2 minutes of continuous rest, resume, and confirm a **new full 3-minute streak** is required for the bonus on both Android and Windows.
- [ ] Pause monitoring for 15 minutes, resume, and verify no elapsed paused time was counted as usage or rest.
- [ ] Stop monitoring, verify overlays are removed and optional CPU limits are restored; never test Android root performance controls on a primary phone.
- [ ] Cross midnight while monitoring and verify the local daily statistics reset.
- [ ] Compare recorded daily use/rest counts against observed sessions and document measurement error.

## Upcoming milestone checklist

1. **v0.6.1 safety** — confirm overlays/CPU restoration and helper termination on real devices; do not merge without regression test evidence.
2. **v0.7.0 Smart Recovery** — this initial opt-in model + daily statistics and controlled A/B evaluation versus fixed recovery.
3. **v0.8.0 selective intervention** — Android foreground app tracking (permission-gated), protected apps / emergency bypass, transparent selection.
4. **v0.9.0 adaptive policy** — user-controlled personalization with local-only data, effectiveness versus user discomfort measurements.
5. **v1.0.0** — pre-registered consent-based evaluation, stability tests, privacy documentation and supported-device matrix.

Source design: [original Godot v0.6.0 branch](https://github.com/doubleduoYT/customrom/tree/feature/godot-overuse-controller).