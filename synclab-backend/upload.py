import boto3
import uvicorn
from fastapi import FastAPI, HTTPException
from botocore.config import Config
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List

app = FastAPI()

# CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# S3 설정
S3_BUCKET_NAME = "synclab-1080p-mp4" # 버킷 이름 추후 480p 버전도 필요
REGION_NAME = "ap-northeast-2" # 서울 리전

s3_client = boto3.client( # S3 클라이언트 생성
    's3',
    region_name=REGION_NAME,
    aws_access_key_id='',      # 본인 키 입력
    aws_secret_access_key='',  # 본인 키 입력
    config=Config(signature_version='s3v4')
)

# 안드로이드 데이터 모델 (정밀 매칭)

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

@app.get("/api/video/upload/init") # 모바일 GET 메소드 수신 시 S3 Presigned URL Return
def init_upload(filename: str, partCount: int):
    """[단계 1] S3 멀티파트 업로드 시작"""
    response = s3_client.create_multipart_upload(
        Bucket=S3_BUCKET_NAME,
        Key=filename,
        ContentType='video/mp4'
    )
    upload_id = response['UploadId']

    # 조각별 업로드 URL 생성
    presigned_urls = [
        s3_client.generate_presigned_url(
            ClientMethod='upload_part',
            Params={
                'Bucket': S3_BUCKET_NAME,
                'Key': filename,
                'UploadId': upload_id,
                'PartNumber': i
            },
            ExpiresIn=3600
        ) for i in range(1, partCount + 1)
    ]
        
    return {"uploadId": upload_id, "presignedUrls": presigned_urls}

@app.post("/api/video/upload/complete")
async def complete_upload(request: CompleteUploadRequest):
    """[단계 2] S3 조각 병합 및 메타데이터 수신"""
    try:
        # S3 형식으로 ETag 변환
        parts = [{"PartNumber": i + 1, "ETag": etag} for i, etag in enumerate(request.etags)]
        
        # S3 병합 명령
        s3_client.complete_multipart_upload(
            Bucket=S3_BUCKET_NAME,
            Key=request.videoName,
            UploadId=request.uploadId,
            MultipartUpload={'Parts': parts}
        )
        
        # 핵심 요약 로그만 출력
        print(f"[SUCCESS] {request.videoName} 병합 완료")
        print(f"[INFO] 기기 시작시간: {request.metadata.absoluteStartTime}")
        
        return {"status": "success"}

    except Exception as e:
        print(f"[ERROR] {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)