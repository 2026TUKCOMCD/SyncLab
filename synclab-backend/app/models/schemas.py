
# app/models/schemas.py
"""
모든 Pydantic 모델 정의 (API 명세 기반)
"""
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
# 인증 관련 (웹)
# ============================================

class Usercreate(BaseModel):
    """회원가입 요청"""
    id: str
    password: str
    user_name: str


class Userlogin(BaseModel):
    """로그인 요청"""
    id: str
    password: str


class LoginResponse(BaseModel):
    """로그인 결과"""
    status: str
    message: str
    userName: str


# ============================================
# 세션 관련 (모바일)
# ============================================

class SessionActionRequest(BaseModel):
    """세션 생성/참가 요청"""
    name: Optional[str] = None


class SessionResponse(BaseModel):
    """세션 결과"""
    status: str
    session: Dict[str, Any]
    temp_code: Optional[str] = None
    expires_in: Optional[int] = None


class VerifyCodeResponse(BaseModel):
    """코드 검증 결과"""
    status: str
    session_id: str


# ============================================
# 홈 데이터
# ============================================

class HomeDataResponse(BaseModel):
    """홈 데이터"""
    current_session: Optional[Dict[str, Any]] = None
    history: List[Dict[str, Any]]
    videos: Optional[Dict[str, List[Any]]] = None


# ============================================
# 영상 업로드 관련 (모바일)
# ============================================

class VideoMetadata(BaseModel):
    """영상 메타데이터"""
    videoName: str
    fileName: str
    absoluteStartTime: int
    absoluteEndTime: int
    duration: float


class CompleteUploadRequest(BaseModel):
    """업로드 완료 요청"""
    sessionId: str
    uploadId: str
    videoName: str
    etags: List[str]
    metadata: VideoMetadata


class UploadInitResponse(BaseModel):
    """업로드 초기화 응답"""
    uploadId: str
    presignedUrls: List[str]
    s3Key: str


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
# 기존 모델 (호환성 유지)
# ============================================

class PresignedUrlRequest(BaseModel):
    """Presigned URL 요청"""
    sessionId: str = Field(..., min_length=1, max_length=100)
    cameraId: int = Field(..., ge=1)
    fileName: str = Field(..., min_length=1)
    ntpStartTime: Optional[int] = None
    ntpEndTime: Optional[int] = None


class PresignedUrlResponse(BaseModel):
    """Presigned URL 응답"""
    success: bool
    uploadUrl: str
    videoId: int
    s3Key: str
    expiresIn: int
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