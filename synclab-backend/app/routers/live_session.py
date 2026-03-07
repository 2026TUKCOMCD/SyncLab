import os
import uuid
from datetime import datetime
from fastapi import APIRouter, HTTPException, Depends, Header
from app.database.connection import get_db_connection
from typing import Optional
from dotenv import load_dotenv
from jose import jwt, JWTError

router = APIRouter(prefix="/api/live", tags=["Live-Session"])

load_dotenv()

# ============================================
# 오버레이 인메모리 스토어 (session_id → overlay dict)
# ============================================
overlay_store: dict = {}

def default_overlay():
    return {
        "showScoreboard": False,
        "homeTeam": "HOME",
        "awayTeam": "AWAY",
        "homeScore": 0,
        "awayScore": 0,
        "showLowerThird": False,
        "lowerThird": "",
        "subTitle": ""
    }
SECRET_KEY = os.getenv("SECRET_KEY", "your-fallback-secret-key")
ALGORITHM = os.getenv("ALGORITHM", "HS256")

# 백엔드 → LiveKit 관리 API 호출용 (Docker 내부 네트워크)
LIVEKIT_SERVER_URL = os.getenv("LIVEKIT_SERVER_URL", "http://localhost:7880")
# 클라이언트(앱/웹)에게 반환하는 URL (외부 접속용, ngrok 등)
LIVEKIT_CLIENT_URL = os.getenv("LIVEKIT_CLIENT_URL", "ws://localhost:7880")
LIVEKIT_API_KEY = os.getenv("LIVEKIT_API_KEY", "synclab-key")
LIVEKIT_API_SECRET = os.getenv("LIVEKIT_API_SECRET", "synclab-secret-minimum-32-chars-long!!")

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
# API 엔드포인트
# ============================================

@router.post("/session/create")
async def create_live_session(
    request: dict,
    user_id: int = Depends(get_current_user_id)
):
    """라이브 세션 생성 + LiveKit 방 생성"""
    session_id = request.get("session_id")
    if not session_id:
        raise HTTPException(status_code=400, detail="session_id가 필요합니다.")

    now = datetime.now()
    date_part = now.strftime("%Y%m%d")
    random_part = str(uuid.uuid4())[:6]
    room_name = f"live_{session_id}_{date_part}_{random_part}"

    try:
        from livekit import api as livekit_api_module
        lk = livekit_api_module.LiveKitAPI(
            url=LIVEKIT_SERVER_URL,
            api_key=LIVEKIT_API_KEY,
            api_secret=LIVEKIT_API_SECRET
        )
        await lk.room.create_room(
            livekit_api_module.CreateRoomRequest(name=room_name)
        )
        await lk.aclose()
    except Exception as e:
        print(f"LiveKit 방 생성 실패: {e}")
        # LiveKit 서버가 없어도 토큰 발급은 가능하도록 진행

    try:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            sql = """
                INSERT INTO live_session (room_name, session_id, created_by)
                VALUES (%s, %s, %s)
                ON DUPLICATE KEY UPDATE room_name = VALUES(room_name)
            """
            cursor.execute(sql, (room_name, session_id, user_id))
            conn.commit()
            cursor.close()
    except Exception as e:
        print(f"live_session DB 저장 실패 (테이블 없을 수 있음): {e}")

    return {
        "session_id": session_id,
        "room_name": room_name,
        "livekit_url": LIVEKIT_CLIENT_URL
    }


