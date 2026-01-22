from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
from enum import Enum

class VideoStatus(str, Enum):
    PENDING = "pending"
    UPLOADED = "uploaded"
    PROCESSING = "processing"
    PROXY_READY = "proxy_ready"
    READY = "ready"
    ERROR = "error"

class PresignedUrlRequest(BaseModel):
    sessionId: str = Field(..., min_length=1, max_length=100)
    cameraId: int = Field(..., ge=1)
    fileName: str = Field(..., min_length=1)
    ntpStartTime: Optional[int] = None
    ntpEndTime: Optional[int] = None

class PresignedUrlResponse(BaseModel):
    success: bool
    uploadUrl: str
    videoId: int
    s3Key: str
    expiresIn: int
    bucket: str

class VideoInfo(BaseModel):
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
    video_id: int
    target_resolution: str = "1280x720"
    crf: int = Field(default=23, ge=0, le=51)