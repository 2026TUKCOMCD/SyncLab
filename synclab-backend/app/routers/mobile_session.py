# app/routers/mobile_session.py
"""
모바일 - 서버 | 세션 생성 요청, 세션 참가 요청 API
경로: /api/mobile/
"""
import random
import string
from fastapi import APIRouter, HTTPException
from app.database.connection import get_db_connection
from app.models.schemas import SessionActionRequest  # ✅ schemas.py 사용
from typing import Optional
from pydantic import BaseModel

router = APIRouter(prefix="/api/mobile", tags=["Mobile-Session"])


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
# API 엔드포인트
# ============================================

@router.post("/create_session")
async def create_session(request: SessionActionRequest):  # ✅ schemas.py 모델 사용
    """
    세션 생성
    - 새로운 세션 생성
    - 초대 코드(invite_code) 발급
    - 생성자를 참가자로 자동 등록
    
    경로: POST /api/mobile/create_session
    요청: {"name": "세션 이름"} (Optional)
    """
    invite_code = generate_invite_code()
    
    # ✅ SessionActionRequest는 name만 있음 (user_pk 제거)
    # user_pk는 나중에 JWT에서 추출하도록 변경 필요
    default_user_pk = 1  # 임시 기본값
    
    try:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            
            # 1. session 테이블에 삽입
            sql_session = """
                INSERT INTO session (session_name, invite_code)
                VALUES (%s, %s)
            """
            cursor.execute(sql_session, (request.name or "새로운 세션", invite_code))
            
            # 2. 생성된 session_id 가져오기 (AUTO_INCREMENT)
            new_session_id = cursor.lastrowid
            
            # 3. user_session에 생성자 등록
            sql_user_session = """
                INSERT INTO user_session (session_session_id, user_user_id)
                VALUES (%s, %s)
            """
            cursor.execute(sql_user_session, (new_session_id, default_user_pk))
            
            conn.commit()
            cursor.close()
        
        return {
            "status": "success",
            "session": {
                "sessionId": str(new_session_id),
                "sessionName": request.name or "새로운 세션",
                "connectCode": invite_code
            },
            "temp_code": invite_code,
            "expires_in": 3600
        }
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"세션 생성 실패: {str(e)}")


@router.post("/join_session")
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