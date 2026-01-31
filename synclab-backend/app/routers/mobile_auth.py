## 모바일 - 서버 | 회원가입, 로그인 API
## /api/mobile/ ~~ 로 경로 지정할 것 ex) /api/mobile/login

"""
모바일 앱 전용 인증 API 모듈
경로: /api/mobile/
"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

router = APIRouter(prefix="/api/mobile", tags=["Mobile Authentication"])

# ============================================
# 데이터 모델
# ============================================
class LoginRequest(BaseModel):
    userId: str
    userPw: str

# ============================================
# API 엔드포인트
# ============================================
@router.post("/login")
async def login(request: LoginRequest):
    """모바일 로그인"""
    if request.userId == "111" and request.userPw == "111":
        return {
            "status": "success", 
            "userName": "테스트 관리자",
            "userId": "111",
            "userPk": 111,
            "currentSessionId": None,
            "lastJoinedAt": None
        }
    raise HTTPException(status_code=401, detail="인증 실패")
