# 실시간 다각도 스트리밍 구현 - Claude Code 프롬프트 모음

> VSCode에서 Claude Code에 아래 프롬프트를 순서대로 복사 붙여넣기 하세요.
> 각 단계가 완료된 걸 확인한 후 다음 단계로 넘어가세요.

---

## 📋 전체 구현 순서

1. **STEP 1** - 백엔드: LiveKit 서버 + Docker 설정
2. **STEP 2** - 백엔드: 라이브 세션 API 구현
3. **STEP 3** - Android: LiveKit SDK 추가 + 카메라 스트리밍 화면
4. **STEP 4** - Android: 컨트롤러 화면 (화면 전환 조작)
5. **STEP 5** - Android: NavGraph 연결
6. **STEP 6** - 웹: 라이브 시청 페이지

---

---

## STEP 1 — 백엔드: LiveKit 서버 + Docker 설정

```
이 프로젝트는 다각도 영상 촬영/편집 시스템이야.
현재 docker-compose.yml에 MySQL, FastAPI(포트 3000), React(포트 80) 서비스가 있어.

아래 작업을 해줘:

1. docker-compose.yml에 LiveKit 서버 컨테이너를 추가해줘
   - image: livekit/livekit-server:latest
   - 포트: 7880(HTTP), 7881(TCP), 7882(UDP/TURN)
   - synclab-network에 연결
   - livekit.yaml 설정 파일을 마운트해서 사용

2. livekit.yaml 설정 파일을 루트 디렉토리에 생성해줘
   - api_key: "synclab-key"
   - api_secret: "synclab-secret-minimum-32-chars-long!!"
   - 개발용이니까 TURN 서버는 기본 설정으로
   - 로그 레벨: info

3. .env 파일에 아래 변수를 추가해줘
   LIVEKIT_URL=ws://localhost:7880
   LIVEKIT_API_KEY=synclab-key
   LIVEKIT_API_SECRET=synclab-secret-minimum-32-chars-long!!

4. synclab-backend/requirements.txt에 아래 패키지를 추가해줘
   livekit-api

작업 후 docker-compose up -d livekit 명령어로 LiveKit만 먼저 실행하는 방법도 알려줘.
```

---

## STEP 2 — 백엔드: 라이브 세션 API 구현

```
이 프로젝트의 백엔드는 FastAPI(Python)야.
기존 파일 구조:
- app/routers/mobile_session.py : 세션 생성/참가 (invite_code 방식, JWT 인증)
- app/routers/mobile_auth.py : JWT 인증 (get_current_user_id 함수)
- app/database/connection.py : get_db_connection() 함수

기존 mobile_session.py의 패턴(JWT 인증, DB 연결 방식)을 그대로 따라서
app/routers/live_session.py 파일을 새로 만들어줘.

구현할 API:

1. POST /api/live/session/create
   - 라이브 세션 생성
   - LiveKit API로 방(room) 생성
   - 방 이름: session_id 기반으로 생성 (예: "live_SID_20260224_a1b2c3")
   - 응답: session_id, room_name, livekit_url

2. POST /api/live/session/token
   - LiveKit 참가 토큰 발급
   - body: { session_id, role } role은 "camera" | "controller" | "viewer"
   - camera/controller는 publish 권한 O, viewer는 subscribe만
   - 응답: token (LiveKit JWT 토큰)

3. POST /api/live/session/switch
   - 메인 화면 전환 명령
   - body: { session_id, target_camera_id }
   - DB에 현재 활성 카메라를 기록
   - 응답: { status: "success", active_camera: target_camera_id }

4. GET /api/live/session/{session_id}/status
   - 현재 라이브 세션 상태 조회
   - 참여 중인 카메라 목록, 현재 활성 카메라 반환

그리고 app/main.py에 라우터를 등록해줘.

LiveKit Python SDK 사용법:
- from livekit import api
- livekit_api = api.LiveKitAPI(url, api_key, api_secret)
- room 생성: await livekit_api.room.create_room(api.CreateRoomRequest(name=room_name))
- 토큰: access_token = api.AccessToken(api_key, api_secret).with_grants(...).to_jwt()
```

---

## STEP 3 — Android: LiveKit SDK + 카메라 스트리밍 화면

