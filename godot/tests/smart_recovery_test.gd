extends SceneTree

const MockMonitor = preload("res://tests/mock_monitor.gd")
var checks := 0
var failures := 0

func _check(condition: bool, label: String) -> void:
	checks += 1
	if condition:
		print("PASS: " + label)
	else:
		failures += 1
		push_error("FAIL: " + label)

func _make_monitor(smart: bool, recovery_minutes: int = 1):
	var monitor = MockMonitor.new()
	monitor.running = true
	monitor.stage = 5
	monitor.settings["recovery"] = recovery_minutes
	monitor.settings["smart_recovery"] = smart
	monitor.stage_at_ms = 600000.0
	return monitor

func _advance(monitor, seconds: int) -> void:
	# Production _process intentionally caps a single tick at 10 seconds.
	for i in range(seconds / 10):
		monitor._process(10.0)

func _init() -> void:
	var fixed = _make_monitor(false)
	_advance(fixed, 180)
	_check(fixed.stage == 2, "fixed recovery: stage 5 -> 2 after three rest minutes")
	fixed.free()

	var smart = _make_monitor(true)
	_advance(smart, 180)
	_check(smart.stage == 1, "Smart Recovery: stage 5 -> 1 after three rest minutes")
	_advance(smart, 60)
	_check(smart.stage == 0, "Smart Recovery: stage 0 after four rest minutes")
	_check(smart.active_ms == 0.0, "stage 0 restores the fresh grace period")
	smart.free()

	var single_bonus = _make_monitor(true, 10)
	_advance(single_bonus, 360)
	_check(single_bonus.stage == 4, "Smart Recovery grants only one bonus per uninterrupted rest")
	single_bonus.free()

	var interrupted = _make_monitor(true, 10)
	_advance(interrupted, 120)
	interrupted.fake_active = true
	_advance(interrupted, 10)
	interrupted.fake_active = false
	_advance(interrupted, 60)
	_check(interrupted.stage == 5, "new active input resets the pending three-minute bonus")
	_advance(interrupted, 120)
	_check(interrupted.stage == 4, "new continuous three-minute rest grants one bonus")
	interrupted.free()

	var paused = _make_monitor(true, 10)
	_advance(paused, 120)
	paused._set_pause()
	_check(paused.rest_streak_ms == 0.0 and paused.recovery_ms == 0.0 and not paused.rest_bonus_awarded, "manual pause clears rest and bonus state")
	var before_pause_total = paused.today_rest_ms
	_advance(paused, 120)
	_check(paused.today_rest_ms == before_pause_total, "paused time is excluded from rest metrics")
	paused._set_pause()
	_advance(paused, 60)
	_check(paused.stage == 5, "resuming does not inherit pre-pause rest time")
	_advance(paused, 120)
	_check(paused.stage == 4, "three full minutes after resuming earns the single bonus")
	paused.free()

	var helper_missing = _make_monitor(true)
	_check(helper_missing._windows_active_input(), "missing Windows idle helper cannot earn false rest")
	helper_missing.free()

	print("Recovery policy checks: %d, failures: %d" % [checks, failures])
	quit(0 if failures == 0 else 1)
