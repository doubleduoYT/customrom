# Overuse Controller v0.4.0 — Godot Engine port

Godot 4.5.2 / GDScript로 UI·설정·시간 기반 0~5단계 로직을 다시 작성한 크로스 플랫폼 포팅판.

## 플랫폼
- Windows x64: 자체 앱 창 안에서 사용 시간·단계·시각 효과 시뮬레이션. **OS 전체 화면에 영향을 주지 않음.**
- Android ARM64: Godot 기반 인터페이스. 선택적 OveruseBridge Android v2 플러그인이 설치되어 있으면, Java 포그라운드 서비스가 화면 사용 시간을 기록하고 다른 앱 위에 터치가 통과하는 오버레이를 표시함. 플러그인이 없는 빌드에서는 앱 내부 시뮬레이션 모드만 작동함.

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
Windows 실행 시 관리자 권한이 필요하지 않음.
