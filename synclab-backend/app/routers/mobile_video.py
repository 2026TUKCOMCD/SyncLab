# app/routers/mobile_video.py
import os
import boto3
import static_ffmpeg
static_ffmpeg.add_paths()
import ffmpeg
from fastapi import APIRouter, HTTPException, BackgroundTasks
from botocore.config import Config
from datetime import datetime
from app.database.connection import get_db_connection
from app.models.schemas import (
    CompleteUploadRequest,
    CompleteUploadResponse,
    UploadInitResponse,
    ProxyCheckResponse
)

router = APIRouter(prefix="/api/mobile/video", tags=["Mobile-Video"])

# S3 및 경로 설정
S3_BUCKET_ORIGINAL = os.getenv("S3_BUCKET_ORIGINAL", "synclab-1080p-mp4")
S3_BUCKET_PROXY = os.getenv("S3_BUCKET_PROXY", "synclab-480p-mp4")
REGION_NAME = os.getenv("AWS_REGION", "ap-northeast-2")
TEMP_DIR = os.getenv("TEMP_DIR", "/tmp/video_processing")
os.makedirs(TEMP_DIR, exist_ok=True)

s3_client = boto3.client(
    's3',
    region_name=REGION_NAME,
    aws_access_key_id=os.getenv('AWS_ACCESS_KEY'),
    aws_secret_access_key=os.getenv('AWS_SECRET_KEY'),
    config=Config(signature_version='s3v4')
)

# ============================================
# 백그라운드 작업 (프록시 생성)
# ============================================

async def create_proxy_video(original_key: str, video_id: int):
    try:
        # original_key 예: "701/filename.mp4"
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        original_local_path = os.path.join(TEMP_DIR, f"{timestamp}_original.mp4")
        proxy_local_path = os.path.join(TEMP_DIR, f"{timestamp}_proxy.mp4")
        proxy_s3_key = original_key.replace(".mp4", "_proxy.mp4")
        
        # 1. 원본 다운로드
        s3_client.download_file(S3_BUCKET_ORIGINAL, original_key, original_local_path)
        
        # 2. FFmpeg 변환 (480p)
        stream = ffmpeg.input(original_local_path)
        stream = ffmpeg.output(
            stream, 
            proxy_local_path,
            vcodec='libx264', acodec='aac',
            video_bitrate='1M', audio_bitrate='128k',
            vf='scale=854:480:force_original_aspect_ratio=decrease,pad=854:480:(ow-iw)/2:(oh-ih)/2',
            preset='fast', crf=23, movflags='faststart'
        )
        ffmpeg.run(stream, overwrite_output=True, capture_stdout=True, capture_stderr=True)
        
        # 3. 프록시 S3 업로드
        s3_client.upload_file(
            proxy_local_path, S3_BUCKET_PROXY, proxy_s3_key,
            ExtraArgs={'ContentType': 'video/mp4'}
        )
        
        proxy_url = f"https://{S3_BUCKET_PROXY}.s3.{REGION_NAME}.amazonaws.com/{proxy_s3_key}"
        
        # 4. DB 상태 업데이트 (snake_case 필드 반영)
        with get_db_connection() as conn:
            cursor = conn.cursor()
            sql_update = """
                UPDATE video 
                SET upload_status = 'COMPLETED'
                WHERE video_id = %s
            """
            cursor.execute(sql_update, (video_id,))
            conn.commit()
            cursor.close()

    except Exception as e:
        print(f"❌ 프록시 생성 실패: {e}")
        try:
            with get_db_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("UPDATE video SET upload_status = 'FAILED' WHERE video_id = %s", (video_id,))
                conn.commit()
        except: pass
    finally:
        for p in [original_local_path, proxy_local_path]:
            if os.path.exists(p): os.remove(p)

# ============================================
# API 엔드포인트
# ============================================

