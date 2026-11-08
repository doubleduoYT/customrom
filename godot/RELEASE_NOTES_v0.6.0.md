# Overuse Controller v0.6.0 — Experimental Release

HGSoft / DoubDu · Godot Engine 4.5.2

Overuse Controller는 사용 시간이 늘어나면 화면 오버레이와 (옵션을 켰을 경우) 성능 제한을 단계적으로 적용하는 디지털 웰빙 앱이야.

## 다운로드 / Downloads

- **OveruseController-v0.6.0-Android-arm64.apk** — Android 설치형 APK
- **OveruseController-v0.6.0-Windows-x64.zip** — Windows 64비트 실행 파일 3개
- **OveruseController-v0.6.0-Godot-Source.zip** — Godot 프로젝트, Android/Windows 네이티브 모듈 소스
- **SHA256SUMS.txt** — 파일 무결성 검증용 SHA-256

## 주요 기능 / Features

- Godot 4.5.2 기반 Android + Windows 공통 UI
- 화면 사용 시간 추적, 0~5단계, 15분 일시정지/초기화
- 다른 앱 위에 표시되는 터치/클릭 통과형 화면 오버레이
- Windows: **사용자가 선택해 새로 실행한 프로그램만** CPU Job Object 한도 적용 (단계 0~5: 100/90/80/70/60/50%)
- Android: 루팅 기기 전용 CPUFreq 클럭 상한 옵션 (단계 0~5: 원래 값/95/90/85/80/75%), 기본 비활성화
- Android: CPU 원래 값 백업, 중지/휴식 시 복원 시도, 비정상 종료에 대비한 복구 감시 스크립트

## Windows 설치

ZIP을 풀고 **OveruseController-Win64.exe**를 실행해.

**OveruseOverlay-Win64.exe**, **OveruseCpuLimit-Win64.exe**도 **같은 폴더**에 있어야 화면 효과와 선택 앱 성능 제한이 동작해.

CPU 제한은 기본적으로 꺼져 있어. 사용자가 CPU 제한을 켜고 EXE 파일을 직접 선택한 후 앱에서 실행해야 적용돼. 이미 실행 중인 프로그램이나 Windows 전체 CPU 클럭은 변경하지 않아.

## Android 설치

APK 설치 후 **다른 앱 위에 표시** 권한을 허용하면 화면 효과를 사용할 수 있어.

**일반 비루팅 Android 기기에서는 실제 CPU 클럭 제한 기능이 작동하지 않아.** 루팅 CPU 제한은 별도로 켜야 하며 기기 전체 성능에 영향을 줄 수 있어. 제한 적용/복원은 커널과 루트 환경에 따라 지원되지 않을 수 있어.

## 주의 사항 / Important

**이 버전은 실기기 동작 검증이 완료되지 않은 실험용 릴리즈야.**

- GitHub Actions에서 Android APK·Windows x64·Godot 스크립트 및 네이티브 코드 **빌드 성공**을 확인했지만, 다양한 기기에서 실제 성능 제한/복원 동작을 검증한 것은 아니야.
- Android 루팅 CPU 기능은 **테스트용 기기에서만** 먼저 사용하고, 루트 권한을 허용하기 전에 위험을 확인해 줘. 복원 실패 시 기기 재부팅이나 별도 CPU 관리 도구로 복구가 필요할 수 있어.
- Android APK는 테스트 서명으로 빌드돼서 이전 테스트 APK와 서명이 다르면 바로 업데이트되지 않을 수 있어. 삭제 후 재설치하면 기존 설정이 지워질 수 있어.
- 화면 오버레이는 시스템 보안 화면, 일부 전체 화면 프로그램, 제조사별 절전 정책 등으로 제한될 수 있어.
- 화면을 완전히 차단하거나 다른 앱의 FPS를 직접 바꾸는 기능은 포함하지 않아.

## Build provenance

- Source commit: 4d3775dd36c3d8a901982dbb6c3be70437c93a28
- CI: https://github.com/doubleduoYT/customrom/actions/runs/42451195409
- Source tree: https://github.com/doubleduoYT/customrom/tree/feature/godot-overuse-controller/godot

English summary: Experimental Godot 4.5.2 Android ARM64 APK and Windows x64 release. Screen-time effects work via native overlays. Windows CPU caps apply only to a program explicitly launched through the controller; Android CPU frequency caps are optional, root-only, device-wide and unverified on physical devices. Use with caution.