@router.post("/session/token")
async def get_live_token(
    request: dict,
    user_id: int = Depends(get_current_user_id)
):
    """LiveKit 참가 토큰 발급"""
    session_id = request.get("session_id")
    role = request.get("role", "viewer")  # camera | controller | viewer

    if not session_id:
        raise HTTPException(status_code=400, detail="session_id가 필요합니다.")
    if role not in ("camera", "controller", "viewer"):
        raise HTTPException(status_code=400, detail="role은 camera | controller | viewer 중 하나여야 합니다.")

    # 컨트롤러 중복 체크: 세션당 하나의 컨트롤러만 허용
    if role == "controller":
        try:
            from livekit import api as livekit_api_module
            lk = livekit_api_module.LiveKitAPI(
                url=LIVEKIT_SERVER_URL,
                api_key=LIVEKIT_API_KEY,
                api_secret=LIVEKIT_API_SECRET
            )
            participants = await lk.room.list_participants(
                livekit_api_module.ListParticipantsRequest(room=session_id)
            )
            await lk.aclose()
            existing = [p for p in participants.participants if p.identity.startswith("controller_")]
            if existing:
                raise HTTPException(status_code=409, detail="이미 컨트롤러가 연결되어 있습니다.")
        except HTTPException:
            raise
        except Exception as e:
            print(f"컨트롤러 중복 체크 실패 (무시): {e}")

    participant_name = f"{role}_{user_id}"

    try:
        from livekit import api as livekit_api_module
        from livekit.api import VideoGrants

        can_publish = role in ("camera", "controller")
        grants = VideoGrants(
            room_join=True,
            room=session_id,
            can_publish=can_publish,
            can_subscribe=True,
            can_publish_data=can_publish,
        )
        token = (
            livekit_api_module.AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET)
            .with_identity(participant_name)
            .with_name(participant_name)
            .with_grants(grants)
            .to_jwt()
        )
        return {"token": token, "livekit_url": LIVEKIT_CLIENT_URL}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"토큰 발급 실패: {str(e)}")


@router.post("/session/go-live")
async def go_live(
    request: dict,
    user_id: int = Depends(get_current_user_id)
):
    """라이브 방송 시작 - 목록에 표시"""
    session_id = request.get("session_id")
    if not session_id:
        raise HTTPException(status_code=400, detail="session_id가 필요합니다.")
    try:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "UPDATE live_session SET is_live = 1 WHERE session_id = %s",
                (session_id,)
            )
            conn.commit()
            cursor.close()
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"DB 오류: {str(e)}")
    return {"status": "live", "session_id": session_id}


@router.post("/session/end-live")
async def end_live(
    request: dict,
    user_id: int = Depends(get_current_user_id)
):
    """라이브 방송 종료 - 목록에서 제거"""
    session_id = request.get("session_id")
    if not session_id:
        raise HTTPException(status_code=400, detail="session_id가 필요합니다.")
    try:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "UPDATE live_session SET is_live = 0 WHERE session_id = %s",
                (session_id,)
            )
            conn.commit()
            cursor.close()
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"DB 오류: {str(e)}")
    return {"status": "ended", "session_id": session_id}


@router.post("/session/switch")
async def switch_camera(
    request: dict,
    user_id: int = Depends(get_current_user_id)
):
    """메인 화면 전환 명령 - DB에 활성 카메라 기록"""
    session_id = request.get("session_id")
    target_camera_id = request.get("target_camera_id")

    if not session_id or not target_camera_id:
        raise HTTPException(status_code=400, detail="session_id와 target_camera_id가 필요합니다.")

    try:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            sql = """
                UPDATE live_session SET active_camera = %s
                WHERE session_id = %s
            """
            cursor.execute(sql, (target_camera_id, session_id))
            conn.commit()
            cursor.close()
    except Exception as e:
        print(f"active_camera 업데이트 실패 (테이블 없을 수 있음): {e}")

    return {"status": "success", "active_camera": target_camera_id}


