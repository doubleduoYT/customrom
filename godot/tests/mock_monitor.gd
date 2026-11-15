extends "res://main.gd"

# Drive the production Godot recovery logic deterministically in headless CI.
# No operating-system overlay, CPU limiter or physical input is invoked.
var fake_active := false

func _is_active_usage() -> bool:
	return fake_active
