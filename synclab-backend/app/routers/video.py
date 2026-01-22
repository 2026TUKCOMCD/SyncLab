from fastapi import APIRouter, HTTPException, BackgroundTasks
from app.models.schemas import (
    PresignedUrlRequest, 
    PresignedUrlResponse,
    ProxyRequest,
    VideoInfo
)
from app.database.connection import get_db_connection
from app.services.s3_service import s3_service
from app.services.ffmpeg_service import ffmpeg_service
from datetime import datetime
import os

router = APIRouter(prefix="/api/video", tags=["video"])

@router.post("/presigned-url", response_model=PresignedUrlResponse)
async def create_presigned_url(request: PresignedUrlRequest):
    """Presigned URL 발급"""
    
    timestamp = int(datetime.now().timestamp() * 1000)
    s3_key = f"{request.sessionId}/{timestamp}_{request.fileName}"
    
    try:
        # 1. DB에 비디오 정보 저장
        with get_db_connection() as conn:
            cursor = conn.cursor()
            sql = """
                INSERT INTO videos 
                (session_id, camera_id, s3_key, original_filename, ntp_start_time, ntp_end_time, status)
                VALUES (%s, %s, %s, %s, %s, %s, 'pending')
            """
            cursor.execute(sql, (
                request.sessionId,
                request.cameraId,
                s3_key,
                request.fileName,
                request.ntpStartTime,
                request.ntpEndTime
            ))
            video_id = cursor.lastrowid
            cursor.close()
        
        # 2. S3 Presigned URL 생성
        upload_url = s3_service.generate_presigned_url(
            s3_key, 
            expires_in=int(os.getenv('PRESIGNED_URL_EXPIRATION', 300))
        )
        
        return PresignedUrlResponse(
            success=True,
            uploadUrl=upload_url,
            videoId=video_id,
            s3Key=s3_key,
            expiresIn=300,
            bucket=s3_service.bucket_name
        )
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/{video_id}", response_model=VideoInfo)
async def get_video(video_id: int):
    """비디오 정보 조회"""
    try:
        with get_db_connection() as conn:
            cursor = conn.cursor(dictionary=True)
            cursor.execute("SELECT * FROM videos WHERE id = %s", (video_id,))
            video = cursor.fetchone()
            cursor.close()
            
            if not video:
                raise HTTPException(status_code=404, detail="비디오를 찾을 수 없습니다")
            
            return video
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/proxy")
async def create_proxy_video(request: ProxyRequest, background_tasks: BackgroundTasks):
    """프록시 영상 생성 요청"""
    
    # 백그라운드 작업으로 처리
    background_tasks.add_task(
        process_proxy,
        request.video_id,
        request.target_resolution,
        request.crf
    )
    
    return {
        "success": True,
        "message": "프록시 생성 작업이 시작되었습니다",
        "video_id": request.video_id
    }

async def process_proxy(video_id: int, resolution: str, crf: int):
    """프록시 생성 백그라운드 작업"""
    try:
        # 1. DB에서 비디오 정보 가져오기
        with get_db_connection() as conn:
            cursor = conn.cursor(dictionary=True)
            cursor.execute("SELECT * FROM videos WHERE id = %s", (video_id,))
            video = cursor.fetchone()
            cursor.close()
            
            if not video:
                raise Exception("비디오를 찾을 수 없습니다")
        
        # 2. S3에서 다운로드 (또는 로컬 파일 사용)
        input_path = f"./uploads/original/{video['original_filename']}"
        output_path = f"./uploads/proxy/proxy_{video_id}.mp4"
        
        # 3. 상태 업데이트: processing
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "UPDATE videos SET status = 'processing' WHERE id = %s",
                (video_id,)
            )
            cursor.close()
        
        # 4. 프록시 생성
        proxy_path = ffmpeg_service.create_proxy(
            input_path,
            output_path,
            resolution,
            crf
        )
        
        # 5. 비디오 메타데이터 추출
        info = ffmpeg_service.get_video_info(output_path)
        
        # 6. DB 업데이트: proxy_ready
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                UPDATE videos 
                SET status = 'proxy_ready',
                    proxy_path = %s,
                    duration = %s,
                    resolution = %s,
                    fps = %s,
                    codec = %s,
                    bitrate = %s,
                    file_size = %s
                WHERE id = %s
            """, (
                proxy_path,
                info['duration'],
                info['resolution'],
                info['fps'],
                info['codec'],
                info['bitrate'],
                info['file_size'],
                video_id
            ))
            cursor.close()
        
        print(f"✅ 프록시 생성 완료: {video_id}")
        
    except Exception as e:
        # 에러 상태로 업데이트
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "UPDATE videos SET status = 'error', error_message = %s WHERE id = %s",
                (str(e), video_id)
            )
            cursor.close()
        
        print(f"❌ 프록시 생성 실패: {video_id} - {str(e)}")