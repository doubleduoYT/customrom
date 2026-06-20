param(
    [string]$ApkPath = ".\app\build\outputs\apk\debug\app-debug.apk",
    [string]$OutputZip = ".\overuse-controller-patch.zip"
)

$patch = ".\patch"
Remove-Item -Recurse -Force $patch -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$patch\META-INF\com\google\android" | Out-Null
New-Item -ItemType Directory -Force "$patch\system\priv-app\OveruseController" | Out-Null
Copy-Item $ApkPath "$patch\system\priv-app\OveruseController\OveruseController.apk"
@'
ui_print("Installing Overuse Controller...");
mount("ext4", "EMMC", "/dev/block/bootdevice/by-name/system", "/system");
package_extract_dir("system", "/system");
set_perm(0, 0, 0755, "/system/priv-app/OveruseController");
set_perm(0, 0, 0644, "/system/priv-app/OveruseController/OveruseController.apk");
unmount("/system");
ui_print("Done. Reboot system.");
'@ | Set-Content -NoNewline "$patch\META-INF\com\google\android\updater-script"
if (Test-Path $OutputZip) { Remove-Item $OutputZip -Force }
Compress-Archive -Path "$patch\*" -DestinationPath $OutputZip -Force
Write-Host "Created: $OutputZip"
