# 하이라이트 자동 생성 기능

## 개요

재생 중인 타임라인에서 원하는 장면(예: 골 순간)에 **하이라이트 마킹** 버튼을 누르면,  
현재 재생 위치를 기준으로 ±3초 구간을 모든 카메라별로 자동 생성하고,  
카메라 선택 · 슬로우 모션 · 순서를 설정하여 기존 클립 뒤에 순차적으로 추가하는 기능입니다.

---

## 시나리오

```
[타임라인 재생 중]
        ↓
[하이라이트 마킹 버튼 클릭]
        ↓
[현재 절대시간 저장 + 카메라별 ±3초 클립 자동 생성]
        ↓
[하이라이트 편집 패널 오픈]
  - 카메라별 포함 여부 선택
  - 슬로우 모션 설정 (1x / ½x / ¼x)
  - 재생 순서 조정
        ↓
[타임라인에 추가]
  - 기존 클립 뒤에 순차적으로 이어 붙임
  - 슬로우 적용 클립은 실제 재생 시간이 늘어남
        ↓
[내보내기]
  - 기존 내보내기 루틴과 동일
  - FFmpeg가 슬로우 클립 속도 조절 후 단일 MP4로 렌더링
```

---

## 주요 설계 결정

### 클립 시간 구조
하이라이트 클립은 같은 시점의 다른 카메라 앵글이므로 소스 타임라인에서 시간이 겹칩니다.  
편집 타임라인에서의 순서를 보장하기 위해 **가상 순차 위치** (`global_in` / `global_out`) 를 부여합니다.

| 필드 | 역할 |
|:---:|:---|
| `start_seek` / `end_seek` | 실제 영상 파일 내 재생 위치 (seek에 사용) |
| `global_in` / `global_out` | 편집 타임라인 상 가상 순차 위치 (표시에 사용) |
| `slow_rate` | 슬로우 모션 배율 (1.0 = 정상, 0.5 = ½배속, 0.25 = ¼배속) |

### 슬로우 모션 처리 (FFmpeg)
| 배율 | 영상 필터 | 음성 필터 |
|:---:|:---:|:---:|
| ½x (0.5) | `setpts=2.0*PTS` | `atempo=0.5` |
| ¼x (0.25) | `setpts=4.0*PTS` | `atempo=0.5,atempo=0.5` |

> `atempo` 필터는 0.5 ~ 2.0 범위만 지원하므로 ¼x는 체이닝으로 처리합니다.

---

## 수정된 파일 목록

### Frontend (React)

| 파일 | 변경 내용 |
|:---|:---|
| `web/src/components/edit/HighlightPanel.jsx` | 신규 생성 — 카메라 선택, 슬로우 설정, 순서 조정 패널 |
| `web/src/components/edit/ControlBar.jsx` | 하이라이트 마킹 버튼 추가 |
| `web/src/components/edit/EditTimeline.jsx` | 슬로우 클립에 ½x / ¼x 배지 표시 |
| `web/src/pages/EditPage.js` | 상태 2개 + 핸들러 2개 + HighlightPanel 렌더링 |
| `web/src/App.css` | 패널·배지·버튼 스타일 추가 |

### Backend (FastAPI + FFmpeg)

| 파일 | 변경 내용 |
|:---|:---|
| `synclab-backend/app/models/schemas.py` | `ClipData`에 `slow_rate: Optional[float] = 1.0` 추가 |
| `synclab-backend/app/services/video_sync_editor.py` | `cut_and_standardize`에 슬로우 모션 FFmpeg 처리 추가 |

---

## 핸들러 흐름 (EditPage.js)

### `handleHighlightMark()`
```
버튼 클릭
  → currentTime 기록
  → cameras 배열 순회
    → 각 카메라의 global 범위 계산 (start_time, end_time 기반)
    → globalIn = max(camStart, currentTime - 3)
    → globalOut = min(camEnd, currentTime + 3)
    → start_seek / end_seek 계산
  → highlightClips 상태 저장
  → showHighlightPanel = true
```

### `handleHighlightConfirm(selectedClips)`
```
패널에서 확인 클릭
  → 기존 savedClips의 마지막 global_out을 cursor 기준으로 설정
  → 선택된 클립을 cursor 기준으로 순차 가상 위치 부여
    (cursor += clip.duration 반복)
  → savedClips 뒤에 이어 붙여 sequence 재부여
  → showHighlightPanel = false
```

---

## UI 구성 (HighlightPanel)

```
┌─────────────────────────────────────────────┐
│  하이라이트 편집                          [X] │
│  카메라 순서와 슬로우 모션을 설정하세요 (±3초) │
├─────────────────────────────────────────────┤
│ ① ● CAM 1  6.0s  [1x] [½x] [¼x]  [↑][↓]  ☑ 포함 │
│ ② ● CAM 2  6.0s  [1x] [½x] [¼x]  [↑][↓]  ☑ 포함 │
│ ③ ● CAM 3  5.8s  [1x] [½x] [¼x]  [↑][↓]  ☐ 포함 │
├─────────────────────────────────────────────┤
│  2개 카메라 선택됨          [취소] [타임라인에 추가] │
└─────────────────────────────────────────────┘
```

---

## 관련 이슈 및 해결

### 클립 겹침 문제
- **원인**: 같은 시점의 카메라별 클립이 동일한 `global_in`/`global_out`을 공유하여 편집 타임라인에서 시각적으로 겹치고 순차 재생이 오작동
- **해결**: `handleHighlightConfirm`에서 클립마다 순차적인 가상 위치를 부여하고, 미리보기·리플레이 시 seek는 `start_seek + camOffset`(실제 소스 시간) 기반으로 계산
