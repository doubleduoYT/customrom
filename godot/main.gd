extends Control

# Overuse Controller / HGSoft
# Godot 4.5.2 cross-platform core. Android uses a separate native bridge
# for true screen-on monitoring and touch-through system overlays.

const CONFIG_FILE = "user://overuse.cfg"
const BG = Color("#0a101d")
const PANEL = Color("#162236")
const PANEL_2 = Color("#202f47")
const WHITE = Color("#eef5ff")
const MUTED = Color("#9eb1ca")
const ACCENT = Color("#76baff")
const WARNING = Color("#ffb36d")
const MAX_STAGE = 5

var settings = {
	"grace": 10, "interval": 2, "recovery": 1,
	"strength": 65, "color": true, "masks": true
}
var bridge: Object = null
var pending_android_start := false
var win_overlay_pid := -1
var win_overlay_active := false
var win_overlay_config := ""
var win_overlay_idle := ""
var running := false
var stage := 0
var active_ms := 0.0
var recovery_ms := 0.0
var stage_at_ms := 600000.0
var pause_until := 0.0
var save_counter := 0.0
var visual_counter := 0.0

var status_label: Label
var stage_label: Label
var active_label: Label
var next_label: Label
var info_label: Label
var progress_bar: ProgressBar
var toggle_button: Button
var pause_button: Button
var overlay: ColorRect
var stripes: Array[ColorRect] = []

func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	get_viewport().get_window().min_size = Vector2i(380, 560) if OS.get_name() == "Windows" else Vector2i.ZERO
	_load_state()
	if OS.get_name() == "Android" and Engine.has_singleton("OveruseBridge"):
		bridge = Engine.get_singleton("OveruseBridge")
	_build_ui()
	if OS.get_name() == "Windows":
		_launch_windows_overlay()
	if bridge != null:
		print("[OveruseController] Android native bridge loaded: ", bridge.getBridgeVersion())
		_settings_changed()
		_read_native_state()
	elif OS.get_name() == "Android":
		push_warning("[OveruseController] Android native bridge was NOT registered. System overlay cannot run.")
	_refresh_view()
	set_process(true)

func _process(delta: float) -> void:
	if bridge != null:
		# When returning from Android's overlay permission settings, start the monitor.
		if pending_android_start and bool(bridge.hasOverlayPermission()):
			pending_android_start = false
			bridge.startMonitor()
		# Java foreground service stays active while the Godot app is in the background.
		save_counter += delta
		if save_counter >= 0.5:
			save_counter = 0.0
			_read_native_state()
			_refresh_view()
	else:
		if running and Time.get_unix_time_from_system() >= pause_until:
			var elapsed_ms = minf(delta * 1000.0, 10000.0)
			if DisplayServer.window_is_focused() or _windows_active_input():
				active_ms += elapsed_ms
				recovery_ms = 0.0
				if stage == 0 and active_ms >= float(settings["grace"]) * 60000.0:
					_change_stage(1)
				elif stage > 0 and stage < MAX_STAGE and active_ms >= stage_at_ms:
					_change_stage(stage + 1)
			else:
				recovery_ms += elapsed_ms
				var recovery_interval = float(settings["recovery"]) * 60000.0
				if stage > 0 and recovery_ms >= recovery_interval:
					recovery_ms -= recovery_interval
					_change_stage(stage - 1)
					if stage == 0:
						active_ms = 0.0
						stage_at_ms = float(settings["grace"]) * 60000.0
					else:
						stage_at_ms = active_ms + float(settings["interval"]) * 60000.0
		save_counter += delta
		if save_counter >= 0.5:
			save_counter = 0.0
			if OS.get_name() == "Windows":
				_write_windows_overlay_config()
			_refresh_view()
			_save_state()
	visual_counter += delta
	if visual_counter >= 2.0:
		visual_counter = 0.0
		_update_visuals()

func _read_native_state() -> void:
	if bridge == null:
		return
	running = bool(bridge.isRunning())
	stage = int(bridge.getStage())
	active_ms = float(bridge.getActiveMs())
	pause_until = float(bridge.getPausedUntil()) / 1000.0
	stage_at_ms = float(bridge.getNextStageMs())

