import boto3
import uvicorn
import ffmpeg
import os
from fastapi import FastAPI, HTTPException, BackgroundTasks
from botocore.config import Config
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime

app = FastAPI()

# CORS 설정 // 보안상 차단 되는 것을 허용
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# S3 설정
S3_BUCKET_ORIGINAL = "synclab-1080p-mp4"  # 원본 버킷
S3_BUCKET_PROXY = "synclab-480p-mp4"      # 프록시 버킷
REGION_NAME = "ap-northeast-2"

# 임시 파일 저장 경로 // ffmepeg 가 실제로 편집하기 위해서는 실제 파일이 필요하므로 저장해서 편집후 , 삭제
TEMP_DIR = "/tmp/video_processing"
os.makedirs(TEMP_DIR, exist_ok=True)

s3_client = boto3.client(
    's3',
    region_name=REGION_NAME,
    aws_access_key_id='키',
    aws_secret_access_key='키',
    config=Config(signature_version='s3v4')
)

# ============================================
# 임시 DB (세션 기능)
# ============================================
fake_db = {
    "current_session": None,
    "history": [
        {"sessionId": "HIST-001", "sessionName": "1차 필드 테스트", "createdAt": "2026-01-20", "participantCount": 2},
        {"sessionId": "HIST-002", "sessionName": "연구실 실내 측정", "createdAt": "2026-01-22", "participantCount": 5}
    ],
    "videos": {}  # ✅ 세션별로 영상 관리하도록 변경
}


# ============================================
# 데이터 모델 // 문자열, 숫자 등등 제대로 된 데이터 전송을 위한 사전정의
# ============================================
class LoginRequest(BaseModel):
    userId: str
    userPw: str

class SessionActionRequest(BaseModel):
    name: Optional[str] = None
    sessionId: Optional[str] = None

class VideoMetadata(BaseModel):
    videoName: str
    fileName: str
    absoluteStartTime: int
    absoluteEndTime: int
    duration: float
    sessionId: str  # ✅ 추가: 세션 ID

class CompleteUploadRequest(BaseModel):
    uploadId: str
    videoName: str  # ✅ 이제 "sessionId/filename.mp4" 형태로 들어옴
    etags: List[str]
    metadata: VideoMetadata


# ============================================
# 프록시 영상 생성 함수
# ============================================
async def create_proxy_video(original_key: str):
    """S3에서 원본 다운로드 → FFmpeg 변환 → 프록시 업로드"""
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    original_filename = f"{TEMP_DIR}/{timestamp}_original.mp4"
    proxy_filename = f"{TEMP_DIR}/{timestamp}_proxy.mp4"
    
    # ✅ 세션 폴더 구조 유지: "session1/video.mp4" → "session1/video_proxy.mp4"
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
        print(f"      프록시 경로: {proxy_key}")  # ✅ 경로 출력
        
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
# 세션 관련 API
# ============================================
@app.post("/api/auth/login")
async def login(request: LoginRequest):
    """로그인"""
    if request.userId == "111" and request.userPw == "111":
        return {"status": "success", "message": "로그인 성공", "userName": "테스트 관리자"}
    raise HTTPException(status_code=401, detail="인증 실패")


@app.get("/api/home/data")
async def get_home_data():
    """홈 데이터 조회 (현재 세션 + 히스토리)"""
    return fake_db


@app.post("/api/session/create")
async def create_session(request: SessionActionRequest):
    """새로운 세션 생성"""
    session_id = f"SESS-{datetime.now().strftime('%Y%m%d%H%M%S')}"
    
    new_session = {
        "sessionId": session_id,
        "sessionName": request.name or "새로운 세션",
        "createdAt": datetime.now().strftime("%Y-%m-%d %H:%M"),
        "participantCount": 1
    }
    fake_db["current_session"] = new_session
    
    # ✅ 세션별 비디오 목록 초기화
    fake_db["videos"][session_id] = []
    
    return {"status": "success", "session": new_session}


@app.post("/api/session/join")
async def join_session(request: SessionActionRequest):
    """기존 세션 참가"""
    joined_session = {
        "sessionId": request.sessionId or "SESS-JOINED",
        "sessionName": "참가된 협업 세션",
        "createdAt": datetime.now().strftime("%Y-%m-%d"),
        "participantCount": 4
    }
    fake_db["current_session"] = joined_session
    
    # ✅ 세션별 비디오 목록이 없으면 초기화
    if joined_session["sessionId"] not in fake_db["videos"]:
        fake_db["videos"][joined_session["sessionId"]] = []
    
    return {"status": "success", "session": joined_session}


@app.get("/api/video/status")
async def get_video_status():
    """영상 처리 상태 조회"""
    # ✅ 현재 세션의 비디오만 반환
    if fake_db["current_session"]:
        session_id = fake_db["current_session"]["sessionId"]
        videos = fake_db["videos"].get(session_id, [])
        
        for video in videos:
            if video["status"] == "PROCESSING":
                video["status"] = "COMPLETED"
        
        return videos
    
    return []


