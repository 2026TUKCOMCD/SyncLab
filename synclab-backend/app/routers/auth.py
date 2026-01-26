# 사용자 관련 처리 함수
import mysql.connector
from fastapi import APIRouter, Depends, HTTPException, status
# from app.database.connection import get_db # 데이터베이스 세션 가져오는 함수? 필요한가
from app.models.schemas import Usercreate, UserLogin
from app.database.connection import *

router = APIRouter(prefix="/users") # /api/users 로 설정해야 함

@router.post("/signup")
def signup(user_data: Usercreate, db = Depends(get_db)):
    cursor = db.cursor(dictionary=True)
    
    # 회원가입 시도 시 동일한 아이디 존재하는지 조회
    try:
        sql_check = "SELECT * FROM user WHERE id = %s"
        cursor.execute(sql_check, (user_data.id,))
        existing_user = cursor.fetchone()

        if existing_user:
            raise HTTPException(status_code = 409, detail="이미 존재하는 아이디입니다.")
        
        # 회원가입 성공 시 데이터 저장
        sql_insert = "INSERT INTO user (id, password) VALUES (%s, %s)"
        cursor.execute(sql_insert, (user_data.id, user_data.password))

        db.commit()

        return {"msg" : "회원가입 성공"}
    
    except mysql.connector.Error as err:
        print(f"Error: {err}")
        raise HTTPException(status_code=500, detail="DB 오류 발생")
    
    finally:
        cursor.close()