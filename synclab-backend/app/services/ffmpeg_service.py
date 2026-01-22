import ffmpeg
import os
from pathlib import Path
from typing import Dict, Optional

class FFmpegService:
    
    @staticmethod
    def get_video_info(input_path: str) -> Dict:
        """비디오 메타데이터 추출"""
        try:
            probe = ffmpeg.probe(input_path)
            video_stream = next(
                (s for s in probe['streams'] if s['codec_type'] == 'video'),
                None
            )
            
            if not video_stream:
                raise ValueError("비디오 스트림을 찾을 수 없습니다")
            
            # FPS 계산
            fps_str = video_stream.get('r_frame_rate', '0/1')
            fps_num, fps_den = map(int, fps_str.split('/'))
            fps = fps_num / fps_den if fps_den != 0 else 0
            
            return {
                'duration': float(probe['format'].get('duration', 0)),
                'width': int(video_stream.get('width', 0)),
                'height': int(video_stream.get('height', 0)),
                'resolution': f"{video_stream.get('width')}x{video_stream.get('height')}",
                'fps': int(fps),
                'codec': video_stream.get('codec_name'),
                'bitrate': int(probe['format'].get('bit_rate', 0)),
                'file_size': int(probe['format'].get('size', 0))
            }
        except Exception as e:
            raise Exception(f"비디오 정보 추출 실패: {str(e)}")
    
    @staticmethod
    def create_proxy(
        input_path: str,
        output_path: str,
        resolution: str = "1280x720",
        crf: int = 23
    ) -> str:
        """프록시 영상 생성"""
        try:
            # 출력 디렉토리 생성
            Path(output_path).parent.mkdir(parents=True, exist_ok=True)
            
            # FFmpeg 스트림 구성
            stream = ffmpeg.input(input_path)
            stream = ffmpeg.filter(stream, 'scale', resolution)
            stream = ffmpeg.output(
                stream,
                output_path,
                vcodec='libx264',
                crf=crf,
                preset='medium',
                acodec='aac',
                audio_bitrate='128k',
                **{'movflags': '+faststart'}
            )
            
            # 실행
            ffmpeg.run(stream, overwrite_output=True, capture_stdout=True, capture_stderr=True)
            
            return output_path
            
        except ffmpeg.Error as e:
            error_msg = e.stderr.decode() if e.stderr else str(e)
            raise Exception(f"FFmpeg 프록시 생성 실패: {error_msg}")

# 싱글톤 인스턴스
ffmpeg_service = FFmpegService()