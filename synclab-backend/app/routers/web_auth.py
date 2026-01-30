# 웹 - 서버 | 회원가입, 로그인 API
# /api/web/ ~~ 로 경로 지정할 것 ex) /api/web/login

import mysql.connector
import jwt # JSON Web Token 패키지 -> 사용자의 상태 정보 유지 목적
from datetime import datetime, timedelta # 시간 계산용
from fastapi import APIRouter, Depends, HTTPException, status
from app.models.schemas import Usercreate, Userlogin
from app.database.connection import *

router = APIRouter(prefix="/web") # /api/web 로 설정해야 함

# 토큰 생성 함수
SECRET_KEY = "my_super_secret_key_synclab" 
ALGORITHM = "HS256" # 암호화 방식 - 해시함수
ACCESS_TOKEN_EXPIRE_MINUTES = 600 # 토큰 유효시간 10시간

def create_access_token(data: dict): # dictionary 클래스 사용
    to_encode = data.copy()

    expire = datetime.utcnow() + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    to_encode.update({"exp": expire}) # 만료시간 정의
    encode_jwt = jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)

    return encode_jwt

# 회원가입 실행 함수
@router.post("/signup")
def signup(user_data: Usercreate, db = Depends(get_db)):
    cursor = db.cursor(dictionary=True)
    
    # 회원가입 시도 시 동일한 아이디 존재하는지 조회
    try:
        sql_check = "SELECT * FROM user WHERE id = %s"
        cursor.execute(sql_check, (user_data.id,))
        existing_user = cursor.fetchone() 
        # fetchone() 함수는 DB의 한 컬럼을 가져오며 튜플 형식으로 반환함 -> 인덱스로 칼럼을 접근할 수 있음
        # db pool 가져다 쓴 경우에 dictionray=True 옵션을 주면 인덱스 번호가 아닌 칼럼이름('')으로 접근해야 함

        if existing_user:
            raise HTTPException(status_code = 409, detail="이미 존재하는 아이디입니다.")
        
        # 회원가입 성공 시 데이터 저장
        sql_insert = "INSERT INTO user (id, password, user_name) VALUES (%s, %s, %s)"
        cursor.execute(sql_insert, (user_data.id, user_data.password, user_data.user_name))

        db.commit()

        return {"msg" : "회원가입 성공"}
    
    except mysql.connector.Error as err:
        print(f"Error: {err}")
        raise HTTPException(status_code=500, detail="DB 오류 발생")
    
    finally:
        cursor.close()

# 로그인 실행 함수
@router.post("/login")
def login(user_data: Userlogin, db = Depends(get_db)):
    cursor = db.cursor(dictionary=True)

    # 로그인 시도 시 아이디와 비밀번호 일치 확인
    try:
        sql_check = "SELECT * FROM user WHERE id = %s"
        cursor.execute(sql_check, (user_data.id,))
        existing_user = cursor.fetchone() # 해당 id로 조회된 유저의 모든 정보를 갖고 있는 객체

        if not existing_user: # 해당 아이디의 회원이 존재하지 않음
            raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="존재하지 않는 아이디입니다."
                )
        if existing_user['password'] != user_data.password: # 아이디는 일치하지만 비밀번호가 일치하지 않음
            raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="비밀번호가 일치하지 않습니다."
                )
        sql_check_session_id = "SELECT session_session_id FROM user_session WHERE user_user_id = %s"
        cursor.execute(sql_check_session_id, (existing_user['user_id'],))
        existing_user_session = cursor.fetchone()

        if existing_user_session:
            current_session_id = existing_user_session['session_session_id']
        else:
            current_session_id = None
        token_data = {
            "id": existing_user['id'],
            "user_id": existing_user['user_id'],
            "user_name": existing_user['user_name'],
            "user_session_id": current_session_id
        }
        access_token = create_access_token(token_data)

        return{
            "message": "로그인 성공",
            "access_token": access_token,
            "token_type": "bearer",
            "user_name": existing_user['user_name'],
            "user_id": existing_user['user_id'],
            "user_session_id": current_session_id
        }

    except mysql.connector.Error as err:
        print(f"Login DB Error : {err}")
        raise HTTPException(status_code=500, detail="서버 데이터베이스 오류")
    
    finally:
        cursor.close()