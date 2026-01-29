import random
import string
from fastapi import APIRouter, HTTPException
from app.database.connection import get_db_connection
from pydantic import BaseModel
from typing import Optional

router = APIRouter(prefix="/api/session", tags=["Session"])

# 앱에서 보내는 데이터 규격 정의
class SessionCreateRequest(BaseModel):
    name: Optional[str] = "새로운 세션"
    user_pk: int # 생성자 ID

def generate_invite_code():
    return ''.join(random.choices(string.ascii_uppercase + string.digits, k=8))

@router.post("/create")
async def create_session(request: SessionCreateRequest): # 클래스 모델로 수신
    invite_code = generate_invite_code()
    
    try:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            
            # 1. session 테이블에 삽입
            sql_session = "INSERT INTO session (session_name, invite_code) VALUES (%s, %s)"
            cursor.execute(sql_session, (request.name, invite_code))
            
            # 2. 생성된 PK 가져오기
            new_session_id = cursor.lastrowid
            
            # 3. user_session에 참여자로 등록
            sql_user_session = "INSERT INTO user_session (session_session_id, user_user_id) VALUES (%s, %s)"
            cursor.execute(sql_user_session, (new_session_id, request.user_pk))
            
            conn.commit()
            
        return {
            "status": "success",
            "session": {
                "sessionId": str(new_session_id),
                "connectCode": invite_code
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))