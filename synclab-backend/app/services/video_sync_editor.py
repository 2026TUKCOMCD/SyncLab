#!/usr/bin/env python3
import json
import subprocess
import os
import uuid
from datetime import datetime
from pathlib import Path
from urllib.parse import urlparse
import boto3
import static_ffmpeg
static_ffmpeg.add_paths()

AWS_REGION = os.getenv("AWS_REGION", "ap-northeast-2")


class VideoEditServer:
    def __init__(self, output_dir: str = "./output"):
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.temp_dir = self.output_dir / "temp"
        self.temp_dir.mkdir(parents=True, exist_ok=True)

    def _download_from_s3(self, video_url: str, index: int) -> str:
        """S3 URL을 로컬 임시파일로 다운로드. 로컬 경로 반환."""
        parsed = urlparse(video_url)
        bucket = parsed.netloc.split('.')[0]
        key = parsed.path.lstrip('/')
        local_path = str(self.temp_dir / f"src_{index:03d}.mp4")

        print(f"[S3] Downloading s3://{bucket}/{key} → {local_path}")
        s3 = boto3.client(
            's3',
            region_name=AWS_REGION,
            aws_access_key_id=os.getenv('AWS_ACCESS_KEY'),
            aws_secret_access_key=os.getenv('AWS_SECRET_KEY'),
        )
        s3.download_file(bucket, key, local_path)
        print(f"[S3] Download complete: {local_path}")
        return local_path

    def cut_and_standardize(self, input_path: str, start: float, duration: float, output_path: str, slow_rate: float = 1.0):
        """자르기 + 규격 통일 + 슬로우 모션 (싱크 방지 핵심)"""
        vf_chain = 'scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2,fps=30'

        cmd = ['ffmpeg', '-ss', str(start), '-t', str(duration), '-i', input_path]

        if 0 < slow_rate < 1.0:
            pts = 1.0 / slow_rate
            vf_chain += f',setpts={pts:.6f}*PTS'
            # atempo 범위는 0.5~2.0 이므로 0.25x는 체이닝 처리
            if slow_rate >= 0.5:
                af_chain = f'atempo={slow_rate:.4f}'
            else:
                af_chain = f'atempo=0.5,atempo={slow_rate * 2:.4f}'
            cmd += ['-vf', vf_chain, '-af', af_chain]
        else:
            cmd += ['-vf', vf_chain]

        cmd += ['-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '23',
                '-c:a', 'aac', '-ar', '44100', '-ac', '2', '-y', output_path]

        print(f"[FFmpeg] input={input_path!r}, start={start}, duration={duration}, slow_rate={slow_rate}")
        result = subprocess.run(cmd, capture_output=True)
        print(f"[FFmpeg] returncode={result.returncode}")

        if result.returncode != 0:
            stderr = result.stderr.decode('utf-8', errors='replace')
            stdout = result.stdout.decode('utf-8', errors='replace')
            print(f"[FFmpeg STDERR] {stderr}")
            raise Exception(f"FFmpeg 처리 실패:\nSTDERR:\n{stderr[-2000:]}\nSTDOUT:\n{stdout[-500:]}")

    def merge_segments(self, segments: list, output_path: str, work_dir: Path = None):
        """규격이 통일된 세그먼트들을 손실 없이 합치기"""
        concat_file = (work_dir or self.temp_dir) / "concat_list.txt"
        with open(concat_file, 'w') as f:
            for seg in segments:
                f.write(f"file '{os.path.abspath(seg)}'\n")

        cmd = [
            'ffmpeg',
            '-f', 'concat',
            '-safe', '0',
            '-i', str(concat_file),
            '-c', 'copy',
            '-y',
            output_path
        ]
        result = subprocess.run(cmd, capture_output=True)
        if result.returncode != 0:
            stderr = result.stderr.decode('utf-8', errors='replace')
            raise Exception(f"병합 실패:\nSTDERR:\n{stderr[-2000:]}")

    def process_request(self, db_data: dict, progress_callback=None):
        """
        progress_callback(0~100): 전체 진행률
        S3 URL → 로컬 다운로드 → FFmpeg 처리 순서로 진행
        job별 서브디렉토리 사용으로 동시 내보내기 충돌 방지
        """
        sorted_edits = sorted(db_data['edit_data'], key=lambda x: x['sequence'])
        total = len(sorted_edits)
        segment_paths = []
        session_id = db_data['session_id']

        # job별 격리된 임시 디렉토리 생성
        job_temp = self.temp_dir / uuid.uuid4().hex[:8]
        job_temp.mkdir(parents=True, exist_ok=True)

        print(f"--- 편집 시작: 세션 {session_id} / 임시 폴더: {job_temp.name} ---")

        try:
            for i, edit in enumerate(sorted_edits):
                seg_path = job_temp / f"seg_{i:03d}.mp4"
                video_url = edit['video_url']

                # S3 URL이면 job 전용 폴더에 다운로드
                if '.s3.' in video_url or 's3.amazonaws.com' in video_url:
                    src_path = str(job_temp / f"src_{i:03d}.mp4")
                    parsed = urlparse(video_url)
                    bucket = parsed.netloc.split('.')[0]
                    key = parsed.path.lstrip('/')
                    print(f"[S3] Downloading s3://{bucket}/{key} → {src_path}")
                    s3 = boto3.client(
                        's3',
                        region_name=AWS_REGION,
                        aws_access_key_id=os.getenv('AWS_ACCESS_KEY'),
                        aws_secret_access_key=os.getenv('AWS_SECRET_KEY'),
                    )
                    s3.download_file(bucket, key, src_path)
                    print(f"[S3] Download complete")
                    input_path = src_path
                else:
                    input_path = video_url

                print(f"[{i+1}/{total}] 자르는 중: ({edit['start_seek']}s ~ +{edit['duration']}s)")

                self.cut_and_standardize(
                    input_path,
                    float(edit['start_seek']),
                    float(edit['duration']),
                    str(seg_path),
                    slow_rate=float(edit.get('slow_rate', 1.0))
                )
                segment_paths.append(str(seg_path))

                if progress_callback:
                    progress_callback(int((i + 1) / total * 90))

            if progress_callback:
                progress_callback(90)

            # 파일명: SyncLab_(날짜시간)_(SID 뒤 6자리).mp4
            sid_suffix = session_id[-6:] if len(session_id) >= 6 else session_id
            final_name = f"SyncLab_{datetime.now().strftime('%Y%m%d_%H%M%S')}_{sid_suffix}.mp4"
            final_path = self.output_dir / final_name

            print("\n모든 클립 병합 중...")
            self.merge_segments(segment_paths, str(final_path), work_dir=job_temp)

        finally:
            import shutil
            shutil.rmtree(job_temp, ignore_errors=True)
            print(f"[Cleanup] 임시 폴더 삭제: {job_temp.name}")

        if progress_callback:
            progress_callback(100)

        print(f"--- 완료: {final_path} ---")
        return str(final_path)


"""
# 실행 예시
if __name__ == "__main__":
    web_data = {
        "session_id": "sports_edit_001",
        "edit_data": [
            {"sequence": 1, "video_url": "cam1.mp4", "start_seek": 10.5, "duration": 5.0},
            {"sequence": 2, "video_url": "cam2.mp4", "start_seek": 8.5, "duration": 3.0},
            {"sequence": 3, "video_url": "cam1.mp4", "start_seek": 25.0, "duration": 4.0}
        ]
    }

    editor = VideoEditServer()
    result = editor.process_request(web_data)
    print(f"\n성공! 결과 파일: {result}")
"""