```
이 프로젝트는 Android Kotlin + Jetpack Compose 앱이야.
기존 패턴:
- MVVM 구조: Screen(UI) → ViewModel → Repository → API
- 기존 RecordScreen.kt 위치: ui/screens/record/RecordScreen.kt
- 기존 API 호출: Retrofit + OkHttp (NetworkClientManager 사용)
- 인증: JWT Bearer 토큰 헤더

아래 작업을 순서대로 해줘:

[1단계] app/build.gradle.kts에 LiveKit Android SDK 추가
implementation("io.livekit:livekit-android:2.7.0")
implementation("io.livekit:livekit-android-compose-components:2.7.0")

[2단계] 아래 파일들을 생성해줘

파일 1: data/api/LiveApiService.kt
- Retrofit interface
- POST /api/live/session/create → LiveSessionResponse
- POST /api/live/session/token → LiveTokenResponse
- POST /api/live/session/switch → SwitchResponse
- GET /api/live/session/{sessionId}/status → LiveStatusResponse

파일 2: data/model/LiveModels.kt
- LiveSessionResponse, LiveTokenResponse, SwitchResponse, LiveStatusResponse 데이터 클래스
- CameraParticipant 데이터 클래스 (id, name, isActive)

파일 3: data/repository/LiveRepository.kt
- createLiveSession(sessionId: String): Result<LiveSessionResponse>
- getToken(sessionId: String, role: String): Result<String>
- switchCamera(sessionId: String, cameraId: String): Result<Unit>
- 기존 NetworkClientManager와 동일한 방식으로 JWT 토큰 헤더 추가

파일 4: ui/screens/live/LiveCameraViewModel.kt
- 상태: isStreaming, connectionState, errorMessage
- fun startStreaming(sessionId: String, context: Context)
- fun stopStreaming()
- LiveKit Room 연결 및 카메라/마이크 트랙 publish

파일 5: ui/screens/live/LiveCameraScreen.kt
- RecordScreen.kt와 비슷한 UI 구조
- 카메라 미리보기 (LiveKit VideoView 사용)
- 하단에 스트리밍 시작/종료 버튼
- 연결 상태 표시 (연결 중 / 스트리밍 중 / 종료)
- navController, homeViewModel, sessionId를 파라미터로 받음

LiveKit Android 연결 코드 패턴:
val room = remember { LiveKit.create(appContext = context) }
room.connect(url = livekitUrl, token = token)
room.localParticipant.setCameraEnabled(true)
room.localParticipant.setMicrophoneEnabled(false)
```

---

## STEP 4 — Android: 컨트롤러 화면 (화면 전환 조작)

```
이 프로젝트는 Android Kotlin + Jetpack Compose 앱이야.
STEP 3에서 LiveRepository, LiveModels, LiveApiService가 이미 생성됐어.

아래 파일들을 추가로 생성해줘:

파일 1: ui/screens/live/LiveControllerViewModel.kt
- 상태:
  - cameras: List<CameraParticipant> (참여 중인 카메라 목록)
  - activeCamera: String (현재 메인 화면 카메라 ID)
  - isConnected: Boolean
- fun loadSessionStatus(sessionId: String) : 1초마다 폴링해서 상태 업데이트
- fun switchCamera(sessionId: String, cameraId: String) : 메인 화면 전환
- fun connectAsController(sessionId: String, context: Context) : LiveKit에 controller로 연결 (subscribe만)

파일 2: ui/screens/live/LiveControllerScreen.kt
UI 구성:
- 상단: "라이브 컨트롤러" 타이틀 + 현재 세션 ID 표시
- 중앙: 카메라 목록을 2열 그리드로 표시
  - 각 카메라 카드: 카메라 이름 + 라이브 미리보기 썸네일 (VideoView)
  - 현재 메인 화면인 카메라는 카드에 빨간 테두리 + "LIVE" 배지 표시
  - 카드 탭하면 switchCamera() 호출
- 하단: "방송 종료" 버튼

파라미터: navController, homeViewModel, sessionId
```

---

## STEP 5 — Android: NavGraph 연결 + Screen 라우트 추가