@router.get("/sessions")
async def list_live_sessions(user_id: int = Depends(get_current_user_id)):
    """현재 활성 라이브 세션 목록 반환 (카메라가 1개 이상 연결된 세션)"""
    sessions_raw = []
    try:
        with get_db_connection() as conn:
            cursor = conn.cursor(dictionary=True)
            cursor.execute("""
                SELECT ls.session_id, ls.room_name, ls.created_at,
                       s.session_name, u.user_name AS host_name
                FROM live_session ls
                LEFT JOIN session s ON ls.session_id = s.session_id
                LEFT JOIN user u ON ls.created_by = u.user_id
                WHERE ls.is_live = 1
                ORDER BY ls.created_at DESC
                LIMIT 30
            """)
            sessions_raw = cursor.fetchall()
            cursor.close()
    except Exception as e:
        print(f"live_session 목록 조회 실패: {e}")

    sessions = []
    try:
        from livekit import api as livekit_api_module
        lk = livekit_api_module.LiveKitAPI(
            url=LIVEKIT_SERVER_URL,
            api_key=LIVEKIT_API_KEY,
            api_secret=LIVEKIT_API_SECRET
        )
        for row in sessions_raw:
            try:
                participants = await lk.room.list_participants(
                    livekit_api_module.ListParticipantsRequest(room=row['session_id'])
                )
                camera_count = sum(1 for p in participants.participants if p.identity.startswith("camera_"))
                viewer_count = sum(1 for p in participants.participants if p.identity.startswith("viewer_"))
                if camera_count > 0:
                    sessions.append({
                        "session_id": row['session_id'],
                        "session_name": row.get('session_name') or row['session_id'],
                        "host_name": row.get('host_name') or '알 수 없음',
                        "camera_count": camera_count,
                        "viewer_count": viewer_count,
                        "created_at": row['created_at'].isoformat() if row.get('created_at') else None,
                    })
            except Exception:
                pass
        await lk.aclose()
    except Exception as e:
        print(f"LiveKit 참가자 조회 실패: {e}")

    return {"sessions": sessions}


@router.get("/session/{session_id}/status")
async def get_live_status(
    session_id: str,
    user_id: int = Depends(get_current_user_id)
):
    """현재 라이브 세션 상태 조회"""
    active_camera = None

    is_live = True
    try:
        with get_db_connection() as conn:
            cursor = conn.cursor(dictionary=True)
            cursor.execute(
                "SELECT room_name, active_camera, is_live FROM live_session WHERE session_id = %s",
                (session_id,)
            )
            row = cursor.fetchone()
            cursor.close()

        if row:
            active_camera = row.get("active_camera")
            is_live = bool(row.get("is_live", 1))
        room_name = session_id  # 토큰의 room=session_id와 일치시킴
    except Exception as e:
        print(f"live_session 조회 실패 (테이블 없을 수 있음): {e}")
        room_name = session_id

    # LiveKit에서 참여자 목록 조회 시도 (session_id 기준)
    cameras = []
    try:
        from livekit import api as livekit_api_module
        lk = livekit_api_module.LiveKitAPI(
            url=LIVEKIT_SERVER_URL,
            api_key=LIVEKIT_API_KEY,
            api_secret=LIVEKIT_API_SECRET
        )
        participants = await lk.room.list_participants(
            livekit_api_module.ListParticipantsRequest(room=session_id)
        )
        await lk.aclose()

        for p in participants.participants:
            if p.identity.startswith("camera_"):
                # TrackSource.CAMERA = 1 (LiveKit protobuf enum)
                is_streaming = any(t.source == 1 for t in p.tracks)
                cameras.append({
                    "id": p.identity,
                    "name": p.name or p.identity,
                    "isActive": p.identity == active_camera,
                    "isStreaming": is_streaming
                })
    except Exception as e:
        print(f"LiveKit 참여자 조회 실패: {e}")

    return {
        "session_id": session_id,
        "room_name": room_name,
        "active_camera": active_camera,
        "cameras": cameras,
        "is_live": is_live
    }


# ============================================
# 오버레이 API
# ============================================

@router.get("/session/{session_id}/overlay")
async def get_overlay(
    session_id: str,
    user_id: int = Depends(get_current_user_id)
):
    """현재 오버레이 데이터 반환 (웹 플레이어가 500ms 폴링)"""
    return overlay_store.get(session_id, default_overlay())


@router.post("/session/{session_id}/overlay")
async def update_overlay(
    session_id: str,
    request: dict,
    user_id: int = Depends(get_current_user_id)
):
    """컨트롤러폰이 오버레이 데이터 업데이트"""
    overlay_store[session_id] = {
        "showScoreboard": request.get("showScoreboard", False),
        "homeTeam": request.get("homeTeam", "HOME"),
        "awayTeam": request.get("awayTeam", "AWAY"),
        "homeScore": request.get("homeScore", 0),
        "awayScore": request.get("awayScore", 0),
        "showLowerThird": request.get("showLowerThird", False),
        "lowerThird": request.get("lowerThird", ""),
        "subTitle": request.get("subTitle", ""),
    }
    return {"status": "ok"}
