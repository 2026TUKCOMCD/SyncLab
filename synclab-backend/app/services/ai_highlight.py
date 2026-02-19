import librosa
import numpy as np
from typing import List, Dict
import tempfile
import os
import requests
from scipy.signal import find_peaks

class AudioAnalyzer:
    """
    오디오 기반 하이라이트 분석기
    """
    
    def __init__(self):
        self.sample_rate = 22050
        
    def download_video(self, video_url: str) -> str:
        """
        비디오 다운로드 (필요시)
        """
        # URL이 로컬 경로면 그대로 반환
        if os.path.exists(video_url):
            return video_url
        
        # 원격 URL이면 다운로드
        temp_file = tempfile.NamedTemporaryFile(delete=False, suffix='.mp4')
        response = requests.get(video_url, stream=True)
        
        for chunk in response.iter_content(chunk_size=8192):
            temp_file.write(chunk)
        
        temp_file.close()
        return temp_file.name
    
    def extract_audio_features(self, audio_path: str) -> tuple:
        """
        오디오에서 특징 추출
        """
        # 오디오 로드
        y, sr = librosa.load(audio_path, sr=self.sample_rate)
        
        # RMS (볼륨) 계산
        rms = librosa.feature.rms(y=y, frame_length=2048, hop_length=512)[0]
        
        # Spectral Centroid (음색 밝기)
        spectral_centroid = librosa.feature.spectral_centroid(y=y, sr=sr)[0]
        
        # Zero Crossing Rate (음성/소음 구분)
        zcr = librosa.feature.zero_crossing_rate(y)[0]
        
        return rms, spectral_centroid, zcr, sr
    
    def find_audio_peaks(self, rms: np.ndarray, sr: int, sensitivity: float) -> List[int]:
        """
        오디오 피크 지점 찾기
        """
        # 임계값 계산
        threshold = np.percentile(rms, sensitivity * 100)
        
        # 피크 찾기 (최소 간격 5초)
        peaks, properties = find_peaks(
            rms,
            height=threshold,
            distance=int(5 * sr / 512)  # 5초 간격
        )
        
        return peaks
    
    def analyze_video(self, video_url: str, duration: float, sensitivity: float = 0.9) -> List[Dict]:
        """
        비디오 분석 메인 함수
        """
        try:
            # 1. 비디오 다운로드
            video_path = self.download_video(video_url)
            
            # 2. 오디오 특징 추출
            rms, spectral_centroid, zcr, sr = self.extract_audio_features(video_path)
            
            # 3. 피크 찾기
            peaks = self.find_audio_peaks(rms, sr, sensitivity)
            
            # 4. 하이라이트 생성
            highlights = []
            for idx, peak in enumerate(peaks):
                timestamp = (peak * 512) / sr  # 초 단위로 변환
                
                # 피크 강도 계산
                peak_value = rms[peak]
                max_value = np.max(rms)
                confidence = int((peak_value / max_value) * 100)
                
                # 하이라이트 구간 설정 (전후 2.5초)
                start = max(0, timestamp - 2.5)
                end = min(duration, timestamp + 2.5)
                
                highlights.append({
                    "id": f"highlight_{idx}",
                    "timestamp": round(timestamp, 2),
                    "start": round(start, 2),
                    "end": round(end, 2),
                    "confidence": confidence,
                    "type": "audio_peak"
                })
            
            # 5. 신뢰도 순으로 정렬
            highlights.sort(key=lambda x: x['confidence'], reverse=True)
            
            # 6. 상위 15개만 반환
            return highlights[:15]
            
        except Exception as e:
            print(f"Error in analyze_video: {e}")
            return []
        
        finally:
            # 임시 파일 삭제
            if video_path != video_url and os.path.exists(video_path):
                os.remove(video_path)

# ========== 테스트 코드 ==========
if __name__ == "__main__":
    analyzer = AudioAnalyzer()
    
    # 테스트 실행
    test_video = "path/to/test_video.mp4"
    highlights = analyzer.analyze_video(test_video, duration=180, sensitivity=0.9)
    
    print(f"Found {len(highlights)} highlights:")
    for h in highlights:
        print(f"  - {h['timestamp']}s: {h['type']} (confidence: {h['confidence']}%)")