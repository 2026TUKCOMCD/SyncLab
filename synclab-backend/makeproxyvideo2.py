import os
import boto3
import uvicorn
import static_ffmpeg
static_ffmpeg.add_paths()
import ffmpeg
import random
import string
from fastapi import FastAPI, HTTPException, BackgroundTasks
from botocore.config import Config
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional
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

# S3 및 경로 설정
S3_BUCKET_ORIGINAL = "synclab-1080p-mp4"
S3_BUCKET_PROXY = "synclab-480p-mp4"
REGION_NAME = "ap-northeast-2"
TEMP_DIR = "/tmp/video_processing"
os.makedirs(TEMP_DIR, exist_ok=True)

s3_client = boto3.client(
    's3',
    region_name=REGION_NAME,
    aws_access_key_id='', 
    aws_secret_access_key='',
    config=Config(signature_version='s3v4')
)

# 임시 메모리 DB
fake_db = {
    "current_session": None,
    "history": [
        {"sessionId": "HIST-001", "sessionName": "1차 필드 테스트", "createdAt": "2026-01-20", "participantCount": 2},
        {"sessionId": "HIST-002", "sessionName": "연구실 실내 측정", "createdAt": "2026-01-22", "participantCount": 5}
    ],
    "videos": {}
}

# ============================================
# 데이터 모델
# ============================================
class LoginRequest(BaseModel):
    userId: str
    userPw: str

class SessionCreateRequest(BaseModel):
    session_id: Optional[str] = None  
    user_pk: int

class SessionJoinRequest(BaseModel):
    invite_code: str
    user_pk: Optional[int] = None

class VideoMetadata(BaseModel):
    videoName: str
    fileName: str
    absoluteStartTime: int
    absoluteEndTime: int
    duration: float
    sessionId: str

class CompleteUploadRequest(BaseModel):
    uploadId: str
    videoName: str
    etags: List[str]
    metadata: VideoMetadata

# ============================================
# 유틸리티 및 백그라운드 작업
# ============================================
def generate_invite_code():
    return ''.join(random.choices(string.ascii_uppercase + string.digits, k=8))

async def create_proxy_video(original_key: str):
    # original_key 예: "701/SyncLab_...mp4"
    try:
        session_id = original_key.split('/')[0]
        filename = original_key.split('/')[-1]
    except Exception:
        print(f"❌ 경로 파싱 실패: {original_key}")
        return

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    # ✅ TEMP_DIR가 존재하는지 다시 확인하고 절대 경로로 생성
    original_local_path = os.path.join(TEMP_DIR, f"{timestamp}_original.mp4")
    proxy_local_path = os.path.join(TEMP_DIR, f"{timestamp}_proxy.mp4")
    
    # S3에서 프록시 버킷에 저장할 경로 (701/filename_proxy.mp4)
    proxy_s3_key = original_key.replace(".mp4", "_proxy.mp4")
    
    try:
        # 1. 원본 다운로드
        s3_client.download_file(S3_BUCKET_ORIGINAL, original_key, original_local_path)
        
        # 2. FFmpeg 변환
        # ✅ proxy_local_path가 확실히 문자열로 전달되어야 합니다.
        stream = ffmpeg.input(original_local_path)
        stream = ffmpeg.output(
            stream, 
            proxy_local_path,  # 👈 여기가 비어있거나 None이면 "A filename must be provided" 에러 발생
            vcodec='libx264', acodec='aac',
            video_bitrate='1M', audio_bitrate='128k',
            vf='scale=854:480:force_original_aspect_ratio=decrease,pad=854:480:(ow-iw)/2:(oh-ih)/2',
            preset='fast'
        )
        
        # 실행
        ffmpeg.run(stream, overwrite_output=True)
        
        # 3. 프록시 S3 업로드
        s3_client.upload_file(
            proxy_local_path, S3_BUCKET_PROXY, proxy_s3_key,
            ExtraArgs={'ContentType': 'video/mp4'}
        )
        
        # 4. DB 상태 업데이트 (중요!)
        if session_id in fake_db["videos"]:
            for video in fake_db["videos"][session_id]:
                if video["fileName"] == filename:
                    video["status"] = "COMPLETED"
                    break
        print(f"✅ 프록시 생성 완료: {proxy_s3_key}")

    except Exception as e:
        print(f"❌ FFmpeg 변환 실패 ({original_key}): {e}")
    finally:
        # 임시 파일 삭제
        if os.path.exists(original_local_path): os.remove(original_local_path)
        if os.path.exists(proxy_local_path): os.remove(proxy_local_path)

# ============================================
# API 엔드포인트
# ============================================
@app.post("/api/mobile/auth/login")
async def login(request: LoginRequest):
    if request.userId == "111" and request.userPw == "111":
        return {
            "status": "success", 
            "userName": "테스트 관리자",
            "userId": "111",
            "userPk": 111,  # ✅ 앱의 LoginResponse 모델과 일치하도록 추가
            "currentSessionId": None,
            "lastJoinedAt": None
        }
    raise HTTPException(status_code=401, detail="인증 실패")
