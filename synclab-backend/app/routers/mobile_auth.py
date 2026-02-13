# -*- coding: utf-8 -*-
from fastapi import APIRouter, HTTPException, Depends
from app.database.connection import get_db
from app.models.schemas import UserLogin, LoginResponse  # ✅ 정의한 스키마 임포트
from datetime import datetime, timedelta
from jose import jwt
import os
from dotenv import load_dotenv

load_dotenv()

router = APIRouter(prefix="/api/mobile/auth", tags=["Mobile Authentication"])

# ============================================
# JWT 설정
# ============================================
SECRET_KEY = os.getenv("SECRET_KEY")
ALGORITHM = os.getenv("ALGORITHM", "HS256")
ACCESS_TOKEN_EXPIRE_MINUTES = int(os.getenv("ACCESS_TOKEN_EXPIRE_MINUTES", 1440))

def create_access_token(data: dict):
    to_encode = data.copy()
    # UTC 기준 시간 사용 시 가급적 datetime.now(timezone.utc) 권장 (파이썬 3.12+)
    expire = datetime.utcnow() + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)

# ============================================
# API 엔드포인트
# ============================================
@router.post("/login") # ✅ 응답 모델 타입을 명시하면 좋습니다 -> response_model=LoginResponse
async def login(request: UserLogin, db = Depends(get_db)): # ✅ LoginRequest 대신 UserLogin(id, password) 사용
    cursor = db.cursor(dictionary=True)
    
    try:
        # DB 컬럼명은 이미 snake_case이므로 그대로 유지
        query = "SELECT user_id, id, user_name FROM user WHERE id = %s AND password = %s"
        cursor.execute(query, (request.id, request.password)) # ✅ request.userId -> request.id
        user = cursor.fetchone()
        
        if not user:
            raise HTTPException(status_code=401, detail="아이디 또는 비밀번호가 틀렸습니다.")

        # 1. JWT 토큰 생성
        token_data = {"sub": str(user['user_id']), "user_id": user['user_id']}
        access_token = create_access_token(token_data)

        # 2. 최신 세션 정보 조회
        session_query = """
            SELECT session_session_id, joined_at 
            FROM user_session 
            WHERE user_user_id = %s 
            ORDER BY joined_at DESC LIMIT 1
        """
        cursor.execute(session_query, (user['user_id'],))
        session_info = cursor.fetchone()

        current_session_id = session_info['session_session_id'] if session_info else None
        last_joined_at = int(session_info['joined_at'].timestamp() * 1000) if session_info and session_info['joined_at'] else None


        # 3. snake_case로 통일된 응답 반환
        return {
            "status": "success",
            "message": "로그인 성공",
            "access_token": access_token,
            "id": user['id'],
            "user_pk": user['user_id'],
            "user_name": user['user_name'],
            "current_session_id": current_session_id,   
            "last_joined_at": last_joined_at      
        }
            
    finally:
        cursor.close()