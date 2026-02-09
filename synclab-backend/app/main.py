from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from app.routers import web_video
from app.routers import web_auth , mobile_session ,mobile_video, mobile_auth, mobile_home
from app.routers import session
from app.database.connection import test_connection
import os
from dotenv import load_dotenv

load_dotenv()

# FastAPI 앱 생성
app = FastAPI(
    title="SyncLab Multi-Camera System",
    description="다각도 영상 촬영 및 편집 시스템",
    version="1.0.0"
)
# ✅ 정적 파일 서빙 설정 추가 (가장 중요!)
# 서비스 로직에서 exports 폴더를 사용하므로 여기서 연결해줍니다.
EXPORT_DIR = "exports"
if not os.path.exists(EXPORT_DIR):
    os.makedirs(EXPORT_DIR)

# 브라우저에서 http://localhost:3000/videos/파일명.mp4 로 접근 가능하게 함 (웹에서 영상 재생 가능)
app.mount("/videos", StaticFiles(directory=EXPORT_DIR), name="videos")

# CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 라우터 등록
app.include_router(web_video.router)
app.include_router(web_auth.router)
app.include_router(session.router)
app.include_router(mobile_session.router)
app.include_router(mobile_video.router)
app.include_router(mobile_auth.router)
app.include_router(mobile_home.router)

# 시작 이벤트
@app.on_event("startup")
async def startup_event():
    print("╔════════════════════════════════════════╗")
    print("║   🚀 SyncLab FastAPI 서버 시작            ║")
    print("╚════════════════════════════════════════╝")
    
    # DB 연결 테스트
    if test_connection():
        print("✅ MySQL 연결 성공")
    else:
        print("❌ MySQL 연결 실패")

# 헬스 체크
@app.get("/health")
def health_check():
    return {
        "status": "OK",
        "service": "SyncLab FastAPI",
        "version": "1.0.0"
    }

@app.get("/")
def root():
    return {
        "message": "SyncLab Multi-Camera API",
        "docs": "/docs",
        "endpoints": {
            "presigned_url": "POST /api/video/presigned-url",
            "get_video": "GET /api/video/{video_id}",
            "create_proxy": "POST /api/video/proxy"
        }
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=os.getenv("SERVER_HOST", "0.0.0.0"),
        port=int(os.getenv("SERVER_PORT", 3000)),
        reload=os.getenv("DEBUG", "True") == "True"
    )