func _launch_windows_overlay() -> void:
	var helper_path: String = OS.get_executable_path().get_base_dir().path_join("OveruseOverlay-Win64.exe")
	if not FileAccess.file_exists(helper_path):
		push_warning("[OveruseController] Windows overlay companion missing: " + helper_path)
		return
	win_overlay_config = ProjectSettings.globalize_path("user://desktop_overlay.txt")
	win_overlay_idle = ProjectSettings.globalize_path("user://desktop_idle.txt")
	_write_windows_overlay_config()
	win_overlay_pid = OS.create_process(helper_path,
		PackedStringArray([win_overlay_config, win_overlay_idle, str(OS.get_process_id())]), false)
	win_overlay_active = win_overlay_pid > 0
	if not win_overlay_active:
		push_warning("[OveruseController] Could not launch the desktop overlay helper.")

func _write_windows_overlay_config() -> void:
	if win_overlay_config.is_empty():
		return
	var file = FileAccess.open(win_overlay_config, FileAccess.WRITE)
	if file == null:
		return
	var paused: int = 1 if running and pause_until > Time.get_unix_time_from_system() else 0
	file.store_string("%d %d %d %d %d %d\n" % [
		int(running), stage, int(settings["strength"]),
		int(settings["color"]), int(settings["masks"]), paused])
	file.close()

func _windows_active_input() -> bool:
	if not win_overlay_active:
		return false
	if not OS.is_process_running(win_overlay_pid):
		win_overlay_active = false
		return false
	if not FileAccess.file_exists(win_overlay_idle):
		return false
	var file = FileAccess.open(win_overlay_idle, FileAccess.READ)
	if file == null:
		return false
	var idle_ms: int = int(file.get_as_text().strip_edges())
	file.close()
	return idle_ms < 5 * 60 * 1000

func _exit_tree() -> void:
	if not win_overlay_config.is_empty():
		var file = FileAccess.open(win_overlay_config, FileAccess.WRITE)
		if file != null:
			file.store_string("0 0 0 0 0 0\n")
			file.close()

func _set_running() -> void:
	if OS.get_name() == "Android" and bridge == null:
		info_label.text = "오류: Android 네이티브 브리지가 로드되지 않았어. 앱 바깥 효과를 사용할 수 없어."
		return
	if bridge != null:
		if running:
			bridge.stopMonitor()
		elif bool(bridge.hasOverlayPermission()):
			bridge.startMonitor()
		else:
			pending_android_start = true
			bridge.requestOverlayPermission()
			info_label.text = "Android 설정에서 '다른 앱 위에 표시'를 허용해 줘. 돌아오면 자동으로 시작돼."
			return
		_read_native_state()
	else:
		running = not running
		if not running:
			stage = 0
			active_ms = 0.0
			recovery_ms = 0.0
			pause_until = 0.0
			stage_at_ms = float(settings["grace"]) * 60000.0
	_save_state()
	_refresh_view()

func _set_pause() -> void:
	if not running:
		return
	if bridge != null:
		if pause_until > Time.get_unix_time_from_system():
			bridge.resumeMonitor()
		else:
			bridge.pauseMonitor()
		_read_native_state()
	else:
		if pause_until > Time.get_unix_time_from_system():
			pause_until = 0.0
		else:
			pause_until = Time.get_unix_time_from_system() + 15.0 * 60.0
	_save_state()
	_refresh_view()

func _change_stage(new_stage: int) -> void:
	stage = clampi(new_stage, 0, MAX_STAGE)
	if stage == 0:
		stage_at_ms = float(settings["grace"]) * 60000.0
	else:
		stage_at_ms = active_ms + float(settings["interval"]) * 60000.0
	_update_visuals()

func _preview(level: int) -> void:
	if OS.get_name() == "Android" and bridge == null:
		info_label.text = "오류: Android 네이티브 브리지 연결 실패. 시스템 오버레이 테스트를 실행할 수 없어."
		return
	if bridge != null:
		if not bool(bridge.hasOverlayPermission()):
			bridge.requestOverlayPermission()
			return
		bridge.previewStage(level)
		_read_native_state()
		info_label.text = "네이티브 미리보기 요청 완료. 홈 버튼을 눌러 앱 바깥에서도 표시되는지 확인해 줘."
	else:
		running = true
		_change_stage(level)
	_save_state()
	_refresh_view()

