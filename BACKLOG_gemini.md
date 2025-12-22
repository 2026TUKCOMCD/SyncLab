# 🎬 졸업작품: 멀티앵글 영상 편집 웹 서비스 (Multi-Angle Video Editor)

> **프로젝트 목표:**  촬영부터 편집, 렌더링까지 이어지는 End-to-End 멀티앵글 영상 편집 솔루션 개발.  
> 웹 브라우저에서 4개의 영상을 싱크 오차 없이 동시에 편집하고 렌더링하는 것을 목표로 합니다.

---

## 📅 Project Backlog & Roadmap

### 1. 🥇 Core Engine: 촬영 및 인제스트 (Mobile & Server)
**Goal:** 편집의 재료가 되는 영상을 정확한 시간 정보(NTP)와 함께 서버에 안전하게 저장하고 가공합니다.

| ID | Status | Priority | User Story | Feature | Dev Area |
|:--:|:--:|:--:|:---|:---|:---:|
| **CP-101** | - [ ] | 🔥 **P0** | 촬영자로서, 여러 대의 카메라 싱크를 맞추기 위해 **NTP 서버 기준의 오차(Offset)**를 측정하고 싶다. | `NTP Module` | Mobile |
| **CP-102** | - [ ] | 🔥 **P0** | 촬영자로서, 녹화 시작 시 **정확한 시작 시간(Absolute Timestamp)**이 메타데이터에 태깅되게 하고 싶다. | `Meta-data` | Mobile |
| **CP-103** | - [ ] | 🔥 **P0** | 촬영자로서, 촬영된 1080p 고화질 영상을 끊김 없이 서버로 업로드하고 싶다. | `Upload Mgr` | Full Stack |
| **CP-104** | - [ ] | 🔥 **P0** | 시스템 관리자로서, 고용량 영상을 웹 편집용 **저용량 프록시(720p)**로 자동 변환하고 싶다. | `Transcoding` | Backend |
| **CP-105** | - [ ] | 🔹 P2 | 촬영자로서, QR 코드를 스캔하여 내 기기를 특정 프로젝트 방에 즉시 연동하고 싶다. | `Session` | Mobile |
| **CP-106** | - [ ] | 🔹 P2 | 촬영자로서, 네트워크가 불안정할 때 업로드가 실패하지 않고 **이어올리기**가 되게 하고 싶다. | `Resilience` | Mobile |

<br>

### 2. 🥈 Core Feature: 웹 편집 및 싱크 플레이어 (Web Frontend)
**Goal:** 웹 브라우저 상에서 4개의 영상을 오차 없이 동시에 재생하고, 사용자 입력(컷 전환)을 받아냅니다.

| ID | Status | Priority | User Story | Feature | Dev Area |
|:--:|:--:|:--:|:---|:---|:---:|
| **WE-201** | - [ ] | 🔥 **P0** | 편집자로서, 4개의 영상을 시간 오차(Offset)만큼 보정하여 **동시 재생(Sync)**하고 싶다. | `Sync Player` | Frontend |
| **WE-202** | - [ ] | 🔥 **P0** | 편집자로서, 재생 중 화면 클릭 시 **해당 시점과 카메라 ID가 타임라인에 기록**되게 하고 싶다. | `Switcher UI` | Frontend |
| **WE-203** | - [ ] | 🔹 P2 | 편집자로서, 특정 구간의 **재생 속도를 조절(0.5x, 2x)**하고 이를 편집 데이터에 반영하고 싶다. | `Speed Ctrl` | Frontend |
| **WE-204** | - [ ] | 🔹 P2 | 편집자로서, 타임라인에서 내가 작업한 컷 편집 내역을 시각적으로 확인하고 수정하고 싶다. | `Timeline UI` | Frontend |
| **WE-205** | - [ ] | 🍃 P3 | 편집자로서, 생성된 프로젝트의 QR 코드를 띄워 팀원들의 카메라를 초대하고 싶다. | `Dashboard` | Frontend |

<br>

### 3. 🥉 Rendering & Business: 수익화 및 렌더링 (Backend)
**Goal:** 편집 데이터(EDL)를 실제 영상 파일로 만들고, 유료/무료 사용자를 구분합니다.

