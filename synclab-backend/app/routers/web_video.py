# video.py는 웹에서 사용자가 참여한 세션 내 동영상을 불러오는 함수
# js에서 사용자의 session_id로 api 호출 -> 데이터베이스 조회 후 해당 session_id가 저장되어 있는 동영상들의 파일 이름을 추출하여 주소들의 배열로 반환하면 EditPage에서 주소 할당 가능
# fetchall()함수로 session_id에 해당하는 모든 동영상을 객체로 받는데, 2차원 배열 형식으로 저장되기 때문에 인덱스 접근 시 [0][1] 식으로 사용

import jwt
import os
import uuid
import asyncio
import threading
import boto3
from urllib.parse import urlparse
from app.services.video_sync_editor import VideoEditServer
from app.services.ai_highlight import BasketballHighlightDetector, get_best_device, estimate_processing_seconds
from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.responses import StreamingResponse
from app.database.connection import get_db
from app.models.schemas import ClipData, SavedEditRequest, AIHighlightRequest
from fastapi.security import OAuth2PasswordBearer
from app.routers.web_auth import ALGORITHM, SECRET_KEY
from pathlib import Path
import json

# 진행 중인 렌더링 작업 상태 저장 (job_id -> 상태 dict)
jobs: dict = {}

S3_BUCKET_ORIGINAL = os.getenv("S3_BUCKET_ORIGINAL", "synclab-1080p-mp4")
AWS_REGION = os.getenv("AWS_REGION", "ap-northeast-2")
S3_BASE_URL = f"https://{S3_BUCKET_ORIGINAL}.s3.{AWS_REGION}.amazonaws.com/"

print(f"비디오 파일 키: {SECRET_KEY}") # 서버 로그 확인
router = APIRouter(prefix="/api/web")

# 26.2.12. 추가. 서비스 인스턴스를 생성합니다. (main.py의 마운트 경로인 'exports'와 일치시킴)
video_editor = VideoEditServer(output_dir="./exports")

# AI 하이라이트 자동 생성 서비스 인스턴스 (GPU 사용 가능 시 자동 사용, 불가능하면 CPU)
ai_highlight_detector = BasketballHighlightDetector()
AI_HIGHLIGHT_TEMP_DIR = Path("./exports/temp")

oauth2_scheme = OAuth2PasswordBearer(tokenUrl = "/api/web/login") # 토큰이 있는 URL 지정 -> 토큰 획득
def get_current_session_id(token: str = Depends(oauth2_scheme)):
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        session_id: int = payload.get("user_session_id")
        
        return session_id
    
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=401, detail="유효하지 않은 토큰입니다.")
    

@router.get("/sessions")
def get_user_session(token: str = Depends(oauth2_scheme), db = Depends(get_db)):
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        user_id = payload.get("user_id")

        cursor = db.cursor(dictionary=True)
        sql = "SELECT session_session_id FROM user_session WHERE user_user_id = %s"
        cursor.execute(sql, (user_id,))
        sessions = cursor.fetchall()
        return {"sessions":sessions}
    except jwt.PyJWTError:
        raise HTTPException(status_code=401, detail="토큰 인증 실패")
    except Exception as e:
        print(f"DB Error: {e}")
        raise HTTPException(status_code=500, detail="데이터베이스 조회 실패")
    finally:
        if 'cursor' in locals():
            cursor.close()


# 사용자 session_id에 해당하는 비디오 출력 처리
@router.get("/list/{session_id}")
def get_video_list(session_id: str, db = Depends(get_db)):

    cursor = db.cursor(dictionary=True)

    try:
        # ORDER BY video_id: /api/web/ai_highlight의 카메라 조회 쿼리와 순서를 일치시켜
        # camera_index가 프론트엔드 cameras[index]와 항상 같은 영상을 가리키도록 보장
        sql = "SELECT * FROM video WHERE session_session_id = %s ORDER BY video_id"
        cursor.execute(sql, (session_id, ))
        result = cursor.fetchall()

        base_url = S3_BASE_URL

        video_list = []
        for index, row in enumerate(result): # enumerate 함수는 인덱스와 원소로 이루어진 튜플을 반환해주는 내장 함수
            video_list.append({
                "id" : index,
                "name" : f"CAM {index+1}",
                "video_name" : row['video_name'],
                "start_time" : row['absolute_start_time'],
                "end_time" : row['absolute_end_time'],
                "videoUrl" : f"{base_url}{row['video_name']}"
            })
        return {"videos" : video_list}
    
    except Exception as e :
        print(f"Error : {e}")
        raise HTTPException(status_code=500, detail="서버 에러 발생")
    finally:
        cursor.close()

