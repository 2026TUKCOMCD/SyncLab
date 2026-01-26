import uvicorn
import boto3
import string
import random
from fastapi import FastAPI, HTTPException, Background_Tasks
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

s3_client = boto3.client(
    's3',
    region_name=REGION_NAME,
    aws_access_key_id='',      # 본인 키 입력
    aws_secret_access_key='',  # 본인 키 입력
    config=Config(signature_version='s3v4')
)

# --- [임시 테스트 데이터베이스] ---
fake_db = {
    "current_session": None, # 현재 참여 중인 세션 (초기엔 없음)
    "history": [
        {"sessionId": "HIST-001", "sessionName": "1차 필드 테스트", "createdAt": "2026-01-20", "participantCount": 2},
        {"sessionId": "HIST-002", "sessionName": "연구실 실내 측정", "createdAt": "2026-01-22", "participantCount": 5}
    ],
    "videos": [
        {"videoId": "VID-01", "fileName": "run_test_01.mp4", "status": "COMPLETED", "timestamp": 1706240000},
        {"videoId": "VID-02", "fileName": "sync_sample.mp4", "status": "PROCESSING", "timestamp": 1706245000}
    ]
}

# --- [데이터 모델 정의] ---
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

class LoginRequest(BaseModel):
    userId: str
    userPw: str

class SessionActionRequest(BaseModel):
    name: Optional[str] = None
    sessionId: Optional[str] = None

# --- [API 엔드포인트: 인증] ---
@app.post("/api/auth/login")
async def login(request: LoginRequest):
    if request.userId == "111" and request.userPw == "111":
        return {
            "status": "success",
            "message": "로그인 성공",
            "userName": "테스트 관리자"
        }
    raise HTTPException(status_code=401, detail="인증 실패")

# --- [API 엔드포인트: 홈 & 세션] ---
@app.get("/api/home/data")
async def get_home_data():
    """홈 화면 초기 데이터 로드 (현재 세션 + 기록 + 영상 상태)"""
    return fake_db

@app.post("/api/session/create")
async def create_session(request: SessionActionRequest):
    """세션 생성 시뮬레이션"""
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
    input_code = request.sessionId
    joined_session = {
        "sessionId": input_code or "SESS-JOINED",
        "sessionName": "참가된 협업 세션",
        "createdAt": datetime.now().strftime("%Y-%m-%d"),
        "participantCount": 4
    }
    fake_db["current_session"] = joined_session
    # 생성(create)과 동일하게 "session" 키 안에 담아서 반환
    return {"status": "success", "session": joined_session} 
       
# --- [API 엔드포인트: 영상 업로드 & 상태] ---
@app.get("/api/video/upload/init")
def init_upload(filename: str, partCount: int):
    response = s3_client.create_multipart_upload(Bucket=S3_BUCKET_NAME, Key=filename, ContentType='video/mp4')
    upload_id = response['UploadId']
    presigned_urls = [
        s3_client.generate_presigned_url(
            ClientMethod='upload_part',
            Params={'Bucket': S3_BUCKET_NAME, 'Key': filename, 'UploadId': upload_id, 'PartNumber': i}
        ) for i in range(1, partCount + 1)
    ]
    return {"uploadId": upload_id, "presignedUrls": presigned_urls}

@app.post("/api/video/upload/complete")
async def complete_upload(request: CompleteUploadRequest):
    # 업로드 완료 시 PROCESSING 상태로 리스트에 추가
    new_video = {
        "videoId": f"VID-{datetime.now().strftime('%M%S')}",
        "fileName": request.videoName,
        "status": "PROCESSING",
        "timestamp": int(datetime.now().timestamp())
    }
    fake_db["videos"].insert(0, new_video) # 최신 영상을 맨 앞으로
    print(f"[SUCCESS] {request.videoName} 업로드 완료 및 처리 시작")
    return {"status": "success"}

@app.get("/api/video/status")
async def get_video_status():
    """폴링용: 호출될 때마다 PROCESSING인 영상을 확률적으로 COMPLETED로 변경 (테스트용)"""
    for video in fake_db["videos"]:
        if video["status"] == "PROCESSING":
            video["status"] = "COMPLETED" # 테스트 편의상 즉시 완료 처리
    return fake_db["videos"]
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    // ... 생략
) {
    // 팝업 상태 관리
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinCodeInput by remember { mutableStateOf("") }

    // --- 1. 세션 생성 확인 팝업 ---
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("세션 생성") },
            text = { Text("새로운 세션을 생성하시겠습니까?\n생성 시 8자리의 참가 코드가 발급됩니다.") },
            confirmButton = {
                Button(onClick = {
                    showCreateDialog = false
                    viewModel.createSession("나의 새 세션") // ViewModel에서 서버 호출
                }) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("취소") }
            }
        )
    }

    // --- 2. 세션 참가 코드 입력 팝업 ---
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("세션 참가") },
            text = {
                Column {
                    Text("공유받은 8자리 코드를 입력하세요.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = joinCodeInput,
                        onValueChange = { if (it.length <= 8) joinCodeInput = it.uppercase() },
                        label = { Text("참가 코드") },
                        placeholder = { Text("예: A1B2C3D4") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = joinCodeInput.length == 8,
                    onClick = {
                        showJoinDialog = false
                        viewModel.joinSession(joinCodeInput)
                        joinCodeInput = ""
                    }
                ) { Text("참가하기") }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) { Text("취소") }
            }
        )
    }

    // 기존 UI 코드의 버튼 클릭 이벤트 연결
    // onCreateSession = { showCreateDialog = true }
    // onJoinSession = { showJoinDialog = true }
}
if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)