| ID | Status | Priority | User Story | Feature | Dev Area |
|:--:|:--:|:--:|:---|:---|:---:|
| **RD-301** | - [ ] | 🔥 **P0** | 편집자로서, 편집이 완료된 영상을 **하나의 통합된 MP4 파일**로 렌더링하여 다운로드 받고 싶다. | `Render Engine` | Backend |
| **RD-302** | - [ ] | 🔹 P2 | 서비스 제공자로서, 무료 플랜 사용자의 결과물에는 **워터마크를 강제로 삽입**하고 싶다. | `Watermark` | Backend |
| **RD-303** | - [ ] | 🔹 P2 | 서비스 제공자로서, 결제된 유료 사용자에게는 **1080p 원본 화질**의 클린 본을 제공하고 싶다. | `Export` | Backend |
| **RD-304** | - [ ] | 🍃 P3 | 편집자로서, 렌더링 진행 상황(%)을 실시간으로 확인하고 완료 시 알림을 받고 싶다. | `Notification` | Full Stack |
| **RD-305** | - [ ] | 🍃 P3 | 사용자로서, 카카오페이/토스 등을 통해 프로 요금제를 결제하고 즉시 권한을 얻고 싶다. | `Payment` | Full Stack |

<br>

### 4. 🧩 Technical Backlog: 비기능적 요구사항
**Goal:** 서비스의 안정성, 데이터 구조, 개발 환경을 구축합니다.

| ID | Status | Priority | Goal | Feature | Dev Area |
|:--:|:--:|:--:|:---|:---|:---:|
| **TB-401** | - [ ] | ⚙️ **Tech** | 프로젝트-비디오-EDL 관계를 정립하는 **DB ERD 설계**. | `DB Design` | Backend |
| **TB-402** | - [ ] | ⚙️ **Tech** | 프론트/백엔드 데이터 교환을 위한 **REST API 명세서(Swagger)** 작성. | `API Spec` | Full Stack |
| **TB-403** | - [ ] | 🔹 P2 | FFmpeg 렌더링이 웹 서버를 블로킹하지 않도록 **비동기 작업 큐** 구축. | `Queue Sys` | Backend |
| **TB-404** | - [ ] | 🔹 P2 | AWS S3 또는 고용량 스토리지를 연동하여 영상 파일을 관리. | `Storage` | DevOps |

---

## 📊 Estimation & Resource Planning

<details>
<summary><strong>⏱️ 상세 구현 예측 시간 및 스토리 포인트 (Click to expand)</strong></summary>

### 1. 촬영 및 인제스트 (Total: 29 SP / ~195h)
- **CP-101 (5 SP):** NTP 오차 측정 (TrueTime 등)
- **CP-102 (3 SP):** 절대 시간 메타데이터 태깅
- **CP-103 (5 SP):** 고화질 영상 업로드 (Multipart)
- **CP-104 (8 SP):** FFmpeg 프록시 자동 변환
- **CP-106 (5 SP):** 이어올리기 구현

### 2. 웹 편집 및 싱크 플레이어 (Total: 30 SP / ~190h)
- **WE-201 (13 SP):** Sync Player 오프셋 계산 및 제어 (Max Difficulty)
- **WE-202 (5 SP):** Switcher UI 및 EDL JSON 생성
- **WE-204 (5 SP):** 타임라인 UI 시각화

### 3. 수익화 및 렌더링 (Total: 26 SP / ~165h)
- **RD-301 (13 SP):** JSON 파싱 및 FFmpeg 컷/병합 실행 (Max Difficulty)
- **RD-302 (3 SP):** 워터마크 합성 필터
- **RD-305 (5 SP):** PG사 결제 모듈 연동

### 4. 기술 셋업 (Total: 14 SP / ~85h)
- **TB-401/402:** DB 설계 및 API 명세
- **TB-403:** 비동기 큐(Celery/Bull) 구축

### 📈 총합 요약
| 분류 | SP 합계 | 예측 시간 (Max) |
|---|---|---|
| **Total** | **99 SP** | **약 635 시간** |

</details>