# ============================================
# 영상 업로드 API
# ============================================
@app.get("/api/video/upload/init")
def init_upload(filename: str, partCount: int, sessionId: str = "default_session"):
    """[단계 1] S3 멀티파트 업로드 시작 - 세션 폴더에 저장"""
    
    # ✅ S3 키에 세션 ID 포함: "sessionId/filename.mp4"
    s3_key = f"{sessionId}/{filename}"
    
    print(f"\n📤 [업로드 초기화]")
    print(f"   세션 ID: {sessionId}")
    print(f"   파일명: {filename}")
    print(f"   S3 경로: {s3_key}")
    
    response = s3_client.create_multipart_upload(
        Bucket=S3_BUCKET_ORIGINAL,
        Key=s3_key,  # ✅ 세션 경로 포함
        ContentType='video/mp4'
    )
    upload_id = response['UploadId']

    presigned_urls = [
        s3_client.generate_presigned_url(
            ClientMethod='upload_part',
            Params={
                'Bucket': S3_BUCKET_ORIGINAL,
                'Key': s3_key,  # ✅ 세션 경로 포함
                'UploadId': upload_id,
                'PartNumber': i
            },
            ExpiresIn=3600
        ) for i in range(1, partCount + 1)
    ]
        
    return {
        "uploadId": upload_id, 
        "presignedUrls": presigned_urls,
        "s3Key": s3_key  # ✅ 프론트엔드에 전달
    }


@app.post("/api/video/upload/complete")
async def complete_upload(
    request: CompleteUploadRequest,
    background_tasks: BackgroundTasks
):
    """[단계 2] S3 조각 병합 및 프록시 영상 생성"""
    try:
        # 1. S3 병합
        parts = [{"PartNumber": i + 1, "ETag": etag} for i, etag in enumerate(request.etags)]
        
        s3_client.complete_multipart_upload(
            Bucket=S3_BUCKET_ORIGINAL,
            Key=request.videoName,  # ✅ "sessionId/filename.mp4" 형태
            UploadId=request.uploadId,
            MultipartUpload={'Parts': parts}
        )
        
        print(f"\n✅ [원본 업로드 완료]")
        print(f"   경로: {request.videoName}")
        print(f"   세션: {request.metadata.sessionId}")
        print(f"   시작 시간: {request.metadata.absoluteStartTime}")
        print(f"   재생 길이: {request.metadata.duration}초")
        
        # 2. DB에 비디오 추가 (세션별로)
        new_video = {
            "videoId": f"VID-{datetime.now().strftime('%M%S')}",
            "fileName": request.metadata.fileName,  # 파일명만
            "fullPath": request.videoName,  # 전체 경로 (sessionId/filename.mp4)
            "status": "PROCESSING",
            "timestamp": int(datetime.now().timestamp()),
            "duration": request.metadata.duration,
            "absoluteStartTime": request.metadata.absoluteStartTime,
            "absoluteEndTime": request.metadata.absoluteEndTime
        }
        
        # ✅ 세션별 비디오 목록에 추가
        session_id = request.metadata.sessionId
        if session_id not in fake_db["videos"]:
            fake_db["videos"][session_id] = []
        
        fake_db["videos"][session_id].insert(0, new_video)
        
        # 3. 백그라운드에서 프록시 생성
        background_tasks.add_task(create_proxy_video, request.videoName)
        
        return {
            "status": "success",
            "message": "원본 업로드 완료. 프록시 생성 중...",
            "original_url": f"https://{S3_BUCKET_ORIGINAL}.s3.{REGION_NAME}.amazonaws.com/{request.videoName}",
            "s3_path": request.videoName  # ✅ 전체 경로 반환
        }

    except Exception as e:
        print(f"\n❌ [에러] {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/video/proxy/check/{sessionId}/{filename}")
async def check_proxy_exists(sessionId: str, filename: str):
    """프록시 영상 존재 여부 확인"""
    # ✅ 세션 경로 포함
    original_key = f"{sessionId}/{filename}"
    proxy_key = original_key.replace(".mp4", "_proxy.mp4")
    
    print(f"\n🔍 [프록시 확인] {proxy_key}")
    
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


# ✅ 새로운 API: 세션별 영상 목록 조회
@app.get("/api/video/list/{sessionId}")
async def list_session_videos(sessionId: str):
    """특정 세션의 모든 영상 조회"""
    videos = fake_db["videos"].get(sessionId, [])
    
    return {
        "sessionId": sessionId,
        "videos": videos,
        "count": len(videos)
    }


# ============================================
# 서버 실행
# ============================================
if __name__ == "__main__":
    print("╔════════════════════════════════════════╗")
    print("║   🚀 SyncLab FastAPI 서버 시작         ║")
    print("╚════════════════════════════════════════╝")
    print(f"   원본 버킷: {S3_BUCKET_ORIGINAL}")
    print(f"   프록시 버킷: {S3_BUCKET_PROXY}")
    print(f"   임시 저장: {TEMP_DIR}")
    print("="*60)
    uvicorn.run(app, host="0.0.0.0", port=8002)