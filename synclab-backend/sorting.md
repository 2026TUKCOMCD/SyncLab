# 🎬 타임라인 자동 정렬 구현 가이드

---

## 📋 프론트엔드 작업 체크리스트

### ✅ 작성해야 할 파일
```
src/
├── utils/
│   └── timelineUtils.js      # 유틸리티 함수
├── components/
│   └── TimelineEditor.jsx    # React 컴포넌트
└── styles/
    └── timeline.css          # 스타일
```

### ✅ 구현 항목

**1. timelineUtils.js 작성**
- [ ] `autoArrangeTracks()` 함수 - 트랙 자동 배치
- [ ] `calculateClipPosition()` 함수 - 클립 위치 계산
- [ ] `generateTimeMarkers()` 함수 - 시간 눈금 생성

**2. TimelineEditor.jsx 작성**
- [ ] API 호출 (fetch)
- [ ] 자동 정렬 실행
- [ ] 타임라인 렌더링
- [ ] 클립 드래그/트림 기능

**3. CSS 스타일링**
- [ ] `timeline.css` - 타임라인 스타일

---

## 🔄 데이터 흐름
```
┌─────────────────────────────────────────────┐
│         백엔드 (Python)                      │
│                                             │
│  1. DB 조회                                  │
│     SELECT * FROM video                     │
│     WHERE session_session_id = 456          │
│     ORDER BY absolute_start_time            │
│                                             │
│  2. JSON 생성                                │
│     {                                       │
│       "videos": [                           │
│         {                                   │
│           "name": "CAM 1",                  │
│           "absolute_start_time": 170711..., │
│           "absolute_end_time": 170711...,   │
│           "duration": 60.0                  │
│         }                                   │
│       ]                                     │
│     }                                       │
│                                             │
│  3. HTTP 응답                                │
└─────────────────┬───────────────────────────┘
                  │
                  │ GET /api/web/list
                  │
                  ▼
┌─────────────────────────────────────────────┐
│      프론트엔드 (JavaScript)                 │
│                                             │
│  1. API 호출                                 │
│     const data = await fetch('/api/web/list')│
│                                             │
│  2. 자동 정렬                                │
│     const tracks = autoArrangeTracks(videos) │
│                                             │
│  3. 위치 계산                                │
│     videos.forEach(v => {                   │
│       left: (v.start - sessionStart) / total│
│       width: v.duration / total             │
│     })                                      │
│                                             │
│  4. UI 렌더링                                │
│     <div style={{ left: '10%', width: '50%' }}> │
│       CAM 1                                 │
│     </div>                                  │
└─────────────────────────────────────────────┘
```

---

## 🎯 핵심 개념 (이건 내가 헷갈려서 적어놓음)

### 절대 시간 (Absolute Time)
- **용도**: 타임라인 자동 정렬
- **예시**: 2026-02-05 14:30:00 (1707116400000)
- **사용처**: 영상들을 트랙에 배치할 때

### 상대 시간 (Relative Time)
- **용도**: 영상 자르기
- **예시**: 영상의 0초~30초 사용
- **사용처**: FFmpeg로 렌더링할 때

---

## 🚀 시작하기

### 1. API 테스트
```bash
# Postman 또는 curl로 백엔드 API 확인
GET http://localhost:8000/api/web/list
Headers:
  Authorization: Bearer YOUR_TOKEN
```

**예상 응답:**
```json
{
  "videos": [
    {
      "id": 0,
      "name": "CAM 1",
      "absolute_start_time": 1707116400000,
      "absolute_end_time": 1707116460000,
      "duration": 60.0,
      "videoUrl": "https://..."
    }
  ]
}
```

### 2. 파일 생성
```bash
# 유틸리티 함수
touch src/utils/timelineUtils.js

# React 컴포넌트
touch src/components/TimelineEditor.jsx

# CSS 스타일
touch src/styles/timeline.css
```

### 3. 구현 순서

1. **timelineUtils.js** 작성 (자동 정렬 로직)
2. **TimelineEditor.jsx** 작성 (UI 컴포넌트)
3. **timeline.css** 작성 (스타일링)
4. 메인 페이지에 통합

---

## 📊 자동 정렬 알고리즘
```
입력: 시간순으로 정렬된 영상 목록

각 영상마다:
   Track 1 확인:
      - 마지막 영상이 끝난 후 시작? → Track 1에 추가
      - 겹침? → 다음 트랙 확인
   
   Track 2 확인:
      - 마지막 영상이 끝난 후 시작? → Track 2에 추가
      - 겹침? → 다음 트랙 확인
   
   모든 트랙 겹침? → 새 트랙 생성

결과: 겹치지 않게 배치된 트랙들
```

**시각화:**
```
Track 1: [━━━ CAM 1 ━━━][━━━ CAM 4 ━━━]
Track 2:     [━━━━━ CAM 2 ━━━━━]
Track 3:                 [━━ CAM 3 ━━]
```

---

## ✅ 완료 체크리스트

- [ ] 백엔드 API 응답 확인 (절대 시간 포함)
- [ ] `timelineUtils.js` 파일 생성 및 함수 작성
- [ ] `TimelineEditor.jsx` 파일 생성 및 컴포넌트 작성
- [ ] `timeline.css` 파일 생성 및 스타일링
- [ ] 프론트엔드에서 자동 정렬 동작 확인
- [ ] 타임라인 UI 렌더링 확인
- [ ] 브라우저 콘솔에서 데이터 흐름 확인

---

## 📚 참고 사항

### 위치 계산 공식
```javascript
// 클립의 시작 위치 (%)
left = (video.absolute_start_time - sessionStart) / totalDuration * 100

// 클립의 길이 (%)
width = (video.duration * 1000) / totalDuration * 100
```

### 겹침 판정 조건
```javascript
// 이전 영상의 끝 <= 현재 영상의 시작 → 겹치지 않음!
if (lastVideo.absolute_end_time <= video.absolute_start_time) {
    // 같은 트랙에 배치 가능
}
```

---