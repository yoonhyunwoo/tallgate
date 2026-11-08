# TallGate

안드로이드 접근성 서비스(Accessibility Service)를 이용해 인스타그램 릴스, 유튜브 쇼츠, 틱톡 추천 피드를 감지하고 차단하는 앱입니다. 원본 앱 변조 없이 화면 노드 트리 기반으로 동작합니다.

## 주요 기능

- **Instagram**
  - **릴스 차단**: 홈의 'For you' 탭 진입 시 'Feed' 탭으로 자동 전환, 전체화면 릴스 뷰어 감지 시 뒤로가기(`GLOBAL_ACTION_BACK`) 실행 (DM 채팅방 예외 처리).
  - **탐색 탭 차단**: 돋보기(탐색) 탭 선택 시 홈 탭으로 복귀 (기본값: 꺼짐).
- **YouTube**
  - **쇼츠 차단**: 쇼츠 플레이어 진입 시 뒤로가기 실행.
- **TikTok**
  - **추천 피드 차단**: For You(추천) 피드 진입 시 뒤로가기 실행.
- **피드백 설정**
  - 차단 시 햅틱 진동 및 토스트 알림 (개별 On/Off 지원).

## 동작 방식

`TallGateAccessibilityService`가 대상 앱의 윈도우 및 콘텐츠 변경 이벤트를 감지하여 처리합니다.

1. **이벤트 필터링**: 대상 앱(`com.instagram.android`, `com.google.android.youtube`, TikTok)의 `TYPE_WINDOW_STATE_CHANGED`, `TYPE_WINDOW_CONTENT_CHANGED` 이벤트만 수신.
2. **노드 검사**: 활성 윈도우 노드 트리에서 숏폼 고유 식별자(`clips_viewer_container`, `reel_watch_fragment_root` 등) 및 뷰 상태 탐색.
3. **차단 및 복귀**: 조건 일치 시 탭 전환 또는 시스템 뒤로가기를 호출하고 디바운스(800ms) 적용.

## 빌드 및 설치

### 요구 사항
- Android 8.0 (API 26) 이상
- JDK 17 이상

### 빌드

```bash
# 디버그 APK 빌드
./gradlew assembleDebug

# 기기 설치
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

앱 설치 후 **설정 > 접근성 > 설치된 앱 > TallGate**에서 접근성 권한을 활성화해야 정상 동작합니다.
