import boto3
import uvicorn
import ffmpeg
import os
from fastapi import FastAPI, HTTPException, BackgroundTasks
from botocore.config import Config
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List
from datetime import datetime

app = FastAPI()

# CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# S3 설정
S3_BUCKET_ORIGINAL = "synclab-1080p-mp4"  # 원본 버킷
S3_BUCKET_PROXY = "synclab-480p-mp4"      # 프록시 버킷 (생성 필요!)
REGION_NAME = "ap-northeast-2"

# 임시 파일 저장 경로
TEMP_DIR = "/tmp/video_processing"
os.makedirs(TEMP_DIR, exist_ok=True)

s3_client = boto3.client(
    's3',
    region_name=REGION_NAME,
    aws_access_key_id='YOUR_ACCESS_KEY',      # ⚠️ 본인 키 입력
    aws_secret_access_key='YOUR_SECRET_KEY',  # ⚠️ 본인 키 입력
    config=Config(signature_version='s3v4')
)

# 데이터 모델
class VideoMetadata(BaseModel):
    videoName: str
    fileName: str
    absoluteStartTime: int
    absoluteEndTime: int
    duration: float

class CompleteUploadRequest(BaseModel):
    uploadId: str
    videoName: str
    etags: List[str]
    metadata: VideoMetadata


# ============================================
# 프록시 영상 생성 함수 (핵심 추가 부분!)
# ============================================

async def create_proxy_video(original_key: str):
    """S3에서 원본 다운로드 → FFmpeg 변환 → 프록시 업로드"""
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    original_filename = f"{TEMP_DIR}/{timestamp}_original.mp4"
    proxy_filename = f"{TEMP_DIR}/{timestamp}_proxy.mp4"
    proxy_key = original_key.replace(".mp4", "_proxy.mp4")
    
    try:
        print(f"\n{'='*60}")
        print(f"[1/4] 📥 S3에서 원본 다운로드 중...")
        print(f"      버킷: {S3_BUCKET_ORIGINAL}")
        print(f"      파일: {original_key}")
        
        # 1. S3에서 원본 다운로드
        s3_client.download_file(
            S3_BUCKET_ORIGINAL,
            original_key,
            original_filename
        )
        
        file_size_mb = os.path.getsize(original_filename) / (1024 * 1024)
        print(f"      ✅ 다운로드 완료 ({file_size_mb:.2f} MB)")
        
        print(f"\n[2/4] 🎬 FFmpeg 변환 시작 (1080p → 480p)")
        
        # 2. FFmpeg로 480p 변환
        stream = ffmpeg.input(original_filename)
        stream = ffmpeg.output(
            stream,
            proxy_filename,
            vcodec='libx264',
            acodec='aac',
            video_bitrate='1M',
            audio_bitrate='128k',
            vf='scale=854:480:force_original_aspect_ratio=decrease,pad=854:480:(ow-iw)/2:(oh-ih)/2', 
            preset='fast',
            crf=23,
            movflags='faststart'
        )
        
        ffmpeg.run(stream, overwrite_output=True, capture_stdout=True, capture_stderr=True)
        
        proxy_size_mb = os.path.getsize(proxy_filename) / (1024 * 1024)
        compression_ratio = (1 - proxy_size_mb / file_size_mb) * 100
        print(f"      ✅ 변환 완료 ({proxy_size_mb:.2f} MB, {compression_ratio:.1f}% 압축)")
        
        print(f"\n[3/4] 📤 S3에 프록시 업로드 중...")
        
        # 3. 프록시 S3 업로드
        s3_client.upload_file(
            proxy_filename,
            S3_BUCKET_PROXY,
            proxy_key,
            ExtraArgs={'ContentType': 'video/mp4'}
        )
        
        proxy_url = f"https://{S3_BUCKET_PROXY}.s3.{REGION_NAME}.amazonaws.com/{proxy_key}"
        print(f"      ✅ 업로드 완료")
        
        print(f"\n[4/4] 🗑️  임시 파일 삭제")
        os.remove(original_filename)
        os.remove(proxy_filename)
        
        print(f"      ✅ 정리 완료")
        print(f"\n🎉 프록시 영상 생성 완료!")
        print(f"   프록시 URL: {proxy_url}")
        print(f"{'='*60}\n")
        
    except ffmpeg.Error as e:
        print(f"\n❌ FFmpeg 에러: {e.stderr.decode('utf8')}")
        if os.path.exists(original_filename):
            os.remove(original_filename)
        if os.path.exists(proxy_filename):
            os.remove(proxy_filename)
            
    except Exception as e:
        print(f"\n❌ 에러: {str(e)}")
        if os.path.exists(original_filename):
            os.remove(original_filename)
        if os.path.exists(proxy_filename):
            os.remove(proxy_filename)


