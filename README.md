# TallGate (숏폼 & 릴스 차단 도우미)

**TallGate**는 안드로이드의 접근성(Accessibility) 서비스를 활용하여 인스타그램 릴스, 유튜브 쇼츠, 틱톡 추천 피드 등 무한 스크롤 도파민 콘텐츠를 정밀 감지하고 차단하는 초경량 디지털 디톡스 도구입니다.

SocialLite의 동작 메커니즘을 벤치마킹하여, **"각 앱별 차단 대상 기능만 딱 켜고 끌 수 있는 극도로 간편한 UI"**를 목표로 설계되었습니다.

---

## ✨ 핵심 기능

1. **📷 Instagram**
   * **릴스 (Reels) 차단**: 하단 릴스 탭 진입 및 피드 내 릴스 뷰어 실행 시 즉시 시스템 뒤로가기(`GLOBAL_ACTION_BACK`)를 트리거하여 무한 스크롤 방지.
   * **탐색 (돋보기) 탭 차단**: 시간 낭비를 유발하는 추천 탐색 탭 진입 차단.
2. **▶️ YouTube**
   * **쇼츠 (Shorts) 차단**: 하단 쇼츠 탭 및 쇼츠 플레이어 진입 시 자동 차단.
3. **🎵 TikTok**
   * **추천 피드 (For You) 차단**: 무한 스크롤 추천 피드 진입 차단.
4. **⚙️ 피드백 설정**
   * 차단 시 미세 진동(Haptic) 알림
   * 차단 시 짧은 토스트(Toast) 팝업 안내

---

## 🏗️ 아키텍처 및 동작 원리

* **접근성 서비스 (`TallGateAccessibilityService`)**:
  * `typeWindowStateChanged`, `typeWindowContentChanged` 이벤트를 감지합니다.
  * 실행 중인 포그라운드 앱(`com.instagram.android`, `com.google.android.youtube` 등)의 화면 노드 트리에서 숏폼 고유 식별자(`ClipsViewer`, `reel_player` 등)를 탐색합니다.
  * 조건 일치 시 **0.01초 내에 `performGlobalAction(GLOBAL_ACTION_BACK)`**을 실행하고 디바운스(800ms) 처리하여 쾌적하게 튕겨냅니다.
* **앱 변조 없음**:
  * 인스타/유튜브 원본 APK를 전혀 변조하지 않으므로 **계정 정지(Ban) 위험이 전혀 없습니다.**
* **설정 관리 (`BlockPreferences`)**:
  * SharedPreferences 기반으로 사용자의 토글 상태를 실시간 저장/반영합니다.

---

## 🚀 빌드 및 실행 방법

### Android Studio에서 실행
1. Android Studio에서 `/Users/hwyoon/workspace/tallgate` 폴더를 엽니다.
2. USB로 연결된 삼성 갤럭시 S24 기기를 선택하고 `Run (Shift + F10)`을 누릅니다.
3. 앱 설치 후 화면 상단의 **"권한 설정하러 가기"**를 눌러 안드로이드 설정 > 접근성 > 설치된 앱 > **TallGate**를 `사용(On)`으로 활성화합니다.

### 터미널 CLI 빌드
```bash
# 디버그 APK 빌드
./gradlew assembleDebug

# 연결된 스마트폰에 설치
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
