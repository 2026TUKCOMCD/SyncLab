from pydantic import BaseModel, Field
from typing import Optional, List, Dict, Any
from datetime import datetime
from enum import Enum


# ============================================
# Enum 정의
# ============================================

class VideoStatus(str, Enum):
    """영상 처리 상태"""
    PENDING = "pending"
    UPLOADED = "uploaded"
    PROCESSING = "processing"
    PROXY_READY = "proxy_ready"
    READY = "ready"
    ERROR = "error"


# ============================================
# 인증 관련 (웹 & 모바일)
# ============================================

class Usercreate(BaseModel):  # web_auth.py에서 사용
    id: str
    password: str
    user_name: str

class Userlogin(BaseModel):   # web_auth.py에서 사용
    id: str
    password: str

class UserCreate(BaseModel):
    """회원가입 요청"""
    id: str
    password: str
    user_name: str


class UserLogin(BaseModel):
    """로그인 요청"""
    id: str
    password: str


class LoginResponse(BaseModel):
    """로그인 결과"""
    status: str
    message: str
    user_name: str       # userName -> user_name
    access_token: Optional[str] = None


class GoogleLoginRequest(BaseModel):
    """Google 소셜 로그인 요청"""
    id_token: str


class KakaoLoginRequest(BaseModel):
    """Kakao 소셜 로그인 요청"""
    access_token: str


class SendCodeRequest(BaseModel):
    """이메일 인증 코드 발송 요청"""
    email: str


class VerifyCodeRequest(BaseModel):
    """이메일 인증 코드 검증 요청"""
    email: str
    code: str


class SignupRequest(BaseModel):
    """이메일 회원가입 요청"""
    email: str
    password: str
    user_name: str


class UpdateNameRequest(BaseModel):
    """닉네임 변경 요청"""
    user_name: str


# ============================================
# 세션 관련 (모바일)
# ============================================

class SessionInfo(BaseModel):
    """세션 상세 정보"""
    session_id: str             # sessionId -> session_id
    session_name: str           # sessionName -> session_name
    created_at: str             # createdAt -> created_at
    participant_count: int      # participantCount -> participant_count
    connect_code: Optional[str] = None # connectCode -> connect_code
    expires_at: Optional[int] = None   # expiresAt -> expires_at


class SessionActionRequest(BaseModel):
    """세션 생성/참가 요청"""
    user_pk: Optional[int] = None # 토큰 인증 시 선택 사항
    name: Optional[str] = None


class SessionResponse(BaseModel):
    """세션 결과"""
    status: str
    session: SessionInfo        # Dict[str, Any]에서 SessionInfo로 구체화
    temp_code: Optional[str] = None
    expires_in: Optional[int] = None


class VerifyCodeResponse(BaseModel):
    """코드 검증 결과"""
    status: str
    session_id: str             # session_id (기존 유지)


# ============================================
# 홈 데이터
# ============================================

class HomeDataResponse(BaseModel):
    """홈 데이터"""
    current_session: Optional[SessionInfo] = None # Dict에서 SessionInfo로 변경 가능
    history: List[Dict[str, Any]]
    videos: Optional[Dict[str, List[Any]]] = None


# ============================================
# 영상 업로드 관련 (모바일)
# ============================================

class VideoMetadata(BaseModel):
    """영상 메타데이터"""
    file_name: str              # fileName -> file_name
    absolute_start_time: int    # absoluteStartTime -> absolute_start_time
    absolute_end_time: int      # absoluteEndTime -> absolute_end_time
    duration: float
    orientation: str = "portrait"  # 촬영 방향 (portrait / landscape)
    rotation: int = 0              # 실제 회전 각도: 0, 90, 180, 270


class CompleteUploadRequest(BaseModel):
    """업로드 완료 요청"""
    session_id: str             # sessionId -> session_id
    upload_id: str              # uploadId -> upload_id
    video_name: str             # videoName -> video_name
    etags: List[str]
    metadata: VideoMetadata


class UploadInitResponse(BaseModel):
    """업로드 초기화 응답"""
    upload_id: str              # uploadId -> upload_id
    presigned_urls: List[str]   # presignedUrls -> presigned_urls
    s3_key: str                 # s3Key -> s3_key


class CompleteUploadResponse(BaseModel):
    """업로드 완료 응답"""
    status: str
    message: str
    s3_path: str
    video_id: Optional[int] = None


# ============================================
# 프록시 체크
# ============================================

class ProxyCheckResponse(BaseModel):
    """프록시 체크 응답"""
    status: str
    ready: Optional[bool] = None
    proxy_url: Optional[str] = None
    file_size_mb: Optional[float] = None


# ============================================
# 기존 모델 (호환성 유지 및 Snake Case 변환)
# ============================================

class PresignedUrlRequest(BaseModel):
    """Presigned URL 요청"""
    session_id: str = Field(..., min_length=1, max_length=100) # sessionId -> session_id
    camera_id: int = Field(..., ge=1)                         # cameraId -> camera_id
    file_name: str = Field(..., min_length=1)                 # fileName -> file_name
    ntp_start_time: Optional[int] = None                      # ntpStartTime -> ntp_start_time
    ntp_end_time: Optional[int] = None                        # ntpEndTime -> ntp_end_time


class PresignedUrlResponse(BaseModel):
    """Presigned URL 응답"""
    success: bool
    upload_url: str             # uploadUrl -> upload_url
    video_id: int               # videoId -> video_id
    s3_key: str
    expires_in: int             # expiresIn -> expires_in
    bucket: str


class VideoInfo(BaseModel):
    """영상 정보"""
    id: int
    session_id: str
    camera_id: int
    s3_key: str
    status: VideoStatus
    duration: Optional[float] = None
    resolution: Optional[str] = None
    fps: Optional[int] = None
    created_at: datetime


class ProxyRequest(BaseModel):
    """프록시 생성 요청"""
    video_id: int
    target_resolution: str = "1280x720"
    crf: int = Field(default=23, ge=0, le=51)

# ============================================
# 편집 관련 (웹)
# ============================================

# 클립 한개 데이터
class ClipData(BaseModel):
    sequence: int
    video_url: str
    start_seek: float
    end_seek: float
    duration: float
    slow_rate: Optional[float] = 1.0

# 편집 전체 데이터
class SavedEditRequest(BaseModel):
    session_id: str
    edit_data: List[ClipData]


# ============================================
# AI 하이라이트 자동 생성 (농구 득점 장면 감지)
# ============================================

class AIHighlightRequest(BaseModel):
    """AI 하이라이트 분석 요청"""
    session_id: str
    camera_index: int = 0  # 분석 대상 카메라 인덱스 (기본: 첫 번째 카메라)
    sensitivity: float = Field(default=0.85, ge=0.0, le=1.0)