@app.get("/api/mobile/home/data")
async def get_home_data():
    return fake_db

@app.post("/api/mobile/session/create")
async def create_session(request: SessionCreateRequest):
    # 디버깅: 데이터가 잘 들어왔는지 서버 터미널에서 확인
    print(f"📥 수신 -> session_id: {request.session_id}, user_pk: {request.user_pk}")
    
    invite_code = generate_invite_code()
    # 앱에서 보낸 id가 있으면 쓰고, 없으면 랜덤 생성
    new_id = request.session_id if request.session_id else str(random.randint(100, 999))
    
    # 세션 이름이 따로 안 오므로 ID를 기반으로 생성
    session_name = f"Session_{new_id}"

    new_session = {
        "sessionId": new_id,
        "sessionName": session_name,
        "createdAt": datetime.now().strftime("%Y-%m-%d"),
        "participantCount": 1,
        "connectCode": invite_code,
        "owner_pk": request.user_pk
    }
    
    # fake_db 업데이트
    fake_db["history"].append(new_session)
    
    return {
        "status": "success",
        "session": new_session,
        "temp_code": invite_code,
        "expires_in": 600
    }

@app.get("/api/mobile/video/upload/init")
def init_upload(filename: str, partCount: int, sessionId: str = "default"):
    s3_key = f"{sessionId}/{filename}"
    response = s3_client.create_multipart_upload(
        Bucket=S3_BUCKET_ORIGINAL, Key=s3_key, ContentType='video/mp4'
    )
    upload_id = response['UploadId']

    presigned_urls = [
        s3_client.generate_presigned_url(
            ClientMethod='upload_part',
            Params={'Bucket': S3_BUCKET_ORIGINAL, 'Key': s3_key, 'UploadId': upload_id, 'PartNumber': i},
            ExpiresIn=3600
        ) for i in range(1, partCount + 1)
    ]
        
    return {"uploadId": upload_id, "presignedUrls": presigned_urls, "s3Key": s3_key}

@app.post("/api/mobile/video/upload/complete")
async def complete_upload(request: CompleteUploadRequest, background_tasks: BackgroundTasks):
    try:
        # 1. 세션 ID 가져오기 (폴더명으로 사용)
        session_id = request.metadata.sessionId
        file_name = request.metadata.fileName
        
        # 2. S3 통합 경로 설정 (sessionId/filename.mp4)
        # 만약 앱에서 이미 fullPath를 sessionId/name 형태로 보낸다면 request.videoName을 그대로 써도 되지만,
        # 안전하게 여기서 직접 조합하는 것이 확실합니다.
        full_s3_key = f"{session_id}/{file_name}"
        
        # 3. S3 멀티파트 업로드 완료 처리
        parts = [{"PartNumber": i + 1, "ETag": etag} for i, etag in enumerate(request.etags)]
        s3_client.complete_multipart_upload(
            Bucket=S3_BUCKET_ORIGINAL, 
            Key=full_s3_key,  # ✅ 수정: 통합된 경로 사용
            UploadId=request.uploadId, 
            MultipartUpload={'Parts': parts}
        )
        
        # 4. DB 객체 생성
        new_video = {
            "videoId": f"VID-{datetime.now().strftime('%M%S')}",
            "fileName": file_name,
            "fullPath": full_s3_key,  # ✅ 수정: 전체 경로 저장
            "status": "PROCESSING",   # 프록시 생성 중 상태
            "timestamp": int(datetime.now().timestamp()),
            "duration": request.metadata.duration
        }
        
        # 5. 메모리 DB 저장
        if session_id not in fake_db["videos"]:
            fake_db["videos"][session_id] = []
        fake_db["videos"][session_id].insert(0, new_video)
        
        # 6. 백그라운드 작업 시작 (프록시 생성)
        # 전체 경로(full_s3_key)를 넘겨줘야 FFmpeg가 파일을 찾을 수 있습니다.
        background_tasks.add_task(create_proxy_video, full_s3_key)
        
        return {"status": "success", "s3_path": full_s3_key}

    except Exception as e:
        print(f"❌ 업로드 완료 처리 중 에러 발생: {e}")
        raise HTTPException(status_code=500, detail=str(e))
    
@app.get("/api/mobile/video/proxy/check/{sessionId}/{filename}")
async def check_proxy_exists(sessionId: str, filename: str):
    proxy_key = f"{sessionId}/{filename}".replace(".mp4", "_proxy.mp4")
    try:
        response = s3_client.head_object(Bucket=S3_BUCKET_PROXY, Key=proxy_key)
        return {
            "status": "completed",
            "proxy_url": f"https://{S3_BUCKET_PROXY}.s3.{REGION_NAME}.amazonaws.com/{proxy_key}",
            "file_size_mb": round(response['ContentLength'] / (1024 * 1024), 2)
        }
    except:
        return {"status": "not_found"}

@app.get("/api/mobile/video/list/{sessionId}")
async def list_session_videos(sessionId: str):
    return {"sessionId": sessionId, "videos": fake_db["videos"].get(sessionId, [])}

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8002)
