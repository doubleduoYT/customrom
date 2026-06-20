#!/usr/bin/env bash
set -euo pipefail
APK_PATH="${1:-app/build/outputs/apk/debug/app-debug.apk}"
OUTPUT_ZIP="${2:-overuse-controller-patch.zip}"
PATCH_DIR="patch"

if [ ! -f "$APK_PATH" ]; then
  echo "Missing APK: $APK_PATH" >&2
  exit 1
fi

rm -rf "$PATCH_DIR" "$OUTPUT_ZIP"
mkdir -p "$PATCH_DIR/META-INF/com/google/android"
mkdir -p "$PATCH_DIR/system/priv-app/OveruseController"
cp "$APK_PATH" "$PATCH_DIR/system/priv-app/OveruseController/OveruseController.apk"
cat > "$PATCH_DIR/META-INF/com/google/android/updater-script" <<'EOF'
ui_print("Installing Overuse Controller...");
mount("ext4", "EMMC", "/dev/block/bootdevice/by-name/system", "/system");
package_extract_dir("system", "/system");
set_perm(0, 0, 0755, "/system/priv-app/OveruseController");
set_perm(0, 0, 0644, "/system/priv-app/OveruseController/OveruseController.apk");
unmount("/system");
ui_print("Done. Reboot system.");
EOF
(cd "$PATCH_DIR" && zip -r "../$OUTPUT_ZIP" META-INF system)
echo "Created: $OUTPUT_ZIP"
