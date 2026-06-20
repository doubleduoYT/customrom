# Overuse Controller

디지털 과의존 완화용 시스템 앱 프로토타입이다. 앱을 차단하지 않고, 오래 사용할수록 화면 위에 터치가 통과되는 시각적 방해 효과를 단계적으로 올린다.

## 동작

- 화면 켠 뒤 10분: 기본 상태 유지
- 10분 이후: 1단계 진입 및 알림
- 이후 2분마다 단계 상승
- 최대 5단계
- 화면을 꺼두면 1분마다 단계 하락
- 단계 상승 알림: `단계가 높아졌습니다\n현재 N단계입니다`
- 단계 하락 알림: `단계가 낮아졌습니다\n현재 N단계입니다`
- 단계가 높아질수록:
  - 터치가 통과되는 화면 가림 오버레이가 추가됨
  - 전체 바탕색이 0.5초마다 랜덤 변경됨
  - 오버레이 이동/회전/색 변화가 저FPS처럼 보여 불편함을 줌
  - 시스템 애니메이션 scale을 올려서 동작이 느려진 듯한 느낌을 줌

## Android Studio 없이 빌드하기

이 저장소를 GitHub에 올린 뒤 `Actions` 탭에서 **Build Overuse Controller Patch** 워크플로를 실행하면 된다.

결과물은 artifact로 제공된다.

- `app-debug.apk`
- `overuse-controller-patch.zip`

## TWRP 적용

1. `overuse-controller-patch.zip`을 폰 저장소나 SD 카드에 복사
2. TWRP 진입
3. 가능하면 `System`, `Boot`, `Data` 백업
4. `Install` → `overuse-controller-patch.zip` 선택
5. Swipe to flash
6. Reboot System
7. 부팅 후 앱 아이콘에서 한 번 실행해서 오버레이/설정 권한을 확인

## 주의

이건 SystemUI.apk 자체를 직접 수정하는 방식이 아니라 `/system/priv-app`에 시스템 앱을 추가하는 방식이다. SystemUI 리소스 리빌드 문제를 피하면서 시연 가능한 효과를 얻기 위한 MVP다.


## v0.2 test/fix notes

- Fixed stage changes so overlay/notifications still work even if WRITE_SETTINGS is not granted.
- Added in-app test buttons for stage 1, stage 3, stage 5, and reset stage 0.
- Normal mode still uses 10 minutes of screen-on time before stage 1.
