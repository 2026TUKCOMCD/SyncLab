# video.py는 웹에서 사용자가 참여한 세션 내 동영상을 불러오는 함수
# js에서 사용자의 session_id로 api 호출 -> 데이터베이스 조회 후 해당 session_id가 저장되어 있는 동영상들의 파일 이름을 추출하여 주소들의 배열로 반환하면 EditPage에서 주소 할당 가능
# fetchall()함수로 session_id에 해당하는 모든 동영상을 객체로 받는데, 2차원 배열 형식으로 저장되기 때문에 인덱스 접근 시 [0][1] 식으로 사용

'''import jwt
import mysql.connector
from fastapi import APIRouter, Depends, HTTPException, status
from app.database.connection import *

router = APIRouter(prefix="/web")

@router.get("/list")
def get_video_list(session_id: int, db = Depends(get_db)):
    print('hello world') '''