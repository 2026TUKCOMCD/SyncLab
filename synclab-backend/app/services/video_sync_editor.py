#!/usr/bin/env python3
"""
영상 동기화 및 편집 시스템
- 여러 영상을 가장 긴 영상 기준으로 동기화
- JSON 데이터를 기반으로 영상 편집 및 병합
"""

import json
import subprocess
import os
from datetime import datetime
from typing import List, Dict, Optional
from pathlib import Path


class VideoSyncEditor:
    def __init__(self, output_dir: str = "./output"):
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.temp_dir = self.output_dir / "temp"
        self.temp_dir.mkdir(parents=True, exist_ok=True)
    
    def get_video_metadata(self, video_path: str) -> Dict:
        """
        ffprobe를 사용하여 영상의 메타데이터 추출
        """
        cmd = [
            'ffprobe',
            '-v', 'quiet',
            '-print_format', 'json',
            '-show_format',
            '-show_streams',
            video_path
        ]
        
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, check=True)
            metadata = json.loads(result.stdout)
            
            # 비디오 스트림 찾기
            video_stream = next(
                (s for s in metadata['streams'] if s['codec_type'] == 'video'),
                None
            )
            
            if not video_stream:
                raise ValueError(f"비디오 스트림을 찾을 수 없습니다: {video_path}")
            
            duration = float(metadata['format']['duration'])
            creation_time = metadata['format'].get('tags', {}).get('creation_time', None)
            
            return {
                'duration': duration,
                'width': int(video_stream['width']),
                'height': int(video_stream['height']),
                'fps': eval(video_stream['r_frame_rate']),
                'creation_time': creation_time,
                'codec': video_stream['codec_name']
            }
        except subprocess.CalledProcessError as e:
            raise Exception(f"메타데이터 추출 실패: {video_path}\n{e.stderr}")
    
    def find_longest_video(self, video_urls: List[str]) -> Dict:
        """
        가장 긴 영상 찾기 (동기화 기준)
        """
        longest = None
        longest_duration = 0
        
        for url in video_urls:
            metadata = self.get_video_metadata(url)
            if metadata['duration'] > longest_duration:
                longest_duration = metadata['duration']
                longest = {
                    'url': url,
                    'metadata': metadata
                }
        
        return longest
    
    def calculate_sync_offset(self, base_time: str, target_time: str) -> float:
        """
        두 영상 간의 시간 차이 계산 (동기화 오프셋)
        creation_time 기준으로 계산
        """
        if not base_time or not target_time:
            return 0.0
        
        try:
            base_dt = datetime.fromisoformat(base_time.replace('Z', '+00:00'))
            target_dt = datetime.fromisoformat(target_time.replace('Z', '+00:00'))
            offset = (target_dt - base_dt).total_seconds()
            return offset
        except Exception as e:
            print(f"Warning: 시간 동기화 계산 실패 - {e}")
            return 0.0
    
    def cut_video_segment(self, video_url: str, start_time: float, end_time: float, 
                          output_path: str, sync_offset: float = 0.0) -> str:
        """
        영상의 특정 구간 자르기 (동기화 오프셋 적용)
        """
        # 동기화 오프셋 적용
        adjusted_start = max(0, start_time + sync_offset)
        adjusted_end = end_time + sync_offset
        duration = adjusted_end - adjusted_start
        
        cmd = [
            'ffmpeg',
            '-ss', str(adjusted_start),
            '-i', video_url,
            '-t', str(duration),
            '-c:v', 'libx264',
            '-preset', 'medium',
            '-crf', '23',
            '-c:a', 'aac',
            '-b:a', '128k',
            '-y',
            output_path
        ]
        
        try:
            subprocess.run(cmd, check=True, capture_output=True)
            return output_path
        except subprocess.CalledProcessError as e:
            raise Exception(f"영상 자르기 실패: {video_url}\n{e.stderr.decode()}")
    
    def create_concat_file(self, video_segments: List[str], concat_file_path: str):
        """
        ffmpeg concat을 위한 파일 리스트 생성
        """
        with open(concat_file_path, 'w') as f:
            for segment in video_segments:
                # 경로를 절대 경로로 변환
                abs_path = os.path.abspath(segment)
                f.write(f"file '{abs_path}'\n")
    
    def merge_videos(self, video_segments: List[str], output_path: str) -> str:
        """
        여러 영상 세그먼트를 하나로 병합
        """
        concat_file = self.temp_dir / "concat_list.txt"
        self.create_concat_file(video_segments, str(concat_file))
        
        cmd = [
            'ffmpeg',
            '-f', 'concat',
            '-safe', '0',
            '-i', str(concat_file),
            '-c', 'copy',
            '-y',
            output_path
        ]
        
        try:
            subprocess.run(cmd, check=True, capture_output=True)
            return output_path
        except subprocess.CalledProcessError as e:
            # copy codec이 실패하면 re-encode 시도
            print("Warning: codec copy 실패, re-encoding 시도...")
            cmd = [
                'ffmpeg',
                '-f', 'concat',
                '-safe', '0',
                '-i', str(concat_file),
                '-c:v', 'libx264',
                '-preset', 'medium',
                '-crf', '23',
                '-c:a', 'aac',
                '-b:a', '128k',
                '-y',
                output_path
            ]
            subprocess.run(cmd, check=True, capture_output=True)
            return output_path
    
    def process_edit_sequence(self, edit_data: List[Dict], 
                             longest_video_info: Dict) -> str:
        """
        편집 시퀀스 처리 (JSON 데이터 기반)
        
        Args:
            edit_data: 편집 정보 리스트
                [
                    {
                        "sequence": 1,
                        "video_url": "/path/to/video1.mp4",
                        "start_seek": 0,
                        "end_seek": 15,
                        "duration": 15
                    },
                    ...
                ]
            longest_video_info: 가장 긴 영상의 정보 (동기화 기준)
        """
        # sequence 순서로 정렬
        sorted_edits = sorted(edit_data, key=lambda x: int(x['sequence']))
        
        # 각 세그먼트 자르기
        segments = []
        base_creation_time = longest_video_info['metadata'].get('creation_time')
        
        for i, edit in enumerate(sorted_edits):
            video_url = edit['video_url']
            start_time = float(edit['start_seek'])
            end_time = float(edit['end_seek'])
            
            # 동기화 오프셋 계산
            video_metadata = self.get_video_metadata(video_url)
            target_creation_time = video_metadata.get('creation_time')
            sync_offset = self.calculate_sync_offset(base_creation_time, target_creation_time)
            
            # 세그먼트 파일명
            segment_path = self.temp_dir / f"segment_{i+1:03d}.mp4"
            
            print(f"처리 중: Sequence {edit['sequence']} - {video_url}")
            print(f"  구간: {start_time}s ~ {end_time}s (동기화 오프셋: {sync_offset:.2f}s)")
            
            # 영상 자르기
            self.cut_video_segment(
                video_url, 
                start_time, 
                end_time, 
                str(segment_path),
                sync_offset
            )
            segments.append(str(segment_path))
        
        # 최종 출력 파일명
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        final_output = self.output_dir / f"final_video_{timestamp}.mp4"
        
        print(f"\n세그먼트 병합 중...")
        # 세그먼트 병합
        output_video = self.merge_videos(segments, str(final_output))
        
        print(f"\n✓ 완료! 출력 파일: {output_video}")
        return output_video
    
    def cleanup_temp_files(self):
        """
        임시 파일 정리
        """
        import shutil
        if self.temp_dir.exists():
            shutil.rmtree(self.temp_dir)
            self.temp_dir.mkdir(parents=True, exist_ok=True)