func _reset() -> void:
	if bridge != null:
		bridge.resetMonitor()
		_read_native_state()
	else:
		stage = 0
		active_ms = 0.0
		recovery_ms = 0.0
		stage_at_ms = float(settings["grace"]) * 60000.0
	_update_visuals()
	_save_state()
	_refresh_view()

func _load_state() -> void:
	var cfg = ConfigFile.new()
	if cfg.load(CONFIG_FILE) != OK:
		return
	for key in settings:
		settings[key] = cfg.get_value("settings", key, settings[key])
	running = bool(cfg.get_value("session", "running", false))
	stage = clampi(int(cfg.get_value("session", "stage", 0)), 0, MAX_STAGE)
	active_ms = float(cfg.get_value("session", "active_ms", 0.0))
	stage_at_ms = float(cfg.get_value("session", "stage_at_ms", settings["grace"] * 60000.0))
	pause_until = float(cfg.get_value("session", "pause_until", 0.0))

func _save_state() -> void:
	var cfg = ConfigFile.new()
	for key in settings:
		cfg.set_value("settings", key, settings[key])
	cfg.set_value("session", "running", running)
	cfg.set_value("session", "stage", stage)
	cfg.set_value("session", "active_ms", active_ms)
	cfg.set_value("session", "stage_at_ms", stage_at_ms)
	cfg.set_value("session", "pause_until", pause_until)
	cfg.save(CONFIG_FILE)

func _settings_changed() -> void:
	_save_state()
	if bridge != null:
		bridge.setConfig(int(settings["grace"]), int(settings["interval"]),
			int(settings["recovery"]), int(settings["strength"]),
			bool(settings["color"]), bool(settings["masks"]))
	elif stage == 0:
		stage_at_ms = float(settings["grace"]) * 60000.0
	_update_visuals()
	_refresh_view()

func _refresh_view() -> void:
	if not is_instance_valid(status_label):
		return
	var paused = running and pause_until > Time.get_unix_time_from_system()
	stage_label.text = "%d / %d" % [stage, MAX_STAGE]
	stage_label.add_theme_color_override("font_color", WARNING if stage >= 4 else WHITE)
	status_label.text = "●  15분 일시정지 중" if paused else ("●  보호 실행 중" if running else "●  보호 중지")
	status_label.add_theme_color_override("font_color", WARNING if paused else (ACCENT if running else MUTED))
	var minutes = int(active_ms / 60000.0)
	active_label.text = "누적 사용  %d시간 %02d분" % [int(minutes / 60), minutes % 60]
	var until_ms = (float(settings["grace"]) * 60000.0 if stage == 0 else stage_at_ms) - active_ms
	if stage >= MAX_STAGE:
		next_label.text = "최고 단계에 도달했어"
	elif paused:
		next_label.text = "일시정지 중에는 사용 시간이 늘지 않아"
	else:
		next_label.text = "다음 단계까지 약 %d분" % maxi(0, int(ceilf(until_ms / 60000.0)))
	progress_bar.value = stage
	toggle_button.text = "보호 종료" if running else "보호 시작"
	pause_button.disabled = not running
	pause_button.text = "보호 계속하기" if paused else "15분 일시정지"
	if bridge != null:
		var native_state: String = str(bridge.getOverlayStatus())
		if not bool(bridge.hasOverlayPermission()):
			info_label.text = "권한 없음: '보호 시작'을 누르고 다른 앱 위에 표시를 허용해 줘."
		else:
			match native_state:
				"VISIBLE_SYSTEM_OVERLAY":
					info_label.text = "✓ 시스템 오버레이 활성화됨 · 홈 화면과 다른 앱에서도 표시돼."
				"WAITING_STAGE":
					info_label.text = "✓ 서비스 실행 중 · 3단계 테스트를 누르고 홈으로 이동해 봐."
				"PAUSED":
					info_label.text = "일시정지 중 · 화면 효과를 숨겼어."
				"SCREEN_LOCKED":
					info_label.text = "화면 잠금 상태 · 잠금 해제 후 자동으로 표시돼."
				_:
					info_label.text = "Android 서비스 상태: " + native_state
	elif OS.get_name() == "Android":
		info_label.text = "⚠ 네이티브 브리지 인식 실패 · 앱 밖 효과가 작동하지 않아."
	elif win_overlay_active:
		info_label.text = "✓ Windows 데스크톱 오버레이 실행 중 · 다른 프로그램에도 적용돼."
	else:
		info_label.text = "Windows 데스크톱 도우미 없음 · 이 창 안에서만 효과가 보여."
	_update_visuals()

