# Overuse Controller v0.6.0 — Godot Engine

Godot 4.5.2 기반 Android ARM64 + Windows x64 사용 시간 제어 앱.

## Windows x64
- 모든 프로그램 위에 표시되는 클릭 통과형 화면 효과: OveruseOverlay-Win64.exe
- **사용자가 선택한 프로그램만** Windows Job Object CPU hard cap 적용: OveruseCpuLimit-Win64.exe
- 제한은 기본 비활성화. CPU 제한 켜기 → EXE 선택 → "선택한 앱 CPU 제한 실행" 버튼 클릭할 때만 적용
- 이미 실행 중인 앱이나 다른 앱, Windows 전체 CPU 클럭은 변경하지 않음
- 단계 0~5별 선택 앱 CPU 한도: 100%, 90%, 80%, 70%, 60%, 50%
- Controller 종료 또는 선택 프로그램 종료 시 Job Object가 닫혀 제한 해제. 대상 앱은 종료하지 않음.
- 앱 실행이나 Job Object 등록에 실패하면 해당 상태를 표시
- EXE 3개를 같은 폴더에 압축 해제해야 동작: OveruseController-Win64.exe, OveruseOverlay-Win64.exe, OveruseCpuLimit-Win64.exe

**테스트:** CPU 제한 옵션 켜기 → EXE 선택 → "선택한 앱 CPU 제한 실행" → Overuse Controller에서 3단계 테스트 → 선택 앱만 CPU 70% 상한. 실제 앱 FPS가 70%로 줄어든다는 뜻은 아님.

## Android ARM64
- 일반 APK에서는 시스템 전역 CPU 클럭 변경이 **불가능**. 기본 UI와 화면 효과, 단계 추적은 정상 지원.
- **루팅 기기에서만**, 명시적인 활성화 동의 후 su를 통해 CPUFreq policy별 scaling_max_freq 상한 변경 시도
- 기기 전체 성능에 영향을 주므로 기본 비활성화
- 단계 0~5: 원래 값 / 95% / 90% / 85% / 80% / 75% (CPU 최대 클럭 상한 비율)
- Windows의 CPU 사용률 제어 방식과 다름
- CPUFreq 경로가 없거나 쓰기 불가능한 ROM, SELinux/루트 관리자에 차단된 기기는 지원하지 않으며 오류 상태를 표시
- 화면 잠금/휴식, 15분 일시정지, 보호 종료, 단계 0에서 백업한 원래 scaling_max_freq 복원 시도
- 포그라운드 서비스가 갑자기 종료되더라도 백업을 기반으로 신호가 75초간 없으면 원래 값 복원을 시도하는 감시 프로세스 포함
- 지원 여부와 복원 동작은 기기별로 반드시 실제 테스트 필요
- 기존 APK 설치 자체는 루팅을 수행하지 않음. 루트 관리자 권한을 이미 가진 기기만 동작함.

**테스트:** 보호 시작 → 루팅 CPU 제한 기능 활성화에 명시적으로 동의 → 루트 관리자 승인 → 3단계 테스트 → CPU_LIMIT_85 상태 확인 → 15분 일시정지 → RESTORED 확인.

**주의:** CPUFreq 수정은 기기 전체 CPU 성능에 영향을 줄 수 있어 전화·시스템 UI에도 영향 가능. 처음에는 테스트용 기기에서만 사용하고, 상태가 복원되지 않으면 재부팅하여 정상 클럭을 확인해.

## 공통
- 기본 10분 사용 후 1단계, 이후 2분마다 상승 (최대 5단계)
- 휴식 1분마다 1단계 회복, 15분 일시정지 및 중지, 강도·단계별 시간 사용자 설정
- 터치·클릭 통과형 화면 오버레이, 1·3·5단계 테스트

## CI
GitHub Actions → Build Godot Overuse Android and Windows → Run workflow. 아티팩트:
- OveruseController-Android-APK (디버그 APK)
- OveruseController-Win64 (EXE 3개 압축)
- OveruseController-Godot-Source (Godot 프로젝트 및 네이티브 코드)

Android 디버그 APK는 빌드마다 서명이 달라 업데이트 설치가 막힐 수 있고, 기존 앱을 제거하면 저장된 설정도 초기화돼. 빌드 통과와 실기기 동작 테스트는 서로 달라.
