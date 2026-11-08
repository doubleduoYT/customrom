# Overuse Controller v0.4.2 — Godot Engine port

Godot 4.5.2 / GDScript로 UI·설정·시간 기반 0~5단계 로직을 다시 작성한 크로스 플랫폼 포팅판.

## 플랫폼
- Windows x64: Godot 대시보드 + 동봉된 OveruseOverlay-Win64.exe가 제공하는 데스크톱 전체 클릭 통과형 오버레이. 실행 중 Windows의 마지막 입력 시간을 기준으로 단계 계산.
- Android ARM64: Godot 기반 인터페이스. 기본 포함된 OveruseBridge Android v2 플러그인으로, Java 포그라운드 서비스가 화면 사용 시간을 기록하고 다른 앱 위에 터치가 통과하는 오버레이를 표시함. 플러그인이 없는 빌드에서는 앱 내부 시뮬레이션 모드만 작동함.

## 기능
- 기본 10분 이후 1단계, 이후 2분마다 상승 (최대 5단계)
- 기본 1분 사용 중단마다 1단계 회복
- 15분 일시정지, 보호 종료, 초기화, 1·3·5단계 미리보기
- 설정 자동 저장 (단계 시간, 오버레이 강도, 색상/마스크)
- 과도하게 깜빡이는 조명 효과 없음, 각 단계 효과는 터치 통과형
- Android 네이티브 브리지와 Godot UI의 계층 분리

## 빌드
GitHub Actions의 Build Godot Overuse Android and Windows를 실행한 뒤 Artifacts에서:
- OveruseController-Android-APK
- OveruseController-Win64

Godot 프로젝트는 godot/ 폴더 전체이며 Editor에서 project.godot를 열면 수정할 수 있음.

## 참고
이전 Java 시스템 앱의 TWRP/루팅 패치는 필요 없음.
기존 설치 앱과 서명이 달라 충돌하는 것을 방지하기 위해 Godot판 패키지 ID는 com.hgsoft.overusecontroller.godot을 사용함.
실제 안드로이드의 시스템 애니메이션 배율이나 다른 앱의 FPS는 변경하지 않음.
Windows 실행 시 관리자 권한이 필요하지 않음. 반드시 OveruseController-Win64.exe와 OveruseOverlay-Win64.exe를 같은 폴더에 추출할 것.

## v0.4.2 — 앱 밖 효과 진단 및 복구
- Android의 Godot 앱 내부 색상 효과는 비활성화하고 **실제 WindowManager 시스템 오버레이만** 사용함. 네이티브 브리지 연결이 실패했는데 앱 안에만 효과가 나타나는 오해를 방지함.
- 상태 화면에서 `VISIBLE_SYSTEM_OVERLAY`, `WAITING_STAGE`, `NEED_OVERLAY_PERMISSION`, `SCREEN_LOCKED`, `ERROR_...` 등 서비스 상태를 직접 확인할 수 있음.
- 잠금/해제·앱 전환 이후에도 매 2초 WindowManager 오버레이를 점검하여 복구함.
- 색상 오버레이 강도를 조정하여 앱 바깥에서도 육안으로 확인하기 쉬움.
- 권한 허용 화면에서 앱으로 복귀하면 보호 기능을 자동으로 시작함.
- APK를 만들 때 Android 네이티브 클래스, manifest 권한, Godot 스크립트 기본 검사 추가.

### 앱 바깥 오버레이 테스트 (Android)
1. 새 APK를 설치하고 **보호 시작**을 눌러 **다른 앱 위에 표시** 권한을 허용.
2. 앱으로 돌아오면 실행 중 상태인지 확인.
3. **3단계** 또는 **5단계** 테스트 버튼을 누름. 기본값인 10분을 기다릴 필요 없음.
4. 앱의 권한·상태 안내에서 **시스템 오버레이 활성화됨**을 확인.
5. 홈 화면으로 나가거나 다른 앱을 열면 같은 색상·마스크 효과가 보여야 함.

문제가 지속되면 앱 하단의 **Android 서비스 상태** 문자열과 Android OS 버전을 함께 확인. 제조사별 자동 시작/배터리 제한 정책이 포그라운드 서비스를 정지시킬 수 있음. Android 보안 화면(권한 설정/잠금/일부 금융앱)에는 시스템 오버레이 표시가 제한될 수 있음.
Windows 버전은 동일한 시스템 전역 오버레이 기능이 아직 없으며 **앱 창 내부 시뮬레이터**로 제공함.


## v0.4.2 — Windows 데스크톱 오버레이
- Windows 전용 네이티브 보조 프로그램 `OveruseOverlay-Win64.exe` 추가.
- Godot의 메인 창이 비활성화돼도 사용 시간 단계를 계산하도록 Windows 마지막 입력 시각을 읽는 방식 적용 (5분 이상 입력이 없으면 휴식으로 판단).
- 보조 프로그램은 다른 프로그램 위에 반투명 창을 띄우되 **마우스 클릭과 키보드 입력을 가로채지 않도록** Win32 확장 스타일 적용.
- 앱 프로세스가 종료되면 보조 프로그램도 종료되며, 제어 파일에는 단계/강도만 기록.
- Win64 ZIP 내 두 EXE를 같은 폴더에 풀어야 하며, Windows 자체 보호 화면·단독 전체 화면 앱에서는 겹쳐 그리기가 제한될 수 있음.
- Windows판은 시스템 전역 화면 효과를 사용하므로 다른 앱 위 효과가 필요한 경우 최소화한 상태에서도 실행 상태를 유지해야 함.

**사용자 테스트:** Windows판에서 3단계 → 다른 프로그램 열기. 전체 화면 색조가 바뀌되 다른 프로그램의 클릭은 정상 작동해야 함. Android판에서는 3단계 → 홈 화면 또는 다른 앱 열기. 화면 하단에 표시되는 Android 서비스 상태가 `VISIBLE_SYSTEM_OVERLAY`인지 확인.

기기별 실제 동작은 별도 실기기 테스트가 필요함.