# 사용자가 프로젝트 생성 버튼 클릭 시 편집정보(EDL) 전송 처리 (현재 DB 저장만 구현된 상태)
@router.post("/save_edit_data")
def save_edit_data(request: SavedEditRequest, db = Depends(get_db)):
    #26.2.12. 수정.  pydantic 모델 객체를 파이썬의 기본 딕셔너리로 변환하기 위해 존재.
    edit_list = [clip.dict() for clip in request.edit_data]
    json_edit_data = json.dumps([clip.dict() for clip in request.edit_data])

    cursor = db.cursor(dictionary=True) 
    try:
        sql = "insert into edit (edit_data, session_session_id) values (%s, %s) ON DUPLICATE KEY UPDATE edit_data = %s"
        cursor.execute(sql, (json_edit_data, request.session_id, json_edit_data))

        db.commit()
        # request.edit_data가 이미 리스트 형태이므로 이를 서비스에 전달합니다.
        #   output_filename = ffmpeg_service.render_project(json_edit_data, request.session_id)

        # 3. 26/2/12  새로운 서비스 호출
        # VideoSyncEditor는 dict 리스트를 받으므로 edit_list를 payload에 담아 전달합니다.
        payload = {
            "session_id": request.session_id,
            "edit_data": edit_list
        }
        
        # 실제 영상 파일이 생성되고 경로가 반환됩니다. (이부분이 실질적으로 메서드 실행 파트!)
        final_output_path = video_editor.process_request(payload)
        # 파일 경로에서 이름만 추출 (예: final_20260212_123456.mp4)
        output_filename = Path(final_output_path).name

        # 4. 결과 반환 (성공 시 편집본을 볼 수 있는 주소 포함)
        return {
            "status": "success",
            "message": "편집 데이터 저장 및 영상 렌더링 완료",
            "video_url": f"/videos/{output_filename}"  #main.py의 마운트 경로와 일치시킴
        }
    except Exception as e:
        print(f"Database Error: {e}")
        db.rollback() # 에러시 롤벡
        raise HTTPException(status_code=500, detail=str(e))
    
    finally:
        cursor.close()




# POST /api/web/export - 렌더링 작업 시작, job_id 즉시 반환
@router.post("/export")
def start_export(request: SavedEditRequest, db = Depends(get_db)):
    edit_list = [clip.dict() for clip in request.edit_data]
    json_edit_data = json.dumps(edit_list)

    cursor = db.cursor(dictionary=True)
    try:
        sql = "INSERT INTO edit (edit_data, session_session_id) VALUES (%s, %s) ON DUPLICATE KEY UPDATE edit_data = %s"
        cursor.execute(sql, (json_edit_data, request.session_id, json_edit_data))
        db.commit()
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        cursor.close()

    job_id = str(uuid.uuid4())
    jobs[job_id] = {"status": "running", "progress": 0, "video_url": None, "error": None}

    payload = {"session_id": request.session_id, "edit_data": edit_list}

    def run_export():
        try:
            def progress_cb(pct):
                jobs[job_id]["progress"] = pct

            final_path = video_editor.process_request(payload, progress_callback=progress_cb)
            output_filename = Path(final_path).name
            jobs[job_id]["video_url"] = f"/videos/{output_filename}"
            jobs[job_id]["status"] = "done"
            jobs[job_id]["progress"] = 100
        except Exception as e:
            jobs[job_id]["status"] = "error"
            jobs[job_id]["error"] = str(e)

    thread = threading.Thread(target=run_export, daemon=True)
    thread.start()

    return {"job_id": job_id}


# GET /api/web/export/stream/{job_id} - SSE로 진행률 스트리밍
@router.get("/export/stream/{job_id}")
async def stream_export_progress(job_id: str):
    async def event_generator():
        while True:
            if job_id not in jobs:
                yield f"data: {json.dumps({'status': 'error', 'error': 'job not found'})}\n\n"
                return

            job = jobs[job_id]
            yield f"data: {json.dumps(job)}\n\n"

            if job["status"] in ("done", "error"):
                del jobs[job_id]
                return

            await asyncio.sleep(0.5)

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}
    )


def _download_video_for_analysis(video_url: str, dest_path: str) -> str:
    """AI 하이라이트 분석용 S3 영상 다운로드. 로컬 경로가 아니면 다운로드 후 경로 반환."""
    if '.s3.' not in video_url and 's3.amazonaws.com' not in video_url:
        return video_url

    parsed = urlparse(video_url)
    bucket = parsed.netloc.split('.')[0]
    key = parsed.path.lstrip('/')

    print(f"[AI Highlight][S3] Downloading s3://{bucket}/{key} -> {dest_path}")
    s3 = boto3.client(
        's3',
        region_name=AWS_REGION,
        aws_access_key_id=os.getenv('AWS_ACCESS_KEY'),
        aws_secret_access_key=os.getenv('AWS_SECRET_KEY'),
    )
    s3.download_file(bucket, key, dest_path)
    return dest_path