def process_from_database(db_data: Dict) -> str:
    """
    데이터베이스에서 받은 데이터 처리 (프론트엔드 형식)
    
    Args:
        db_data: {
            "session_id": "session_abc123",
            "edit_data": [
                {
                    "sequence": 1,
                    "video_url": "https://bucket.s3.amazonaws.com/video1.mp4",
                    "start_seek": 0,
                    "end_seek": 15,
                    "duration": 15
                },
                ...
            ]
        }
    """
    editor = VideoSyncEditor()
    
    # edit_data에서 사용되는 모든 고유한 영상 URL 추출
    edit_data = db_data.get('edit_data', [])
    if not edit_data:
        raise ValueError("edit_data가 비어있습니다.")
    
    unique_video_urls = list(set([edit['video_url'] for edit in edit_data]))
    
    print(f"세션 ID: {db_data.get('session_id', 'N/A')}")
    print(f"편집할 영상 수: {len(unique_video_urls)}")
    print(f"총 클립 수: {len(edit_data)}")
    
    # 가장 긴 영상 찾기 (동기화 기준)
    print("\n가장 긴 영상 찾는 중...")
    longest_video = editor.find_longest_video(unique_video_urls)
    
    print(f"\n동기화 기준 영상:")
    print(f"  URL: {longest_video['url']}")
    print(f"  길이: {longest_video['metadata']['duration']:.2f}초")
    print(f"  해상도: {longest_video['metadata']['width']}x{longest_video['metadata']['height']}")
    print(f"  생성 시간: {longest_video['metadata'].get('creation_time', 'N/A')}")
    print()
    
    # 편집 시퀀스 처리
    output_video = editor.process_edit_sequence(edit_data, longest_video)
    
    # 임시 파일 정리 (옵션)
    # editor.cleanup_temp_files()
    
    return output_video


# 사용 예시
if __name__ == "__main__":
    # 프론트엔드에서 받는 JSON 데이터 형식
    sample_db_data = {
        "session_id": "session_abc123",
        "edit_data": [
            {
                "sequence": 1,
                "video_url": "/path/to/video1.mp4",
                "start_seek": 0,
                "end_seek": 15,
                "duration": 15
            },
            {
                "sequence": 2,
                "video_url": "/path/to/video2.mp4",
                "start_seek": 10,
                "end_seek": 25,
                "duration": 15
            },
            {
                "sequence": 3,
                "video_url": "/path/to/video1.mp4",
                "start_seek": 30,
                "end_seek": 45,
                "duration": 15
            }
        ]
    }
    
    try:
        # 처리 실행
        output_file = process_from_database(sample_db_data)
        print(f"\n최종 영상이 생성되었습니다: {output_file}")
    except Exception as e:
        print(f"오류 발생: {e}")
        import traceback
        traceback.print_exc()