func _update_visuals() -> void:
	if not is_instance_valid(overlay):
		return
	# Android must use the real OS overlay. Do not disguise a missing native
	# plugin by showing an app-only tint over the Godot window.
	var visible_fx = OS.get_name() != "Android" and not win_overlay_active and running and stage > 0 and pause_until <= Time.get_unix_time_from_system()
	var strength = float(settings["strength"]) / 100.0
	overlay.visible = visible_fx and bool(settings["color"]) and strength > 0.0
	if overlay.visible:
		var palette = [Color("#405e85"), Color("#6b4b80"), Color("#387470"), Color("#725a3e")]
		var tone: Color = palette[randi() % palette.size()]
		tone.a = minf(0.32, (0.025 + float(stage) * 0.04) * strength)
		overlay.color = tone
	for i in stripes.size():
		var block = stripes[i]
		block.visible = visible_fx and bool(settings["masks"]) and strength > 0.0 and i < stage
		if block.visible:
			block.position = Vector2(randf_range(0.0, 0.65) * get_viewport_rect().size.x, 0)
			block.size = Vector2(26.0 + i * 19.0, get_viewport_rect().size.y)
			block.color = Color(0.1, 0.17, 0.28, 0.025 * strength)

func _build_ui() -> void:
	var backdrop = ColorRect.new()
	backdrop.color = BG
	backdrop.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	backdrop.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(backdrop)
	var scroll = ScrollContainer.new()
	scroll.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	add_child(scroll)
	var margin = MarginContainer.new()
	margin.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	for side in ["left", "right"]:
		margin.add_theme_constant_override("margin_" + side, 20)
	margin.add_theme_constant_override("margin_top", 27)
	margin.add_theme_constant_override("margin_bottom", 35)
	scroll.add_child(margin)
	var content = VBoxContainer.new()
	content.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	content.add_theme_constant_override("separation", 12)
	margin.add_child(content)

	content.add_child(_label("H G S O F T   /   D I G I T A L   W E L L B E I N G", 12, ACCENT, true))
	content.add_child(_label("Overuse Controller", 32, WHITE, true))
	content.add_child(_label("사용 습관을 부드럽게 조절하는 스마트 제한 시스템", 14, MUTED))

	var dashboard = _card(content)
	dashboard.add_child(status_label_placeholder())
	stage_label = _label("0 / 5", 52, WHITE, true)
	dashboard.add_child(stage_label)
	dashboard.add_child(_label("CURRENT OVERUSE STAGE", 12, MUTED))
	progress_bar = ProgressBar.new()
	progress_bar.custom_minimum_size = Vector2(0, 13)
	progress_bar.max_value = 5
	progress_bar.value = 0
	progress_bar.show_percentage = false
	dashboard.add_child(progress_bar)
	active_label = _label("누적 사용 0시간 00분", 19, WHITE, true)
	dashboard.add_child(active_label)
	next_label = _label("다음 단계까지 약 10분", 14, MUTED)
	dashboard.add_child(next_label)

	toggle_button = _button("보호 시작", true)
	content.add_child(toggle_button)
	toggle_button.pressed.connect(_set_running)
	pause_button = _button("15분 일시정지", false)
	content.add_child(pause_button)
	pause_button.pressed.connect(_set_pause)

	content.add_child(_label("단계 설정", 22, WHITE, true))
	content.add_child(_label("자신의 사용 습관에 맞춰 시간을 조정해", 13, MUTED))
	_add_slider(content, "첫 단계까지", "grace", 1, 30, "분")
	_add_slider(content, "단계 상승 간격", "interval", 1, 15, "분")
	_add_slider(content, "단계 회복 간격", "recovery", 1, 10, "분")

	content.add_child(_label("화면 효과", 22, WHITE, true))
	_add_toggle(content, "부드러운 색상 오버레이", "color")
	_add_toggle(content, "화면 가림 마스크", "masks")
	_add_slider(content, "효과 강도", "strength", 0, 100, "%")

	content.add_child(_label("효과 테스트", 22, WHITE, true))
	content.add_child(_label("Android에서는 단계 버튼을 누른 후 홈 화면으로 이동해서 확인해.", 13, MUTED))
	var buttons = HBoxContainer.new()
	buttons.add_theme_constant_override("separation", 8)
	content.add_child(buttons)
	for level in [1, 3, 5]:
		var btn = _button("%d단계" % level, false)
		btn.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		buttons.add_child(btn)
		btn.pressed.connect(_preview.bind(level))
	var reset = _button("단계 및 사용 시간 초기화", false)
	content.add_child(reset)
	reset.pressed.connect(_reset)

	var info = _card(content)
	info_label = _label("플랫폼 기능 확인 중…", 13, MUTED)
	info.add_child(info_label)
	info.add_child(_label("전화나 다른 앱의 터치는 차단하지 않아. 언제든 일시정지·종료할 수 있어.", 12, MUTED))
	content.add_child(_label("Overuse Controller v0.4.2 · Godot Engine 4.5.2", 12, MUTED))

	overlay = ColorRect.new()
	overlay.mouse_filter = Control.MOUSE_FILTER_IGNORE
	overlay.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	overlay.visible = false
	add_child(overlay)
	for i in MAX_STAGE:
		var stripe = ColorRect.new()
		stripe.mouse_filter = Control.MOUSE_FILTER_IGNORE
		stripe.visible = false
		add_child(stripe)
		stripes.append(stripe)