# POST /api/web/ai_highlight - 농구 득점 장면 AI 자동 분석 시작, job_id 즉시 반환 (백그라운드 처리)
@router.post("/ai_highlight")
def start_ai_highlight(request: AIHighlightRequest, db = Depends(get_db)):
    cursor = db.cursor(dictionary=True)
    try:
        sql = "SELECT * FROM video WHERE session_session_id = %s ORDER BY video_id"
        cursor.execute(sql, (request.session_id,))
        rows = cursor.fetchall()
    except Exception as e:
        print(f"DB Error: {e}")
        raise HTTPException(status_code=500, detail="세션 영상 조회 실패")
    finally:
        cursor.close()

    if not rows:
        raise HTTPException(status_code=404, detail="해당 세션에 영상이 없습니다.")
    if request.camera_index < 0 or request.camera_index >= len(rows):
        raise HTTPException(status_code=400, detail="유효하지 않은 카메라 인덱스입니다.")

    target_row = rows[request.camera_index]
    video_url = f"{S3_BASE_URL}{target_row['video_name']}"

    duration = target_row.get('duration')
    if not duration:
        try:
            duration = (float(target_row['absolute_end_time']) - float(target_row['absolute_start_time'])) / 1000
        except (KeyError, TypeError, ValueError):
            duration = None

    device = get_best_device()
    job_id = str(uuid.uuid4())
    jobs[job_id] = {
        "status": "running",
        "progress": 0,
        "highlights": None,
        "error": None,
        "device": device,
        "estimated_seconds": estimate_processing_seconds(duration, device) if duration else None,
    }

    AI_HIGHLIGHT_TEMP_DIR.mkdir(parents=True, exist_ok=True)

    def run_analysis():
        local_path = None
        try:
            dest_path = str(AI_HIGHLIGHT_TEMP_DIR / f"ai_highlight_{job_id}.mp4")
            local_path = _download_video_for_analysis(video_url, dest_path)

            def progress_cb(pct):
                jobs[job_id]["progress"] = pct

            highlights = ai_highlight_detector.analyze_video(
                local_path,
                duration=duration,
                sensitivity=request.sensitivity,
                progress_callback=progress_cb,
            )
            jobs[job_id]["highlights"] = highlights
            jobs[job_id]["status"] = "done"
            jobs[job_id]["progress"] = 100
        except Exception as e:
            print(f"[AI Highlight] 분석 실패: {e}")
            jobs[job_id]["status"] = "error"
            jobs[job_id]["error"] = str(e)
        finally:
            if local_path and local_path != video_url and os.path.exists(local_path):
                try:
                    os.remove(local_path)
                except OSError:
                    pass

    thread = threading.Thread(target=run_analysis, daemon=True)
    thread.start()

    return {"job_id": job_id, "device": device}


# GET /api/web/ai_highlight/stream/{job_id} - SSE로 진행률 및 결과 스트리밍
@router.get("/ai_highlight/stream/{job_id}")
async def stream_ai_highlight_progress(job_id: str):
    async def event_generator():
        while True:
            if job_id not in jobs:
                yield f"data: {json.dumps({'status': 'error', 'error': 'job not found'})}\n\n"
                return

            job = jobs[job_id]
            yield f"data: {json.dumps(job)}\n\n"

            if job["status"] in ("done", "error"):
                del jobs[job_id]
                return

            await asyncio.sleep(0.5)

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}
    )


        #26.2.13. 추가 - 서버 조회용 api, 즉 ( 불러오기시) 데베에서 꺼내서 편집할수 있게 만든 코드.
     
"""
@router.get("/get_saved_edit/{session_id}")
def get_saved_edit(session_id: str, db = Depends(get_db)):
    cursor = db.cursor(dictionary=True)
    try:
        # DB에서 해당 세션의 편집 데이터 조회
        sql = "SELECT edit_data FROM edit WHERE session_session_id = %s"
        cursor.execute(sql, (session_id,))
        result = cursor.fetchone()
        
        if result and result['edit_data']:
            # 저장된 문자열을 다시 리스트 객체로 변환하여 반환
            return {"status": "success", "edit_data": json.loads(result['edit_data'])}
        return {"status": "empty", "message": "저장된 데이터가 없습니다."}
    finally:
        cursor.close() 
        """