@router.get("/upload/init", response_model=UploadInitResponse)
def init_upload(filename: str, part_count: int, session_id: str): # partCount -> part_count, sessionId -> session_id
    s3_key = f"{session_id}/{filename}"
    try:
        response = s3_client.create_multipart_upload(
            Bucket=S3_BUCKET_ORIGINAL, Key=s3_key, ContentType='video/mp4'
        )
        upload_id = response['UploadId']

        presigned_urls = [
            s3_client.generate_presigned_url(
                ClientMethod='upload_part',
                Params={
                    'Bucket': S3_BUCKET_ORIGINAL, 'Key': s3_key,
                    'UploadId': upload_id, 'PartNumber': i
                },
                ExpiresIn=3600
            ) for i in range(1, part_count + 1)
        ]
        
        return UploadInitResponse(
            upload_id=upload_id,
            presigned_urls=presigned_urls,
            s3_key=s3_key
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/upload/complete", response_model=CompleteUploadResponse)
async def complete_upload(request: CompleteUploadRequest, background_tasks: BackgroundTasks):
    try:
        session_id = request.session_id      # sessionId -> session_id
        file_name = request.metadata.file_name # fileName -> file_name
        full_s3_key = f"{session_id}/{file_name}"
        
        # S3 병합
        parts = [{"PartNumber": i + 1, "ETag": etag} for i, etag in enumerate(request.etags)]
        s3_client.complete_multipart_upload(
            Bucket=S3_BUCKET_ORIGINAL, Key=full_s3_key,
            UploadId=request.upload_id, MultipartUpload={'Parts': parts}
        )
        
        original_url = f"https://{S3_BUCKET_ORIGINAL}.s3.{REGION_NAME}.amazonaws.com/{full_s3_key}"
        
        # DB 저장 (DB 컬럼명은 시스템 설계에 맞게 snake_case 유지)
        with get_db_connection() as conn:
            cursor = conn.cursor()
            sql = """
                INSERT INTO video (
                    session_session_id, video_name,
                    upload_status, absolute_start_time, absolute_end_time, duration
                ) VALUES (%s, %s, %s, %s, %s, %s)
            """
            cursor.execute(sql, (
                session_id, request.video_name,
                'PROCESSING', 
                request.metadata.absolute_start_time,
                request.metadata.absolute_end_time,
                request.metadata.duration
            ))
            video_id = cursor.lastrowid
            conn.commit()
            cursor.close()
        
        if video_id:
            background_tasks.add_task(create_proxy_video, full_s3_key, video_id)
        
        return CompleteUploadResponse(
            status="success",
            message="원본 업로드 완료. 프록시 생성 중...",
            s3_path=full_s3_key,
            video_id=video_id
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/proxy/check/{session_id}/{filename}", response_model=ProxyCheckResponse)
async def check_proxy_exists(session_id: str, filename: str):
    proxy_key = f"{session_id}/{filename}".replace(".mp4", "_proxy.mp4")
    try:
        response = s3_client.head_object(Bucket=S3_BUCKET_PROXY, Key=proxy_key)
        return ProxyCheckResponse(
            status="completed", ready=True,
            proxy_url=f"https://{S3_BUCKET_PROXY}.s3.{REGION_NAME}.amazonaws.com/{proxy_key}",
            file_size_mb=round(response['ContentLength'] / (1024 * 1024), 2)
        )
    except s3_client.exceptions.NoSuchKey:
        return ProxyCheckResponse(status="not_found", ready=False)
    except Exception:
        return ProxyCheckResponse(status="error", ready=False)

@router.get("/list/{session_id}")
async def list_session_videos(session_id: str):
    try:
        with get_db_connection() as conn:
            cursor = conn.cursor(dictionary=True)
            sql = """
                SELECT 
                    video_id, video_name, upload_status,
                    duration, absolute_start_time, absolute_end_time
                FROM video
                WHERE session_session_id = %s
                ORDER BY video_id DESC
            """
            cursor.execute(sql, (int(session_id),))
            videos = cursor.fetchall()
            cursor.close()
        
        return {
            "session_id": session_id,
            "videos": videos,
            "count": len(videos)
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))