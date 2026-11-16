extends SceneTree

const Monitor = preload("res://tests/mock_monitor.gd")
var checks := 0
var failures := 0

func _check(ok: bool, description: String) -> void:
	checks += 1
	if ok:
		print("PASS: " + description)
	else:
		failures += 1
		push_error("FAIL: " + description)

func _init() -> void:
	var monitor = Monitor.new()
	monitor.picker_cached_lines = PackedStringArray([
		"com.example.game\tGame Alpha",
		"com.example.photo\tPhoto Editor",
		"com.example.reader\tReader"
	])
	monitor.picker_checked_packages["com.example.game"] = true
	var app_list = ItemList.new()
	app_list.select_mode = ItemList.SELECT_MULTI
	monitor._update_picker_results(app_list, "")
	_check(app_list.item_count == 3, "initial installed-app list has three entries")
	_check(app_list.is_selected(0), "saved app is checked after loading")
	monitor._update_picker_results(app_list, "Photo")
	_check(app_list.item_count == 1, "search by display label")
	_check(str(app_list.get_item_metadata(0)) == "com.example.photo", "package metadata preserved in search results")
	monitor.picker_checked_packages["com.example.photo"] = true
	monitor._update_picker_results(app_list, "game")
	_check(app_list.item_count == 1 and app_list.is_selected(0), "saved checkbox persists across search")
	monitor._update_picker_results(app_list, "")
	_check(app_list.is_selected(0) and app_list.is_selected(1), "all saved selections restored")
	monitor.picker_checked_packages.erase("com.example.game")
	var result = monitor._picker_merged_targets("com.example.game,com.manual.hidden")
	_check(result == "com.manual.hidden,com.example.photo", "unchecked launchable app removed and manual target retained")
	monitor.picker_checked_packages.clear()
	_check(monitor._picker_merged_targets("com.example.photo,com.manual.hidden") == "com.manual.hidden", "uncheck all preserves non-launchable manual target")
	monitor._update_picker_results(app_list, "NO_MATCH")
	_check(app_list.item_count == 0, "nonmatching search returns empty list")
	app_list.free()
	monitor.free()
	print("App picker checks: %d, failures: %d" % [checks, failures])
	quit(0 if failures == 0 else 1)
