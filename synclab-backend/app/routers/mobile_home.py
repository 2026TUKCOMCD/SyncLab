from fastapi import APIRouter, HTTPException, Depends
from fastapi.security import HTTPBearer
from fastapi.security.http import HTTPAuthorizationCredentials
from app.database.connection import get_db
from jose import jwt, JWTError
import os
from dotenv import load_dotenv
import mysql.connector

load_dotenv()

router = APIRouter(prefix="/api/mobile/home", tags=["Mobile Home"])

# JWT 설정
SECRET_KEY = os.getenv("SECRET_KEY")
ALGORITHM = os.getenv("ALGORITHM", "HS256")
security = HTTPBearer()

def get_current_user(credentials: HTTPAuthorizationCredentials = Depends(security)):
    """JWT 토큰에서 현재 로그인한 사용자 정보를 추출"""
    token = credentials.credentials
    
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        user_id: int = payload.get("user_id")
        
        if user_id is None:
            raise HTTPException(status_code=401, detail="Invalid authentication credentials")
        
        return {"user_id": user_id}
        
    except JWTError:
        raise HTTPException(status_code=401, detail="Could not validate credentials")

@router.get("/data")
async def get_home_data(
    current_user: dict = Depends(get_current_user),
    db = Depends(get_db)
):
    cursor = db.cursor(dictionary=True)
    
    try:
        # 토큰에서 가져온 실제 user_id 사용
        user_id = current_user["user_id"]
        
        # 사용자 정보 조회 (user_id는 PK이므로 user_id로 조회)
        query_user = "SELECT user_id, id as user_id_string, user_name FROM user WHERE user_id = %s"
        cursor.execute(query_user, (user_id,))
        user_info = cursor.fetchone()
        
        if not user_info:
            raise HTTPException(status_code=404, detail="사용자를 찾을 수 없습니다.")

        # 1. 현재 세션 조회 (해당 사용자의 최신 세션)
        query_current = """
            SELECT s.session_id as session_id, 
                   s.session_name as session_name, 
                   s.created_at as created_at, 
                   s.invite_code as connect_code
            FROM session s
            INNER JOIN user_session us ON s.session_id = us.session_session_id
            WHERE us.user_user_id = %s
            ORDER BY us.joined_at DESC LIMIT 1
        """
        cursor.execute(query_current, (user_id,))
        current_session = cursor.fetchone()

        # 2. 세션 히스토리 조회 (해당 사용자가 참여한 세션만)
        query_history = """
            SELECT s.session_id as session_id, 
                   s.session_name as session_name, 
                   s.created_at as created_at, 
                   (SELECT COUNT(*) FROM user_session WHERE session_session_id = s.session_id) as participant_count
            FROM session s
            INNER JOIN user_session us ON s.session_id = us.session_session_id
            WHERE us.user_user_id = %s
            ORDER BY us.joined_at DESC
        """
        cursor.execute(query_history, (user_id,))
        history = cursor.fetchall()

        # 3. 비디오 맵 구성
        videos_map = {}
        if history:
            for s in history:
                sid = s['session_id']
                query_v = """
                    SELECT video_id as video_id, 
                           video_name as file_name, 
                           upload_status as status, 
                           UNIX_TIMESTAMP(created_at)*1000 as timestamp
                    FROM video 
                    WHERE session_session_id = %s
                """
                cursor.execute(query_v, (sid,))
                videos_map[str(sid)] = cursor.fetchall()

        # 4. 안드로이드 응답 규격 반환
        return {
            "user_name": user_info['user_name'],
            "user_id": user_info['user_id_string'],  # id 컬럼 값 (예: "111", "select")
            "current_session": current_session,
            "history": history if history else [],
            "videos": videos_map,
            "temp_codes": None
        }

    except HTTPException:
        raise
    except Exception as e:
        print(f"🔥 홈 데이터 로드 실패: {e}")
        raise HTTPException(status_code=500, detail=f"서버 에러가 발생했습니다: {str(e)}")
    finally:
        cursor.close()

        security = HTTPBearer()

def get_current_user(credentials: HTTPAuthorizationCredentials = Depends(security)):
    """JWT 토큰에서 사용자 ID 추출"""
    token = credentials.credentials
    payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
    return {"user_id": payload.get("user_id")}