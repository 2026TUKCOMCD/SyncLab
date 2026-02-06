from fastapi import APIRouter, HTTPException, Depends
from app.database.connection import get_db
import mysql.connector

router = APIRouter(prefix="/api/mobile/home", tags=["Mobile Home"])

@router.get("/data")
async def get_home_data(db = Depends(get_db)):
    cursor = db.cursor(dictionary=True)
    
    try:
        # --- 0. 사용자 정보 조회 추가 ---
        # 실제 운영 환경에서는 로그인된 사용자의 PK를 세션이나 토큰에서 가져와야 합니다.
        # 여기서는 테스트를 위해 '111' 사용자를 직접 조회합니다.
        query_user = "SELECT id as user_id, user_name FROM user WHERE id = %s"
        cursor.execute(query_user, ('111',))
        user_info = cursor.fetchone()

        # 1. 현재 세션 조회 (최신순 1개)
        query_current = """
            SELECT session_id as session_id, 
                   session_name as session_name, 
                   created_at as created_at, 
                   invite_code as connect_code
            FROM session 
            ORDER BY created_at DESC LIMIT 1
        """
        cursor.execute(query_current)
        current_session = cursor.fetchone()

        # 2. 세션 히스토리 조회
        query_history = """
            SELECT session_id as session_id, 
                   session_name as session_name, 
                   created_at as created_at, 
                   (SELECT COUNT(*) FROM user_session WHERE session_session_id = session_id) as participant_count
            FROM session 
            ORDER BY created_at DESC
        """
        cursor.execute(query_history)
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

        # 4. 안드로이드 응답 규격 업데이트 (user_name, user_id 추가)
        return {
            "user_name": user_info['user_name'] if user_info else "게스트",
            "user_id": user_info['user_id'] if user_info else None,
            "current_session": current_session,
            "history": history if history else [],
            "videos": videos_map,
            "temp_codes": None
        }

    except Exception as e:
        print(f"🔥 홈 데이터 로드 실패: {e}")
        return {
            "user_name": "게스트",
            "user_id": None,
            "current_session": None, 
            "history": [], 
            "videos": {}, 
            "temp_codes": None
        }
    finally:
        cursor.close()