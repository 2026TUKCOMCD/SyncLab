from fastapi import APIRouter, HTTPException
from app.database.connection import get_db_connection
from pydantic import BaseModel
from typing import Optional

router = APIRouter(prefix="/api/session", tags=["Session"])

# 앱에서 보내는 참가 요청 규격
class SessionJoinRequest(BaseModel):
    invite_code: str
    user_pk: Optional[int] = None # 비회원일 경우 대비

@router.post("/join")
async def join_session(request: SessionJoinRequest): # 바디 데이터로 변경
    try:
        with get_db_connection() as conn:
            cursor = conn.cursor(dictionary=True)
            
            # 1. 초대코드로 session_id 조회
            cursor.execute("SELECT session_id FROM session WHERE invite_code = %s", (request.invite_code,))
            session = cursor.fetchone()
            
            if not session:
                raise HTTPException(status_code=404, detail="유효하지 않은 코드입니다.")
            
            # 2. 회원인 경우만 참여 명단에 기록
            if request.user_pk:
                # INSERT IGNORE 등을 사용하여 이미 참가한 경우의 에러 방지 가능
                sql_join = "INSERT INTO user_session (session_session_id, user_user_id) VALUES (%s, %s)"
                cursor.execute(sql_join, (session['session_id'], request.user_pk))
                conn.commit()
            
            return {
                "status": "success",
                "session_id": str(session['session_id'])
            }
    except Exception as e:
        # 중복 참가 시 발생하는 에러 등을 처리하려면 예외 처리를 세분화하는 것이 좋음
        raise HTTPException(status_code=500, detail=str(e))