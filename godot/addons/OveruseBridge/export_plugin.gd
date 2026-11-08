@tool
extends EditorPlugin

var _export_bridge: EditorExportPlugin

func _enter_tree() -> void:
	_export_bridge = BridgeExport.new()
	add_export_plugin(_export_bridge)

func _exit_tree() -> void:
	if _export_bridge != null:
		remove_export_plugin(_export_bridge)
		_export_bridge = null

class BridgeExport extends EditorExportPlugin:
	func _get_name() -> String:
		return "OveruseBridge"

	func _supports_platform(platform: EditorExportPlatform) -> bool:
		return platform is EditorExportPlatformAndroid

	func _get_android_libraries(_platform: EditorExportPlatform, debug: bool) -> PackedStringArray:
		if debug:
			return PackedStringArray(["OveruseBridge/bin/debug/OveruseBridge-debug.aar"])
		return PackedStringArray(["OveruseBridge/bin/release/OveruseBridge-release.aar"])

	func _get_android_dependencies(_platform: EditorExportPlatform, _debug: bool) -> PackedStringArray:
		return PackedStringArray()
