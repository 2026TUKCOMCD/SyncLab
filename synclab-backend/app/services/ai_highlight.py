"""
농구 경기 AI 하이라이트 자동 생성 (비전 기반)

설계 근거: /하이라이트내용정리.txt (2026-06-29, 비전 방식으로 교체)
- 1차: YOLOv8 농구공 추적 + 포물선 궤적 감지 (슛 동작 직접 인식)
- 폴백: 시각적 동작 에너지 패턴 분석 (선수 움직임 변화, 공 감지 실패 시)
- GPU(CUDA) 사용 가능 시 자동 사용, 불가능하면 CPU로 자동 전환
"""

import os
from typing import List, Dict, Optional, Tuple

import cv2
import numpy as np
from scipy.signal import find_peaks
from scipy.ndimage import uniform_filter1d

try:
    import torch
    _TORCH_AVAILABLE = True
except ImportError:
    _TORCH_AVAILABLE = False

try:
    from ultralytics import YOLO
    _ULTRALYTICS_AVAILABLE = True
except ImportError:
    _ULTRALYTICS_AVAILABLE = False


# ── GPU/CPU 처리 시간 예상치 (YOLOv8n 공개 벤치마크 기반 근사값) ──────────
# 5fps 샘플링 기준, 영상 1분(=300 프레임 샘플)당 예상 소요 시간
EST_SEC_PER_MIN_GPU = 6.0
EST_SEC_PER_MIN_CPU = 35.0
EST_FIXED_OVERHEAD_SEC = 5.0  # 모델 로드 등 고정 오버헤드


def get_best_device() -> str:
    """CUDA GPU 사용 가능하면 'cuda', 아니면 'cpu' 반환"""
    if _TORCH_AVAILABLE:
        try:
            if torch.cuda.is_available():
                return "cuda"
        except Exception:
            pass
    return "cpu"


def estimate_processing_seconds(duration_sec: float, device: str) -> float:
    """영상 길이(초)와 처리 장치를 바탕으로 예상 소요 시간(초) 계산"""
    minutes = max(0.0, duration_sec) / 60.0
    per_min = EST_SEC_PER_MIN_GPU if device == "cuda" else EST_SEC_PER_MIN_CPU
    return round(minutes * per_min + EST_FIXED_OVERHEAD_SEC, 1)


