# video.py는 웹에서 사용자가 참여한 세션 내 동영상을 불러오는 함수
# js에서 사용자의 session_id로 api 호출 -> 데이터베이스 조회 후 해당 session_id가 저장되어 있는 동영상들의 파일 이름을 추출하여 주소들의 배열로 반환하면 EditPage에서 주소 할당 가능
# fetchall()함수로 session_id에 해당하는 모든 동영상을 객체로 받는데, 2차원 배열 형식으로 저장되기 때문에 인덱스 접근 시 [0][1] 식으로 사용

import jwt
from app.services.video_sync_editor import VideoEditServer
from fastapi import APIRouter, Depends, HTTPException, status
from app.database.connection import get_db
from app.models.schemas import ClipData, SavedEditRequest
from fastapi.security import OAuth2PasswordBearer # HTTP 헤더에서 토큰을 추출하고 검증하는 보안 도구
from app.routers.web_auth import ALGORITHM, SECRET_KEY # 로그인에서 사용했던 토큰과 알고리즘 HS256
from pathlib import Path
import json

print(f"비디오 파일 키: {SECRET_KEY}") # 서버 로그 확인
router = APIRouter(prefix="/api/web")

# 26.2.12. 추가. 서비스 인스턴스를 생성합니다. (main.py의 마운트 경로인 'exports'와 일치시킴)
video_editor = VideoEditServer(output_dir="./exports")

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
        sql = "SELECT * FROM video WHERE session_session_id = %s"
        cursor.execute(sql, (session_id, ))
        result = cursor.fetchall()

        base_url = "https://synclab-1080p-mp4.s3.ap-northeast-2.amazonaws.com/"

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