```
이 프로젝트는 Android Kotlin + Jetpack Compose 앱이야.
기존 파일: ui/navigation/NavGraph.kt
기존 Screen 라우트 정의 파일을 찾아서 확인해줘.

아래 작업을 해줘:

[1단계] Screen 라우트 파일에 추가
- Screen.LiveCamera : route = "live_camera/{sessionId}"  (카메라 역할 폰)
- Screen.LiveController : route = "live_controller/{sessionId}"  (컨트롤러 역할 폰)

[2단계] NavGraph.kt에 아래 두 화면을 추가해줘
- LiveCameraScreen : sessionId를 argument로 받아서 전달
- LiveControllerScreen : sessionId를 argument로 받아서 전달

[3단계] HomeScreen에서 라이브 시작 버튼 추가
HomeScreen.kt를 찾아서 기존 UI를 분석한 다음,
기존 세션이 있을 때 "라이브 시작(카메라)" 버튼과 "라이브 컨트롤" 버튼을 추가해줘.
버튼을 누르면 해당 라이브 화면으로 navigate.

navigate 패턴 예시:
navController.navigate("live_camera/${sessionId}")
navController.navigate("live_controller/${sessionId}")
```

---

## STEP 6 — 웹: 라이브 시청 페이지

```
이 프로젝트의 웹은 React (Create React App)야.
기존 파일:
- src/pages/EditPage.js (857줄, 가장 복잡한 페이지)
- src/pages/MainPage.js
- src/App.js (라우팅 확인 필요)

아래 작업을 해줘:

[1단계] 패키지 추가
package.json에 추가:
"@livekit/components-react": "^2.0.0",
"@livekit/components-styles": "^1.0.0",
"livekit-client": "^2.0.0"

[2단계] src/pages/LivePage.js 생성
기능:
- URL 파라미터로 sessionId를 받음 (/live/:sessionId)
- 페이지 진입 시 백엔드에서 viewer 토큰 발급 (POST /api/live/session/token)
- LiveKitRoom 컴포넌트로 연결
- 메인 화면: 현재 활성 카메라 영상을 크게 표시
- 하단: 참여 중인 카메라들 썸네일 (작게)
- 연결 상태 표시

LiveKit React 기본 패턴:
import { LiveKitRoom, VideoTrack, useTracks } from '@livekit/components-react';
import '@livekit/components-styles';

const serverUrl = process.env.REACT_APP_LIVEKIT_URL || 'ws://localhost:7880';

<LiveKitRoom serverUrl={serverUrl} token={token} connect={true}>
  // 내부 컴포넌트
</LiveKitRoom>

[3단계] .env 파일에 추가 (웹 루트의 .env)
REACT_APP_LIVEKIT_URL=ws://localhost:7880
REACT_APP_API_URL=http://localhost:3000

[4단계] App.js에 라우트 추가
/live/:sessionId → LivePage 연결

기존 EditPage.js의 API 호출 패턴(fetch 사용 방식)을 LivePage에서도 동일하게 사용해줘.
```

---

## STEP 7 — 통합 테스트 체크리스트 (테스트용 프롬프트)

```
지금까지 구현한 실시간 라이브 스트리밍 기능을 테스트하려고 해.

아래 순서로 테스트할 수 있는 체크리스트와,
각 단계에서 실패했을 때 확인할 디버깅 포인트를 정리해줘.

테스트 환경:
- 서버: Mac (docker-compose)
- 카메라 폰: Android 폰 1-3대
- 컨트롤러 폰: Android 폰 1대
- 시청자: 웹 브라우저

테스트 순서:
1. docker-compose up → LiveKit 서버 정상 실행 확인
2. 백엔드 API 테스트 (curl 또는 Swagger UI)
3. Android 앱 빌드 → 카메라 폰에서 스트리밍 시작
4. 컨트롤러 폰에서 화면 전환
5. 웹 브라우저에서 시청

각 단계별로 성공/실패 판단 기준과 실패 시 로그 확인 방법도 알려줘.
```

---

## 💡 참고 사항

- 각 STEP은 독립적으로 테스트 가능하도록 설계됨
- STEP 1~2 완료 후 curl로 API 테스트 권장
- STEP 3~4는 Android Studio에서 빌드 에러 확인하며 진행
- 에러 발생 시 에러 메시지를 그대로 Claude Code에 붙여넣으면 됨
