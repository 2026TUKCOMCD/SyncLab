# app/routers/mobile_session.py
"""
모바일 - 서버 | 세션 생성 요청, 세션 참가 요청 API
경로: /api/mobile/
"""
import random
import string
import time
from fastapi import APIRouter, HTTPException
from app.database.connection import get_db_connection
from app.models.schemas import SessionActionRequest  # ✅ schemas.py 사용
from typing import Optional
from pydantic import BaseModel
from datetime import datetime

router = APIRouter(prefix="/api/mobile/session", tags=["Mobile-Session"])


# ============================================
# 추가 모델 (schemas.py에 없는 것만)
# ============================================

class SessionJoinRequest(BaseModel):
    """세션 참가 요청"""
    invite_code: str
    user_pk: Optional[int] = None  # 비회원일 경우 None


# ============================================
# 유틸리티 함수
# ============================================

def generate_invite_code() -> str:
    """8자리 초대 코드 생성 (대문자 + 숫자)"""
    return ''.join(random.choices(string.ascii_uppercase + string.digits, k=8))


# ============================================
# API 
# ============================================

@router.post("/create") # 앱이 호출하는 경로로 맞춤
async def create_session(request: SessionActionRequest):
    """
    세션 생성 API
    - 만료 시간(expiresAt) 및 필수 필드(participantCount 등) 추가
    """
    invite_code = generate_invite_code()
    
    # 만료 시간 설정 (1시간 = 3600초)
    expires_in_seconds = 3600
    expires_at_timestamp = int(time.time()) + expires_in_seconds
    current_time_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    # 앱에서 보낸 user_pk 추출
    user_id_from_app = request.user_pk 
    
    try:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            
            # 1. session 테이블에 삽입
            sql_session = """
                INSERT INTO session (session_name, invite_code, expires_at)
                VALUES (%s, %s, %s)
            """
            cursor.execute(sql_session, (request.name or "새로운 세션", invite_code, expires_at_timestamp))
            
            # 2. 생성된 session_id 가져오기
            new_session_id = cursor.lastrowid
            
            # 3. user_session에 생성자 등록 
            # (주의: user_id_from_app이 DB의 user 테이블에 존재해야 함)
            sql_user_session = """
                INSERT INTO user_session (session_session_id, user_user_id)
                VALUES (%s, %s)
            """
            cursor.execute(sql_user_session, (new_session_id, user_id_from_app))
            
            conn.commit()
            cursor.close()
        
        # ✅ 앱의 SessionResponse 및 SessionInfo 데이터 구조에 완벽히 맞춤
        return {
            "status": "success",
            "session": {
                "sessionId": str(new_session_id),
                "sessionName": request.name or "새로운 세션",
                "createdAt": current_time_str,      # 앱 모델 필수 필드
                "participantCount": 1,              # 앱 모델 필수 필드
                "connectCode": invite_code,         # 앱 모델 필드
                "expiresAt": expires_at_timestamp   # 앱 모델 필수 필드 (Long)
            },
            "temp_code": invite_code,               # 앱 @SerializedName("temp_code")
            "expires_in": expires_in_seconds        # 앱 @SerializedName("expires_in")
        }
    
    except Exception as e:
        # 서버 콘솔에서 구체적인 에러 확인용
        print(f"🔥 세션 생성 에러 상세: {str(e)}")
        raise HTTPException(
            status_code=500, 
            detail=f"세션 생성 실패 (DB 또는 데이터 불일치): {str(e)}"
        )

@router.post("/join")
async def join_session(request: SessionJoinRequest):
    """
    세션 참가
    - 초대 코드로 세션 찾기
    - 회원인 경우만 참가자 명단에 등록
    - 비회원은 세션 정보만 반환
    
    경로: POST /api/mobile/join_session
    요청: {"invite_code": "ABC12345", "user_pk": 1} (user_pk는 Optional)
    """
    try:
        with get_db_connection() as conn:
            cursor = conn.cursor(dictionary=True)
            
            # 1. 초대 코드로 session_id 조회
            cursor.execute(
                "SELECT session_id, session_name FROM session WHERE invite_code = %s",
                (request.invite_code,)
            )
            session = cursor.fetchone()
            
            if not session:
                cursor.close()
                raise HTTPException(status_code=404, detail="유효하지 않은 코드입니다.")
            
            # 2. 회원인 경우만 참여 명단에 기록
            if request.user_pk:
                # 중복 참가 확인
                cursor.execute(
                    "SELECT 1 FROM user_session WHERE session_session_id = %s AND user_user_id = %s",
                    (session['session_id'], request.user_pk)
                )
                already_joined = cursor.fetchone()
                
                # 중복이 아닐 때만 삽입
                if not already_joined:
                    sql_join = """
                        INSERT INTO user_session (session_session_id, user_user_id)
                        VALUES (%s, %s)
                    """
                    cursor.execute(sql_join, (session['session_id'], request.user_pk))
                    conn.commit()
            
            cursor.close()
            
            return {
                "status": "success",
                "session_id": str(session['session_id']),
                "session": {
                    "sessionId": str(session['session_id']),
                    "sessionName": session['session_name']
                }
            }
    
    except HTTPException:
        raise
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"세션 참가 실패: {str(e)}")