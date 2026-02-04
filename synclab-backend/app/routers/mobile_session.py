import random
import string
import os
import uuid
from fastapi import APIRouter, HTTPException, Depends, Header
from app.database.connection import get_db_connection
from app.models.schemas import SessionActionRequest, SessionResponse, SessionInfo
from typing import Optional
from datetime import datetime, timedelta
from dotenv import load_dotenv
from jose import jwt, JWTError

router = APIRouter(prefix="/api/mobile/session", tags=["Mobile-Session"])

load_dotenv()
SECRET_KEY = os.getenv("SECRET_KEY", "your-fallback-secret-key")
ALGORITHM = os.getenv("ALGORITHM", "HS256")

# ============================================
# 유효성 검사 및 유저 식별 함수
# ============================================
async def get_current_user_id(authorization: str = Header(...)):
    """Header에서 Bearer 토큰을 읽어 user_id(pk)를 반환"""
    try:
        token = authorization.split(" ")[1]
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        user_id: int = payload.get("user_id")
        if user_id is None:
            raise HTTPException(status_code=401, detail="토큰에 유저 정보가 없습니다.")
        return user_id
    except (JWTError, IndexError):
        raise HTTPException(status_code=401, detail="유효하지 않은 인증 토큰입니다.")

# ============================================
# 헬퍼 함수
# ============================================
def generate_invite_code() -> str:
    return ''.join(random.choices(string.ascii_uppercase + string.digits, k=8))

# ============================================
# API 엔드포인트
# ============================================

@router.post("/create")
async def create_session(request: SessionActionRequest, user_id: int = Depends(get_current_user_id)):
    invite_code = generate_invite_code()
    expires_in_seconds = 3600
    
    now = datetime.now()
    expires_at_dt = now + timedelta(seconds=expires_in_seconds)
    expires_at_timestamp = int(expires_at_dt.timestamp() * 1000)
    current_time_str = now.strftime("%Y-%m-%d %H:%M:%S")

    # ⭐️ 1. 고유한 세션 ID 생성 (예: SID_20260204_a1b2c3)
    date_part = now.strftime("%Y%m%d")
    random_part = str(uuid.uuid4())[:6]
    unique_session_id = f"SID_{date_part}_{random_part}"

    try:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            
            # 2. session 테이블에 삽입 (ID를 직접 전달)
            # 주의: DB의 session_id 컬럼 타입이 VARCHAR(문자열)여야 합니다.
            sql_session = """
                INSERT INTO session (session_id, session_name, invite_code, expires_at)
                VALUES (%s, %s, %s, %s)
            """
            cursor.execute(sql_session, (unique_session_id, request.name or "새로운 세션", invite_code, expires_at_dt))
            
            # 3. user_session 등록 (위에서 만든 unique_session_id 사용)
            sql_user_session = """
                INSERT INTO user_session (session_session_id, user_user_id)
                VALUES (%s, %s)
            """
            cursor.execute(sql_user_session, (unique_session_id, user_id))
            
            conn.commit()
            cursor.close()
        
        # 4. 앱 응답 (생성한 고유 ID 반환)
        return {
            "status": "success",
            "session": {
                "session_id": unique_session_id,  # ⭐️ 숫자 대신 문자열 ID 반환
                "session_name": request.name or "새로운 세션",
                "created_at": current_time_str,
                "participant_count": 1,
                "connect_code": invite_code,
                "expires_at": expires_at_timestamp 
            },
            "temp_code": invite_code,
            "expires_in": expires_in_seconds
        }
    
    except Exception as e:
        print(f"🔥 세션 생성 에러 상세: {str(e)}")
        raise HTTPException(status_code=500, detail=f"세션 생성 실패: {str(e)}")    
    
@router.post("/join")
async def join_session(request: dict, # 간단하게 dict로 받거나 전용 스키마 생성 권장
                       user_id_from_auth: int = Depends(get_current_user_id)):
    try:
        invite_code = request.get("invite_code")
        if not invite_code:
            raise HTTPException(status_code=400, detail="초대 코드가 필요합니다.")

        with get_db_connection() as conn:
            cursor = conn.cursor(dictionary=True)
            
            cursor.execute(
                "SELECT session_id, session_name, created_at, invite_code, expires_at FROM session WHERE invite_code = %s",
                (invite_code,)
            )
            session = cursor.fetchone()
            
            if not session:
                cursor.close()
                raise HTTPException(status_code=404, detail="유효하지 않은 코드입니다.")
            
            # INSERT IGNORE 사용하여 중복 참가 방지
            sql_join = """
                INSERT IGNORE INTO user_session (session_session_id, user_user_id)
                VALUES (%s, %s)
            """
            cursor.execute(sql_join, (session['session_id'], user_id_from_auth))
            conn.commit()
            
            # 현재 참여자 수 계산
            cursor.execute("SELECT COUNT(*) as count FROM user_session WHERE session_session_id = %s", (session['session_id'],))
            p_count = cursor.fetchone()['count']
            
            cursor.close()
            
            return {
                "status": "success",
                "session_id": session['session_id'], # 이미 문자열이므로 str() 제거
                "session": {
                    "session_id": session['session_id'], # 이미 문자열이므로 str() 제거
                    "session_name": session['session_name'],
                    "created_at": session['created_at'].strftime("%Y-%m-%d %H:%M:%S") if session['created_at'] else "",
                    "participant_count": p_count,
                    "connect_code": session['invite_code'],
                    "expires_at": int(session['expires_at'].timestamp() * 1000) if session['expires_at'] else None
                }
            }
    except HTTPException:
        raise
    except Exception as e:
        print(f"🔥 세션 참가 에러 상세: {str(e)}")
        raise HTTPException(status_code=500, detail=f"세션 참가 실패: {str(e)}")