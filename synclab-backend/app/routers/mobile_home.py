from fastapi import APIRouter, HTTPException, Depends
from app.database.connection import get_db
import mysql.connector

router = APIRouter(prefix="/api/mobile/home", tags=["Mobile Home"])

@router.get("/data")
async def get_home_data(db = Depends(get_db)):
    cursor = db.cursor(dictionary=True)
    
    try:
        # 1. 현재 세션 조회 (최신순 1개)
        # SQL Alias를 snake_case로 변경: sessionId -> session_id 등
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

        # 2. 세션 히스토리 조회 (참여자 수 포함)
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

        # 3. 비디오 맵 구성 (key: session_id, value: 비디오 리스트)
        videos_map = {}
        if history:
            for s in history:
                # 쿼리에서 가져온 snake_case 키 사용
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

        # 4. 안드로이드 응답 규격과 일치 (모두 snake_case)
        return {
            "current_session": current_session,
            "history": history if history else [],
            "videos": videos_map,
            "temp_codes": None
        }

    except Exception as e:
        print(f"🔥 홈 데이터 로드 실패: {e}")
        return {
            "current_session": None, 
            "history": [], 
            "videos": {}, 
            "temp_codes": None
        }
    finally:
        cursor.close()