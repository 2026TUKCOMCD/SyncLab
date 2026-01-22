# 📹 Multi-Angle Video System Backend

이 프로젝트는 멀티 앵글 영상 업로드, 프록시 생성 및 효율적인 비디오 스트리밍을 위한 백엔드 시스템입니다.

---

## 🏗️ System Architecture

### 1. `schemas.py` (Data Architect)
* **Role:** 데이터 입출력 형식 정의 및 자동 검증 수행.
* **Key Functions:**
    * **Data Validation:** 정의된 스키마에 맞지 않는 요청 자동 거부.
    * **Auto Documentation:** Swagger UI(API 문서) 생성을 위한 메타데이터 제공.
    * **Consistency:** 서비스 전반의 데이터 규격 표준화 및 타입 안정성 보장.

### 2. `video.py` (API Controller)
* **Role:** 클라이언트 요청 라우팅 및 서비스 로직 오케스트레이션.
* **Key Functions:**
    * **Endpoint Mapping:** RESTful API 경로 및 HTTP 메서드 정의.
    * **Service Coordination:** DB, S3, FFmpeg 서비스 함수 호출 및 결과 조합.
    * **Error Handling:** 프로세스 중 발생하는 예외 처리 및 규격화된 응답 반환.

---

## 🛣️ API Flow Logic

### 📤 [POST] Video Upload URL Request
1.  **Receive Request:** 클라이언트로부터 업로드 요청 및 메타데이터 수신.
2.  **S3 Service:** 안전한 업로드를 위한 **Presigned URL** 생성.
3.  **DB Service:** 영상 정보 및 초기 상태(`Pending`)를 데이터베이스에 저장.
4.  **Return Response:** 발급된 URL과 부여된 Video ID를 클라이언트에 반환.

### ⚙️ [POST] Proxy Generation Request
1.  **Receive Request:** 특정 비디오에 대한 프록시 변환 요청 수신.
2.  **Task Registration:** `BackgroundTasks`를 통한 비동기 작업 등록.
3.  **Immediate Response:** 서버 부하를 방지하기 위해 작업 접수 즉시 완료 응답 반환.

### 🔍 [GET] Video Information & List
* **Single Inquiry:** 특정 ID 기반 비디오 상세 메타데이터 DB 조회.
* **Session List:** 세션(프로젝트) 단위의 전체 영상 목록 DB 조회 및 반환.

---

## 🛠️ Specialized Services

### `ffmpeg_service.py` (Media Expert)
* **Proxy Conversion:** 원본 영상을 편집에 최적화된 저사양 영상으로 변환.
* **Metadata Extraction:** 영상의 해상도, 프레임 레이트, 코덱 정보 추출.

### `s3_service.py` (AWS Expert)
* **URL Management:** 업로드/다운로드용 Presigned URL 관리.
* **File Operations:** S3 버킷 내 파일 업로드, 다운로드, 삭제 수행.
* **Security:** AWS 자격 증명(Credentials) 및 접근 권한 관리.

---

## 🔄 Background Worker Logic (Proxy Generation)

1.  **Fetch Original:** S3에서 변환할 원본 영상 소스 확보.
2.  **Status Update:** DB 내 영상 상태를 `Processing`으로 변경.
3.  **Execute FFmpeg:** `ffmpeg_service`를 호출하여 실제 인코딩 작업 수행.
4.  **Upload Result:** 생성된 프록시 파일을 S3의 지정된 경로에 업로드.
5.  **Finalize:** DB에 프록시 경로를 업데이트하고 최종 상태를 `Completed`로 변경.