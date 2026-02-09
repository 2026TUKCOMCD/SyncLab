import os
import subprocess
import uuid
from pathlib import Path

class FFmpegService:
    def __init__(self):
        # 결과물과 임시 파일을 저장할 경로 설정
        self.temp_dir = Path("temp_clips")
        self.export_dir = Path("exports")
        
        # 필요한 디렉토리가 없으면 생성
        self.temp_dir.mkdir(parents=True, exist_ok=True)
        self.export_dir.mkdir(parents=True, exist_ok=True)

    def render_project(self, clips: list, project_id: int) -> str:
        """
        DB에서 가져온 클립 리스트를 바탕으로 영상을 편집하고 저장합니다.
        """
        session_id = uuid.uuid4().hex[:8]
        temp_files = []

        try:
            # 1. 각 클립별로 지정된 구간 자르기 (Trim)
            for i, clip in enumerate(clips):
                temp_path = self.temp_dir / f"clip_{session_id}_{i}.mp4"
                
                # 원본에서 start_seek ~ end_seek 구간 추출
                # -preset ultrafast를 사용하여 편집 속도를 최대로 높임
                trim_cmd = [
                    'ffmpeg', '-y',
                    '-i', clip['video_url'],
                    '-ss', str(clip['start_seek']),
                    '-to', str(clip['end_seek']),
                    '-c:v', 'libx264', '-c:a', 'aac',
                    '-preset', 'ultrafast',
                    str(temp_path)
                ]
                subprocess.run(trim_cmd, check=True)
                temp_files.append(str(temp_path))

            # 2. FFmpeg Concat을 위한 텍스트 리스트 파일 생성
            list_path = self.temp_dir / f"list_{session_id}.txt"
            with open(list_path, 'w', encoding='utf-8') as f:
                for p in temp_files:
                    # 절대 경로를 사용하여 FFmpeg가 파일을 정확히 찾도록 함
                    f.write(f"file '{os.path.abspath(p)}'\n")

            # 3. 모든 조각 영상 합치기 (Concat)
            output_filename = f"final_{project_id}_{session_id}.mp4"
            final_path = self.export_dir / output_filename
            
            # 이미 자른 영상들이므로 재인코딩 없이 '-c copy'로 빠르게 병합
            concat_cmd = [
                'ffmpeg', '-y',
                '-f', 'concat', '-safe', '0',
                '-i', str(list_path),
                '-c', 'copy',
                str(final_path)
            ]
            subprocess.run(concat_cmd, check=True)

            # 4. 사용이 끝난 임시 파일 삭제 (Cleanup)
            os.remove(list_path)
            for f in temp_files:
                if os.path.exists(f):
                    os.remove(f)

            return output_filename

        except subprocess.CalledProcessError as e:
            # FFmpeg 실행 중 에러 발생 시 처리
            self._cleanup_error(temp_files)
            raise Exception(f"FFmpeg 작업 중 오류 발생: {str(e)}")
        except Exception as e:
            self._cleanup_error(temp_files)
            raise Exception(f"렌더링 서비스 오류: {str(e)}")

    def _cleanup_error(self, temp_files):
        """에러 발생 시 남아있는 임시 조각 파일들을 삭제합니다."""
        for f in temp_files:
            if os.path.exists(f):
                os.remove(f)

# 싱글톤 인스턴스로 생성하여 어디서든 불러올 수 있게 함
ffmpeg_service = FFmpegService()