# ============================================
# API 엔드포인트
# ============================================

@app.get("/api/video/upload/init")
def init_upload(filename: str, partCount: int):
    """[단계 1] S3 멀티파트 업로드 시작"""
    response = s3_client.create_multipart_upload(
        Bucket=S3_BUCKET_ORIGINAL,
        Key=filename,
        ContentType='video/mp4'
    )
    upload_id = response['UploadId']

    presigned_urls = [
        s3_client.generate_presigned_url(
            ClientMethod='upload_part',
            Params={
                'Bucket': S3_BUCKET_ORIGINAL,
                'Key': filename,
                'UploadId': upload_id,
                'PartNumber': i
            },
            ExpiresIn=3600
        ) for i in range(1, partCount + 1)
    ]
        
    return {"uploadId": upload_id, "presignedUrls": presigned_urls}


@app.post("/api/video/upload/complete")
async def complete_upload(
    request: CompleteUploadRequest,
    background_tasks: BackgroundTasks  # 🔑 이게 추가됨!
):
    """[단계 2] S3 조각 병합 및 프록시 영상 생성"""
    try:
        # 1. S3 병합
        parts = [{"PartNumber": i + 1, "ETag": etag} for i, etag in enumerate(request.etags)]
        
        s3_client.complete_multipart_upload(
            Bucket=S3_BUCKET_ORIGINAL,
            Key=request.videoName,
            UploadId=request.uploadId,
            MultipartUpload={'Parts': parts}
        )
        
        print(f"\n✅ [원본 업로드 완료] {request.videoName}")
        print(f"   시작 시간: {request.metadata.absoluteStartTime}")
        print(f"   재생 길이: {request.metadata.duration}초")
        
        # 2. 백그라운드에서 프록시 생성 🔑 이게 추가됨!
        background_tasks.add_task(create_proxy_video, request.videoName)
        
        return {
            "status": "success",
            "message": "원본 업로드 완료. 프록시 생성 중...",
            "original_url": f"https://{S3_BUCKET_ORIGINAL}.s3.{REGION_NAME}.amazonaws.com/{request.videoName}"
        }

    except Exception as e:
        print(f"\n❌ [에러] {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/video/proxy/check/{filename}")
async def check_proxy_exists(filename: str):
    """프록시 영상 존재 여부 확인"""
    proxy_key = filename.replace(".mp4", "_proxy.mp4")
    
    try:
        response = s3_client.head_object(Bucket=S3_BUCKET_PROXY, Key=proxy_key)
        
        return {
            "status": "completed",
            "proxy_url": f"https://{S3_BUCKET_PROXY}.s3.{REGION_NAME}.amazonaws.com/{proxy_key}",
            "file_size_mb": round(response['ContentLength'] / (1024 * 1024), 2)
        }
    except s3_client.exceptions.NoSuchKey:
        return {"status": "not_found"}
    except Exception as e:
        return {"status": "error", "message": str(e)}


if __name__ == "__main__":
    print("🚀 SyncLab FastAPI 서버 시작!")
    print(f"   원본 버킷: {S3_BUCKET_ORIGINAL}")
    print(f"   프록시 버킷: {S3_BUCKET_PROXY}")
    print(f"   임시 저장: {TEMP_DIR}")
    print("="*60)
    uvicorn.run(app, host="0.0.0.0", port=8001)