class BasketballHighlightDetector:
    """농구 득점 장면 자동 감지기 (YOLOv8 비전 분석 + 동작 에너지 폴백)"""

    SAMPLE_FPS = 5
    BALL_CLASS_ID = 32  # COCO class 32: sports ball
    MOTION_SIZE = (320, 180)

    ARC_PEAK_ZONE = 0.45       # 화면 상단 몇 %까지 슛 정점으로 인정
    MIN_ARC_HEIGHT = 0.07      # 포물선 최소 높이 (프레임 높이 비율)
    TRAJECTORY_GAP_SEC = 1.5   # 궤적 분리 기준 감지 공백
    MIN_TRAJECTORY_FRAMES = 4  # 유효 궤적 최소 프레임 수

    MAX_BUILDUP_SEC = 20.0     # 최대 빌드업 소급 시간
    POST_SCORE_SEC = 2.5       # 득점 후 포함 시간
    REPLAY_PRE_SEC = 2.0       # 리플레이 시작 (score - 2.0)
    MIN_GOAL_GAP_SEC = 8.0     # 연속 득점 최소 간격
    MIN_CLIP_SEC = 1.0         # 최소 클립 길이

    MAX_RESULTS = 25

    def __init__(self, device: Optional[str] = None):
        self.device = device or get_best_device()
        self._model = None
        self._model_load_failed = False

    def _load_model(self):
        if not _ULTRALYTICS_AVAILABLE or self._model_load_failed:
            return None
        if self._model is None:
            try:
                self._model = YOLO("yolov8n.pt")
            except Exception as e:
                print(f"[ai_highlight] YOLOv8 모델 로드 실패, 동작 에너지 폴백으로 전환: {e}")
                self._model_load_failed = True
                return None
        return self._model

    # ── 메인 분석 함수 ──────────────────────────────────────────────
    def analyze_video(
        self,
        video_path: str,
        duration: Optional[float] = None,
        sensitivity: float = 0.85,
        progress_callback=None,
    ) -> List[Dict]:
        if not os.path.exists(video_path):
            raise FileNotFoundError(f"영상 파일을 찾을 수 없습니다: {video_path}")

        cap = cv2.VideoCapture(video_path)
        if not cap.isOpened():
            raise RuntimeError(f"영상을 열 수 없습니다: {video_path}")

        try:
            src_fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
            total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
            frame_w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)) or 1
            frame_h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT)) or 1
            video_duration = duration or (total_frames / src_fps if src_fps else 0.0)

            step = max(1, round(src_fps / self.SAMPLE_FPS))
            est_samples = max(1, total_frames // step)

            model = self._load_model()
            use_vision = model is not None

            ball_detections: List[Tuple[float, float, float]] = []  # (t, x_norm, y_norm)
            motion_energy: List[Tuple[float, float]] = []           # (t, energy)
            prev_gray = None
            frame_idx = 0
            sampled = 0

            while True:
                ok, frame = cap.read()
                if not ok:
                    break

                if frame_idx % step == 0:
                    t_sec = frame_idx / src_fps

                    small = cv2.resize(frame, self.MOTION_SIZE, interpolation=cv2.INTER_AREA)
                    gray = cv2.cvtColor(small, cv2.COLOR_BGR2GRAY)
                    if prev_gray is not None:
                        diff = cv2.absdiff(gray, prev_gray)
                        motion_energy.append((t_sec, float(np.mean(diff))))
                    prev_gray = gray

                    if use_vision:
                        try:
                            results = model.predict(
                                frame, classes=[self.BALL_CLASS_ID], device=self.device,
                                verbose=False, conf=0.25,
                            )
                            best_conf = -1.0
                            best_xy = None
                            for r in results:
                                for box in r.boxes:
                                    conf = float(box.conf[0])
                                    if conf > best_conf:
                                        x1, y1, x2, y2 = box.xyxy[0].tolist()
                                        best_conf = conf
                                        best_xy = ((x1 + x2) / 2 / frame_w, (y1 + y2) / 2 / frame_h)
                            if best_xy is not None:
                                ball_detections.append((t_sec, best_xy[0], best_xy[1]))
                        except Exception as e:
                            print(f"[ai_highlight] YOLO 추론 실패, 동작 에너지 폴백으로 전환: {e}")
                            use_vision = False

                    sampled += 1
                    if progress_callback and est_samples:
                        pct = int(min(95, (sampled / est_samples) * 90))
                        progress_callback(pct)

                frame_idx += 1
        finally:
            cap.release()

        goals = []
        if use_vision and ball_detections:
            goals = self._detect_from_trajectories(ball_detections, sensitivity)

        used_fallback = not goals
        if used_fallback:
            goals = self._detect_from_motion_fallback(motion_energy, sensitivity)

        highlights = self._build_clips(goals, motion_energy, video_duration)

        if progress_callback:
            progress_callback(100)

        return highlights

    # ── 1차: 공 궤적 기반 포물선(슛) 감지 ──────────────────────────
    def _group_trajectories(self, detections: List[Tuple[float, float, float]]) -> List[List[Tuple[float, float, float]]]:
        trajectories: List[List[Tuple[float, float, float]]] = []
        current: List[Tuple[float, float, float]] = []
        for det in detections:
            if current and (det[0] - current[-1][0]) > self.TRAJECTORY_GAP_SEC:
                trajectories.append(current)
                current = []
            current.append(det)
        if current:
            trajectories.append(current)
        return [t for t in trajectories if len(t) >= self.MIN_TRAJECTORY_FRAMES]

    def _detect_from_trajectories(self, detections: List[Tuple[float, float, float]], sensitivity: float) -> List[Dict]:
        goals = []
        min_confidence = 40 + sensitivity * 40  # sensitivity가 높을수록 더 엄격하게 필터링

        for traj in self._group_trajectories(detections):
            ys = [p[2] for p in traj]
            peak_idx = int(np.argmin(ys))  # y 최솟값 = 화면상 최고점

            # a. 정점이 궤적 양 끝 사이(진짜 아치 형태)
            if peak_idx == 0 or peak_idx == len(traj) - 1:
                continue

            peak_y = ys[peak_idx]
            # b. 정점이 화면 상단 45% 이내
            if peak_y > self.ARC_PEAK_ZONE:
                continue

            # c. 정점 전후 y 변화량이 최소 높이 이상 (수평 이동과 구분)
            rise = ys[0] - peak_y
            fall = ys[-1] - peak_y
            if rise < self.MIN_ARC_HEIGHT or fall < self.MIN_ARC_HEIGHT:
                continue

            arc_quality = min(rise, fall)
            confidence = int(min(100, 70 + arc_quality * 200))
            if confidence < min_confidence:
                continue

            peak_x = traj[peak_idx][1]
            if peak_x < 0.40:
                goal_side = "left"
            elif peak_x > 0.60:
                goal_side = "right"
            else:
                goal_side = "unknown"

            score_time = traj[-1][0]
            goals.append({
                "score_time": score_time,
                "confidence": confidence,
                "goal_side": goal_side,
                "type": "scoring_play",
            })

        return goals

    # ── 폴백: 동작 에너지 급감→회복 패턴 감지 ──────────────────────
    def _detect_from_motion_fallback(self, motion_energy: List[Tuple[float, float]], sensitivity: float) -> List[Dict]:
        if len(motion_energy) < 5:
            return []

        times = np.array([m[0] for m in motion_energy])
        values = np.array([m[1] for m in motion_energy])
        smoothed = uniform_filter1d(values, size=3)

        # 급감 지점(저점)을 찾기 위해 신호를 반전하여 피크 탐색
        inverted = -smoothed
        threshold = -np.percentile(smoothed, (1 - sensitivity) * 60 + 10)
        min_distance = max(1, int(self.MIN_GOAL_GAP_SEC * self.SAMPLE_FPS))

        dips, _ = find_peaks(inverted, height=threshold, distance=min_distance)

        baseline = np.percentile(smoothed, 50)
        goals = []
        for dip in dips:
            dip_value = smoothed[dip]
            if baseline <= 0:
                continue
            drop_ratio = max(0.0, (baseline - dip_value) / baseline)
            confidence = int(min(60, 30 + drop_ratio * 60))  # 직접 감지보다 낮게, 최대 60
            goals.append({
                "score_time": float(times[dip]),
                "confidence": confidence,
                "goal_side": "unknown",
                "type": "motion_fallback",
            })

        return goals

    # ── 빌드업 시작점 탐색 + 클립 구간 결정 ─────────────────────────
    def _find_buildup_start(self, score_time: float, motion_energy: List[Tuple[float, float]]) -> float:
        window_start = max(0.0, score_time - self.MAX_BUILDUP_SEC)
        window = [(t, e) for t, e in motion_energy if window_start <= t <= score_time]

        if len(window) < 3:
            return max(0.0, score_time - self.MAX_BUILDUP_SEC / 2)

        values = np.array([e for _, e in window])
        baseline = np.percentile(values, 35) * 1.4

        quiet_times = [t for t, e in window if e <= baseline]
        if quiet_times:
            return max(window_start, quiet_times[-1])  # 득점 직전의 '마지막 조용한 순간'

        return window_start

    def _build_clips(self, goals: List[Dict], motion_energy: List[Tuple[float, float]], video_duration: float) -> List[Dict]:
        # 중복 병합 (MIN_GOAL_GAP_SEC 이내는 신뢰도 높은 것만 유지)
        goals_sorted = sorted(goals, key=lambda g: g["score_time"])
        merged: List[Dict] = []
        for g in goals_sorted:
            if merged and (g["score_time"] - merged[-1]["score_time"]) < self.MIN_GOAL_GAP_SEC:
                if g["confidence"] > merged[-1]["confidence"]:
                    merged[-1] = g
                continue
            merged.append(g)

        # 신뢰도 순 상위 N개
        top = sorted(merged, key=lambda g: g["confidence"], reverse=True)[: self.MAX_RESULTS]
        top.sort(key=lambda g: g["score_time"])

        highlights = []
        for i, g in enumerate(top):
            score_time = g["score_time"]
            buildup_start = self._find_buildup_start(score_time, motion_energy)
            clip_end = min(video_duration, score_time + self.POST_SCORE_SEC) if video_duration else score_time + self.POST_SCORE_SEC
            clip_start = max(0.0, buildup_start)

            if (clip_end - clip_start) < self.MIN_CLIP_SEC:
                continue

            replay_start = max(0.0, score_time - self.REPLAY_PRE_SEC)
            replay_end = min(video_duration, score_time + self.POST_SCORE_SEC) if video_duration else score_time + self.POST_SCORE_SEC

            highlights.append({
                "id": f"goal_{i}",
                "index": i + 1,
                "timestamp": round(score_time, 2),
                "start": round(clip_start, 2),
                "end": round(clip_end, 2),
                "duration": round(clip_end - clip_start, 2),
                "buildup_duration": round(score_time - clip_start, 2),
                "confidence": g["confidence"],
                "type": g["type"],
                "goal_side": g["goal_side"],
                "replay_start": round(replay_start, 2),
                "replay_end": round(replay_end, 2),
            })

        return highlights
