# 🎬 Video Editing System (FastAPI + FFmpeg + MySQL)

웹 프론트엔드에서 설정한 영상 편집 데이터(In/Out점)를 기반으로, 서버 뒷단에서 FFmpeg를 호출하여 물리적인 영상 파일을 생성하고 제공하는 시스템입니다.

## 🏗️ 프로젝트 아키텍처 (Layered Architecture)

프로젝트는 계층형 구조로 설계되어 각 레이어가 명확한 역할을 수행합니다.



- **Router Layer (`app/routers/`)**: 웹 클라이언트의 API 요청을 수신하고 최종 영상 URL을 응답합니다.
- **Service Layer (`app/services/`)**: `ffmpeg_service.py`를 통해 실제 영상 커팅(Trim) 및 병합(Concat) 비즈니스 로직을 처리합니다.
- **Database Layer (`app/database/`)**: `connection.py`의 Connection Pool을 사용하여 MySQL에서 클립 메타데이터를 안전하게 조회합니다.
- **Storage Layer**:
  - `temp_clips/`: 편집 과정 중 생성되는 임시 조각 파일 저장소
  - `exports/`: 최종 편집 완료된 영상 저장소 및 정적 서빙(Static Serving) 경로

---

## 🛠️ 주요 기능 및 워크플로우

### 1. 처리 프로세스
1. **Request**: 사용자가 웹에서 "편집 완료" 버튼을 누르면 `project_id`가 서버로 전송됩니다.
2. **Database Query**: 서버는 `get_db_connection`을 사용하여 해당 프로젝트의 클립 리스트를 `sequence` 순으로 가져옵니다.
3. **Video Processing (FFmpeg)**:
   - **Trim**: 각 클립의 `start_seek`부터 `end_seek`까지를 개별 파일로 추출합니다.
   - **Concat**: 추출된 모든 조각을 하나의 파일로 병합합니다.
4. **Cleanup**: 작업에 사용된 임시 파일(`.txt`, 조각 `.mp4`)을 삭제합니다.
5. **Response**: 사용자가 즉시 재생하거나 다운로드할 수 있는 영상 URL을 반환합니다.

### 2. FFmpeg 편집 전략
- **정밀도**: `-ss`와 `-to` 옵션을 활용하여 초 단위의 정밀한 컷팅을 수행합니다.
- **안정성**: 클립 추출 시 재인코딩(`libx264`)을 거쳐 서로 다른 원본 소스 간의 코덱/해상도 충돌을 방지합니다.
- **속도**: 최종 병합 시에는 인코딩 없이 스트림 복사(`-c copy`) 방식을 사용하여 처리 시간을 획기적으로 단축합니다.

---

## 📂 파일 구성 가이드

### `app/services/ffmpeg_service.py` (핵심 로직)
- FFmpeg 명령어 실행 및 임시 작업 디렉토리 관리.
- 영상 처리 중 발생하는 예외 처리 및 리소스 해제.

### `app/routers/video_router.py` (API 엔드포인트)
- `get_db_connection` 컨텍스트 매니저를 통해 DB 트랜잭션 관리.
- 서비스 레이어 호출 후 최종 경로를 HTTP URL로 변환.

### `app/main.py` (서버 설정)
- `app.mount("/videos", ...)`를 통해 `exports` 폴더의 결과물을 웹에 노출.

---

## 🚀 시작하기 전에

### 1. 필수 요구사항
- 서버 OS에 **FFmpeg**가 설치되어 있어야 합니다.
- 프로젝트 루트에 `.env` 파일이 설정되어 있어야 합니다 (DB 호스트, 계정 등).

### 2. 디렉토리 구조 생성
서버 실행 시 자동으로 생성되지만, 권한 문제가 있을 경우 수동으로 생성하세요.
```bash
mkdir temp_clips
mkdir exports