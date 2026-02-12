#!/usr/bin/env python3
import json
import subprocess
import os
from datetime import datetime
from pathlib import Path

class VideoEditServer:
    def __init__(self, output_dir: str = "./output"):
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.temp_dir = self.output_dir / "temp"
        self.temp_dir.mkdir(parents=True, exist_ok=True)

    def cut_and_standardize(self, video_url: str, start: float, duration: float, output_path: str):
        """
        웹의 정보를 바탕으로 자르기 + 규격 통일(싱크 방지 핵심)
        """
        cmd = [
            'ffmpeg',
            '-ss', str(start),        # 입력 파일 앞에서 찾기 (빠름)
            '-t', str(duration),
            '-i', video_url,
            # 모든 클립을 동일한 해상도, 프레임레이트, 오디오 샘플링으로 강제 변환
            # 이렇게 해야 합쳤을 때 소리 밀림이 없습니다.
            '-vf', 'scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2,fps=30',
            '-c:v', 'libx264',
            '-preset', 'ultrafast',   # 처리 속도 우선
            '-crf', '23',
            '-c:a', 'aac',
            '-ar', '44100',           # 오디오 샘플링 레이트 고정
            '-ac', '2',               # 스테레오 고정
            '-y',
            output_path
        ]
        
        try:
            subprocess.run(cmd, check=True, capture_output=True)
        except subprocess.CalledProcessError as e:
            raise Exception(f"FFmpeg Error: {e.stderr.decode()}")

    def merge_segments(self, segments: list, output_path: str):
        """
        규격이 통일된 세그먼트들을 손실 없이 합치기
        """
        concat_file = self.temp_dir / "concat_list.txt"
        with open(concat_file, 'w') as f:
            for seg in segments:
                f.write(f"file '{os.path.abspath(seg)}'\n")

        cmd = [
            'ffmpeg',
            '-f', 'concat',
            '-safe', '0',
            '-i', str(concat_file),
            '-c', 'copy',             # 이미 위에서 규격을 맞춰서 'copy'만 해도 싱크가 맞음
            '-y',
            output_path
        ]
        subprocess.run(cmd, check=True)

    def process_request(self, db_data: dict):
        # 1. 시퀀스 순으로 정렬
        sorted_edits = sorted(db_data['edit_data'], key=lambda x: x['sequence'])
        
        segment_paths = []
        print(f"--- 편집 시작: 세션 {db_data['session_id']} ---")

        # 2. 각 클립 가공
        for i, edit in enumerate(sorted_edits):
            seg_name = f"seg_{i:03d}.mp4"
            seg_path = self.temp_dir / seg_name
            
            print(f"[{edit['sequence']}] 자르는 중: {edit['video_url']} ({edit['start_seek']}s ~)")
            
            self.cut_and_standardize(
                edit['video_url'],
                float(edit['start_seek']),
                float(edit['duration']),
                str(seg_path)
            )
            segment_paths.append(str(seg_path))

        # 3. 최종 병합
        final_name = f"final_{datetime.now().strftime('%Y%m%d_%H%M%S')}.mp4"
        final_path = self.output_dir / final_name
        
        print("\n모든 클립 병합 중...")
        self.merge_segments(segment_paths, str(final_path))
        
        # 4. 정리 (선택 사항)
        # self.cleanup()
        
        return str(final_path)

# 실행 예시
if __name__ == "__main__":
    # 웹에서 넘어온 데이터 예시 (동기화 오프셋이 이미 반영된 start_seek)
    web_data = {
        "session_id": "sports_edit_001",
        "edit_data": [
            {"sequence": 1, "video_url": "cam1.mp4", "start_seek": 10.5, "duration": 5.0},
            {"sequence": 2, "video_url": "cam2.mp4", "start_seek": 8.5, "duration": 3.0}, # cam2가 2초 늦게 시작했다면 웹에서 이미 2초 뺀 값을 보냄
            {"sequence": 3, "video_url": "cam1.mp4", "start_seek": 25.0, "duration": 4.0}
        ]
    }

    editor = VideoEditServer()
    result = editor.process_request(web_data)
    print(f"\n성공! 결과 파일: {result}")