# app/routers/mobile_video.py
"""
모바일 - 서버 | 동영상 정보 전송, Presigned URL 요청, S3 업로드, 프록시 생성 요청 API
경로: /api/mobile/
"""
import os
import boto3
import static_ffmpeg
static_ffmpeg.add_paths()
import ffmpeg
from fastapi import APIRouter, HTTPException, BackgroundTasks
from botocore.config import Config
from typing import List
from datetime import datetime
from app.database.connection import get_db_connection
from app.models.schemas import (  # ✅ schemas.py 사용
    VideoMetadata,
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
    """
    백그라운드 프록시 영상 생성
    original_key 예: "701/SyncLab_...mp4"
    video_id: DB의 video 테이블 PK
    """
    try:
        session_id = original_key.split('/')[0]
        filename = original_key.split('/')[-1]
    except Exception:
        print(f"❌ 경로 파싱 실패: {original_key}")
        return

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    original_local_path = os.path.join(TEMP_DIR, f"{timestamp}_original.mp4")
    proxy_local_path = os.path.join(TEMP_DIR, f"{timestamp}_proxy.mp4")
    
    # S3 프록시 경로: sessionId/filename_proxy.mp4
    proxy_s3_key = original_key.replace(".mp4", "_proxy.mp4")
    
    try:
        print(f"\n{'='*60}")
        print(f"[1/4] 📥 S3에서 원본 다운로드 중...")
        print(f"      파일: {original_key}")
        
        # 1. 원본 다운로드
        s3_client.download_file(S3_BUCKET_ORIGINAL, original_key, original_local_path)
        
        file_size_mb = os.path.getsize(original_local_path) / (1024 * 1024)
        print(f"      ✅ 다운로드 완료 ({file_size_mb:.2f} MB)")
        
        print(f"\n[2/4] 🎬 FFmpeg 변환 시작 (1080p → 480p)")
        
        # 2. FFmpeg 변환
        stream = ffmpeg.input(original_local_path)
        stream = ffmpeg.output(
            stream, 
            proxy_local_path,
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
        
        proxy_size_mb = os.path.getsize(proxy_local_path) / (1024 * 1024)
        compression_ratio = (1 - proxy_size_mb / file_size_mb) * 100
        print(f"      ✅ 변환 완료 ({proxy_size_mb:.2f} MB, {compression_ratio:.1f}% 압축)")
        
        print(f"\n[3/4] 📤 S3에 프록시 업로드 중...")
        print(f"      프록시 경로: {proxy_s3_key}")
        
        # 3. 프록시 S3 업로드
        s3_client.upload_file(
            proxy_local_path,
            S3_BUCKET_PROXY,
            proxy_s3_key,
            ExtraArgs={'ContentType': 'video/mp4'}
        )
        
        proxy_url = f"https://{S3_BUCKET_PROXY}.s3.{REGION_NAME}.amazonaws.com/{proxy_s3_key}"
        print(f"      ✅ 업로드 완료")
        
        print(f"\n[4/4] 💾 DB 상태 업데이트")
        
        # 4. MySQL DB 상태 업데이트
        try:
            with get_db_connection() as conn:
                cursor = conn.cursor()
                
                sql_update = """
                    UPDATE video 
                    SET upload_status = 'COMPLETED',
                        s3_url = %s
                    WHERE video_id = %s
                """
                cursor.execute(sql_update, (proxy_url, video_id))
                
                conn.commit()
                cursor.close()
                
                print(f"      ✅ DB 업데이트 완료 (video_id={video_id})")
        except Exception as db_err:
            print(f"      ⚠️  DB 업데이트 실패: {db_err}")
        
        print(f"\n🎉 프록시 영상 생성 성공!")
        print(f"{'='*60}\n")

    except Exception as e:
        print(f"\n❌ 프록시 생성 실패 ({original_key}): {e}")
        
        # 에러 발생 시 DB 상태 업데이트
        try:
            with get_db_connection() as conn:
                cursor = conn.cursor()
                cursor.execute(
                    "UPDATE video SET upload_status = 'FAILED' WHERE video_id = %s",
                    (video_id,)
                )
                conn.commit()
                cursor.close()
        except:
            pass
    finally:
        # 임시 파일 삭제
        if os.path.exists(original_local_path):
            os.remove(original_local_path)
        if os.path.exists(proxy_local_path):
            os.remove(proxy_local_path)


# ============================================
# API 엔드포인트
# ============================================

@router.get("/upload/init", response_model=UploadInitResponse)  # ✅ response_model 추가
def init_upload(filename: str, partCount: int, sessionId: str):
    """
    [1단계] Presigned URL 발급
    
    경로: GET /api/mobile/upload/init
    """
    s3_key = f"{sessionId}/{filename}"
    
    try:
        response = s3_client.create_multipart_upload(
            Bucket=S3_BUCKET_ORIGINAL,
            Key=s3_key,
            ContentType='video/mp4'
        )
        upload_id = response['UploadId']

        presigned_urls = [
            s3_client.generate_presigned_url(
                ClientMethod='upload_part',
                Params={
                    'Bucket': S3_BUCKET_ORIGINAL,
                    'Key': s3_key,
                    'UploadId': upload_id,
                    'PartNumber': i
                },
                ExpiresIn=3600
            ) for i in range(1, partCount + 1)
        ]
        
        print(f"\n📤 [업로드 초기화]")
        print(f"   세션 ID: {sessionId}")
        print(f"   파일명: {filename}")
        print(f"   S3 경로: {s3_key}")
        
        # ✅ schemas.py의 UploadInitResponse 형식
        return UploadInitResponse(
            uploadId=upload_id,
            presignedUrls=presigned_urls,
            s3Key=s3_key
        )
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"업로드 초기화 실패: {str(e)}")


@router.post("/upload/complete", response_model=CompleteUploadResponse)  # ✅ response_model 추가
async def complete_upload(request: CompleteUploadRequest, background_tasks: BackgroundTasks):
    """
    [2단계] 업로드 완료 처리 및 프록시 생성
    
    경로: POST /api/mobile/upload/complete
    """
    try:
        session_id = request.metadata.sessionId
        file_name = request.metadata.fileName
        full_s3_key = f"{session_id}/{file_name}"
        
        # S3 멀티파트 병합
        parts = [
            {"PartNumber": i + 1, "ETag": etag}
            for i, etag in enumerate(request.etags)
        ]
        
        s3_client.complete_multipart_upload(
            Bucket=S3_BUCKET_ORIGINAL,
            Key=full_s3_key,
            UploadId=request.uploadId,
            MultipartUpload={'Parts': parts}
        )
        
        print(f"\n✅ [원본 업로드 완료]")
        print(f"   경로: {full_s3_key}")
        
        # MySQL DB 저장
        original_url = f"https://{S3_BUCKET_ORIGINAL}.s3.{REGION_NAME}.amazonaws.com/{full_s3_key}"
        
        video_id = None
        try:
            with get_db_connection() as conn:
                cursor = conn.cursor()
                
                sql = """
                    INSERT INTO video (
                        session_session_id, s3_url, video_name,
                        upload_status, absoulte_start_time, absoulte_end_time, duration
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s)
                """
                
                cursor.execute(sql, (
                    int(session_id),
                    original_url,
                    request.metadata.videoName,
                    'PROCESSING',
                    request.metadata.absoluteStartTime,
                    request.metadata.absoluteEndTime,
                    request.metadata.duration
                ))
                
                video_id = cursor.lastrowid
                conn.commit()
                cursor.close()
                
                print(f"   ✅ DB 저장 완료 (video_id={video_id})")
        except Exception as db_err:
            print(f"   ⚠️  DB 저장 실패: {db_err}")
        
        # 백그라운드 프록시 생성
        if video_id:
            background_tasks.add_task(create_proxy_video, full_s3_key, video_id)
        
        # ✅ schemas.py의 CompleteUploadResponse 형식
        return CompleteUploadResponse(
            status="success",
            message="원본 업로드 완료. 프록시 생성 중...",
            s3_path=full_s3_key,
            video_id=video_id
        )

    except Exception as e:
        print(f"\n❌ [에러] {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/proxy/check/{sessionId}/{filename}", response_model=ProxyCheckResponse)  # ✅ response_model
async def check_proxy_exists(sessionId: str, filename: str):
    """
    [3단계] 프록시 영상 존재 여부 확인
    
    경로: GET /api/mobile/proxy/check/{sessionId}/{filename}
    """
    original_key = f"{sessionId}/{filename}"
    proxy_key = original_key.replace(".mp4", "_proxy.mp4")
    
    print(f"\n🔍 [프록시 확인] {proxy_key}")
    
    try:
        response = s3_client.head_object(Bucket=S3_BUCKET_PROXY, Key=proxy_key)
        
        # ✅ schemas.py의 ProxyCheckResponse 형식
        return ProxyCheckResponse(
            status="completed",
            ready=True,
            proxy_url=f"https://{S3_BUCKET_PROXY}.s3.{REGION_NAME}.amazonaws.com/{proxy_key}",
            file_size_mb=round(response['ContentLength'] / (1024 * 1024), 2)
        )
    except s3_client.exceptions.NoSuchKey:
        return ProxyCheckResponse(status="not_found", ready=False)
    except Exception as e:
        return ProxyCheckResponse(status="error", ready=False)


@router.get("/list/{sessionId}")
async def list_session_videos(sessionId: str):
    """
    [4단계] 세션별 영상 목록 조회
    
    경로: GET /api/mobile/list/{sessionId}
    """
    try:
        with get_db_connection() as conn:
            cursor = conn.cursor(dictionary=True)
            
            sql = """
                SELECT 
                    video_id, video_name, s3_url, upload_status,
                    duration, absoulte_start_time, absoulte_end_time
                FROM video
                WHERE session_session_id = %s
                ORDER BY video_id DESC
            """
            
            cursor.execute(sql, (int(sessionId),))
            videos = cursor.fetchall()
            cursor.close()
        
        return {
            "sessionId": sessionId,
            "videos": videos,
            "count": len(videos)
        }
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"영상 목록 조회 실패: {str(e)}")