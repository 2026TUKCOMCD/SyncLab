import uvicorn
import boto3
import string
import random
from fastapi import FastAPI, HTTPException
from botocore.config import Config
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime

app = FastAPI()

# 1. CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 2. S3 설정
S3_BUCKET_NAME = "synclab-1080p-mp4"
REGION_NAME = "ap-northeast-2"

# 🔴 중요: 여기에 실제 AWS Access Key와 Secret Key를 넣어야 S3에 올라갑니다.
s3_client = boto3.client(
    's3',
    region_name=REGION_NAME,
    aws_access_key_id='YOUR_ACCESS_KEY', 
    aws_secret_access_key='YOUR_SECRET_KEY',
    config=Config(signature_version='s3v4')
)

# --- [임시 DB] ---
fake_db = {
    "current_session": None,
    "history": [
        {"sessionId": "HIST-001", "sessionName": "1차 필드 테스트", "createdAt": "2026-01-20", "participantCount": 2},
        {"sessionId": "HIST-002", "sessionName": "연구실 실내 측정", "createdAt": "2026-01-22", "participantCount": 5}
    ],
    "videos": []
}

# --- [데이터 모델 정의] ---
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

class CompleteUploadRequest(BaseModel):
    uploadId: str
    videoName: str
    etags: List[str]
    metadata: VideoMetadata

# --- [API 엔드포인트] ---

@app.post("/api/auth/login")
async def login(request: LoginRequest):
    if request.userId == "111" and request.userPw == "111":
        return {"status": "success", "message": "로그인 성공", "userName": "테스트 관리자"}
    raise HTTPException(status_code=401, detail="인증 실패")

@app.get("/api/home/data")
async def get_home_data():
    return fake_db

@app.post("/api/session/create")
async def create_session(request: SessionActionRequest):
    new_session = {
        "sessionId": f"SESS-{datetime.now().strftime('%H%M%S')}",
        "sessionName": request.name or "새로운 세션",
        "createdAt": datetime.now().strftime("%Y-%m-%d %H:%M"),
        "participantCount": 1
    }
    fake_db["current_session"] = new_session
    return {"status": "success", "session": new_session}

@app.post("/api/session/join")
async def join_session(request: SessionActionRequest):
    joined_session = {
        "sessionId": request.sessionId or "SESS-JOINED",
        "sessionName": "참가된 협업 세션",
        "createdAt": datetime.now().strftime("%Y-%m-%d"),
        "participantCount": 4
    }
    fake_db["current_session"] = joined_session
    return {"status": "success", "session": joined_session}

@app.get("/api/video/upload/init")
def init_upload(filename: str, partCount: int):
    # S3 멀티파트 업로드 시작 및 ID 발급
    response = s3_client.create_multipart_upload(Bucket=S3_BUCKET_NAME, Key=filename, ContentType='video/mp4')
    upload_id = response['UploadId']
    
    # 각 파트별로 안드로이드가 직접 업로드할 수 있는 Presigned URL 생성
    presigned_urls = [
        s3_client.generate_presigned_url(
            ClientMethod='upload_part',
            Params={'Bucket': S3_BUCKET_NAME, 'Key': filename, 'UploadId': upload_id, 'PartNumber': i}
        ) for i in range(1, partCount + 1)
    ]
    return {"uploadId": upload_id, "presignedUrls": presigned_urls}

@app.post("/api/video/upload/complete")
async def complete_upload(request: CompleteUploadRequest):
    try:
        # 🔴 핵심: 안드로이드에서 보내준 ETag들을 기반으로 S3에 '합치기 완료' 명령
        parts = [{"ETag": etag, "PartNumber": i + 1} for i, etag in enumerate(request.etags)]
        
        s3_client.complete_multipart_upload(
            Bucket=S3_BUCKET_NAME,
            Key=request.videoName,
            UploadId=request.uploadId,
            MultipartUpload={'Parts': parts}
        )

        # DB에 처리 중 상태로 추가
        new_video = {
            "videoId": f"VID-{datetime.now().strftime('%M%S')}",
            "fileName": request.videoName,
            "status": "PROCESSING",
            "timestamp": int(datetime.now().timestamp())
        }
        fake_db["videos"].insert(0, new_video)
        
        print(f"Successfully completed upload to S3: {request.videoName}")
        return {"status": "success"}

    except Exception as e:
        print(f"Error completing upload: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/video/status")
async def get_video_status():
    for video in fake_db["videos"]:
        if video["status"] == "PROCESSING":
            video["status"] = "COMPLETED"
    return fake_db["videos"]

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)