func status_label_placeholder() -> Label:
	status_label = _label("●  보호 중지", 15, MUTED, true)
	return status_label

func _label(value: String, font_size: int, font_color: Color, bold := false) -> Label:
	var label = Label.new()
	label.text = value
	label.add_theme_color_override("font_color", font_color)
	label.add_theme_font_size_override("font_size", font_size)
	label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	if bold:
		label.add_theme_font_override("font", ThemeDB.fallback_font)
	return label

func _style(color: Color, radius: int = 18) -> StyleBoxFlat:
	var box = StyleBoxFlat.new()
	box.bg_color = color
	box.set_corner_radius_all(radius)
	box.set_content_margin_all(17)
	return box

func _card(parent: VBoxContainer) -> VBoxContainer:
	var panel = PanelContainer.new()
	panel.add_theme_stylebox_override("panel", _style(PANEL))
	parent.add_child(panel)
	var layout = VBoxContainer.new()
	layout.add_theme_constant_override("separation", 12)
	panel.add_child(layout)
	return layout

func _button(caption: String, primary: bool) -> Button:
	var btn = Button.new()
	btn.text = caption
	btn.custom_minimum_size = Vector2(0, 54)
	btn.add_theme_font_size_override("font_size", 15)
	btn.add_theme_color_override("font_color", BG if primary else WHITE)
	btn.add_theme_color_override("font_hover_color", BG if primary else WHITE)
	btn.add_theme_color_override("font_pressed_color", BG if primary else WHITE)
	btn.add_theme_stylebox_override("normal", _style(ACCENT if primary else PANEL_2, 12))
	btn.add_theme_stylebox_override("hover", _style(Color("#a3d5ff") if primary else Color("#304868"), 12))
	btn.add_theme_stylebox_override("pressed", _style(Color("#559ee0") if primary else Color("#18273b"), 12))
	return btn

func _add_slider(parent: VBoxContainer, caption: String, key: String, min_value: int, max_value: int, suffix: String) -> void:
	var card = _card(parent)
	var lbl = _label("%s  ·  %d%s" % [caption, int(settings[key]), suffix], 15, WHITE, true)
	card.add_child(lbl)
	var slider = HSlider.new()
	slider.min_value = min_value
	slider.max_value = max_value
	slider.step = 1
	slider.value = int(settings[key])
	slider.custom_minimum_size = Vector2(0, 28)
	card.add_child(slider)
	slider.value_changed.connect(func(value: float) -> void:
		settings[key] = int(value)
		lbl.text = "%s  ·  %d%s" % [caption, int(value), suffix]
		_settings_changed()
	)

func _add_toggle(parent: VBoxContainer, caption: String, key: String) -> void:
	var card = _card(parent)
	var toggle = CheckButton.new()
	toggle.text = caption
	toggle.button_pressed = bool(settings[key])
	toggle.add_theme_font_size_override("font_size", 15)
	card.add_child(toggle)
	toggle.toggled.connect(func(on: bool) -> void:
		settings[key] = on
		_settings_changed()
	)
