# 웹 - 서버 | 회원가입, 로그인 API
# /api/web/ ~~ 로 경로 지정할 것 ex) /api/web/login

import mysql.connector
import jwt # JSON Web Token 패키지 -> 사용자의 상태 정보 유지 목적
import bcrypt
from datetime import datetime, timedelta # 시간 계산용
from fastapi import APIRouter, Depends, HTTPException, status
from app.models.schemas import Usercreate, Userlogin
from app.database.connection import *
# 소셜 로그인 / 이메일 인증 추가 import
import httpx
import random
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from datetime import timezone
from app.models.schemas import GoogleLoginRequest, KakaoLoginRequest, SendCodeRequest, VerifyCodeRequest, SignupRequest
from google.oauth2 import id_token
from google.auth.transport import requests as google_requests

GOOGLE_CLIENT_ID = os.getenv("GOOGLE_CLIENT_ID")
SMTP_EMAIL = os.getenv("SMTP_EMAIL")
SMTP_PASSWORD = os.getenv("SMTP_PASSWORD")

router = APIRouter(prefix="/api/web") # /api/web 로 설정해야 함

# 토큰 생성 함수
SECRET_KEY = os.getenv("SECRET_KEY")
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
    cursor = db.cursor(dictionary=True, buffered = True)
    
    # 회원가입 시도 시 동일한 아이디 존재하는지 조회
    try:
        sql_check = "SELECT * FROM user WHERE id = %s"
        cursor.execute(sql_check, (user_data.id,))
        existing_user = cursor.fetchone() 
        # fetchone() 함수는 DB의 한 컬럼을 가져오며 튜플 형식으로 반환함 -> 인덱스로 칼럼을 접근할 수 있음
        # db pool 가져다 쓴 경우에 dictionray=True 옵션을 주면 인덱스 번호가 아닌 칼럼이름('')으로 접근해야 함

        if existing_user:
            raise HTTPException(status_code = 409, detail="이미 존재하는 아이디입니다.")
        
        # 회원가입 성공 시 데이터 저장 (비밀번호 bcrypt 해싱)
        hashed_pw = bcrypt.hashpw(user_data.password.encode(), bcrypt.gensalt()).decode()
        sql_insert = "INSERT INTO user (id, password, user_name) VALUES (%s, %s, %s)"
        cursor.execute(sql_insert, (user_data.id, hashed_pw, user_data.user_name))

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
    cursor = db.cursor(dictionary=True, buffered=True)

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
        try:
            password_ok = bool(
                existing_user['password'] and
                bcrypt.checkpw(user_data.password.encode(), existing_user['password'].encode())
            )
        except (ValueError, TypeError):
            password_ok = False
        if not password_ok: # 아이디는 일치하지만 비밀번호가 일치하지 않음
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


# 공통 함수
def _build_web_login_response(cursor, user: dict, message: str = "로그인 성공") -> dict: # 로그인 성공 시 토큰 생성
    token_data = {
        "id": user['id'],
        "user_id": user['user_id'],
        "user_name": user['user_name'],
    }
    access_token = create_access_token(token_data)

    cursor.execute( # 토큰 생성 후 해당 유저가 참여한 세션 아이디 반환
        "SELECT session_session_id FROM user_session WHERE user_user_id = %s",
        (user['user_id'],)
    )
    session_row = cursor.fetchone()
    current_session_id = session_row['session_session_id'] if session_row else None

    return {
        "message": message,
        "access_token": access_token,
        "token_type": "bearer",
        "user_name": user['user_name'],
        "user_id": user['user_id'],
        "user_session_id": current_session_id
    }


# Google 로그인
@router.post("/google")
async def web_google_login(request: GoogleLoginRequest, db=Depends(get_db)):

    cursor = db.cursor(dictionary=True)
    try:
        idinfo = id_token.verify_oauth2_token(
            request.id_token,
            google_requests.Request(),
            GOOGLE_CLIENT_ID
        )
        google_sub = idinfo['sub']
        email = idinfo.get('email', '')
        name = idinfo.get('name', email.split('@')[0] if email else 'Google User')
        picture = idinfo.get('picture', '')

        cursor.execute(
            "SELECT user_user_id FROM social_account WHERE provider = 'google' AND provider_user_id = %s",
            (google_sub,)
        )
        existing = cursor.fetchone()

        if existing:
            cursor.execute(
                "SELECT user_id, id, user_name, profile_image, email FROM user WHERE user_id = %s",
                (existing['user_user_id'],)
            )
            user = cursor.fetchone()
        else:
            user_login_id = f"google_{google_sub}"
            cursor.execute(
                "INSERT INTO user (id, password, user_name, login_type, email, profile_image) "
                "VALUES (%s, NULL, %s, 'google', %s, %s)",
                (user_login_id, name, email, picture)
            )
            user_id = cursor.lastrowid
            cursor.execute(
                "INSERT INTO social_account (user_user_id, provider, provider_user_id, email) "
                "VALUES (%s, 'google', %s, %s)",
                (user_id, google_sub, email)
            )
            db.commit()
            user = {'user_id': user_id, 'id': user_login_id, 'user_name': name}

        return _build_web_login_response(cursor, user, "Google 로그인 성공")

    except ValueError as e:
        raise HTTPException(status_code=401, detail=f"유효하지 않은 Google 토큰입니다: {str(e)}")
    finally:
        cursor.close()


# Kakao 소셜 로그인
@router.post("/kakao")
async def web_kakao_login(request: KakaoLoginRequest, db=Depends(get_db)):
    cursor = db.cursor(dictionary=True)
    try:
        async with httpx.AsyncClient() as client:
            resp = await client.get(
                "https://kapi.kakao.com/v2/user/me",
                headers={"Authorization": f"Bearer {request.access_token}"}
            )

        if resp.status_code != 200:
            raise HTTPException(status_code=401, detail="유효하지 않은 Kakao 토큰입니다.")

        kakao_data = resp.json()
        kakao_id = str(kakao_data['id'])
        kakao_account = kakao_data.get('kakao_account', {})
        profile = kakao_account.get('profile', {})
        email = kakao_account.get('email', '')
        name = profile.get('nickname', f'kakao_{kakao_id[:8]}')
        picture = profile.get('profile_image_url', '')

        cursor.execute(
            "SELECT user_user_id FROM social_account WHERE provider = 'kakao' AND provider_user_id = %s",
            (kakao_id,)
        )
        existing = cursor.fetchone()

        if existing:
            cursor.execute( # 동일한 사용자의 정보가 이미 존재할 경우
                "SELECT user_id, id, user_name, profile_image, email FROM user WHERE user_id = %s",
                (existing['user_user_id'],)
            )
            user = cursor.fetchone()
        else:
            user_login_id = f"kakao_{kakao_id}"
            cursor.execute( # 사용자 정보가 없을 경우 회원가입 (데이터베이스 내 등록)
                "INSERT INTO user (id, password, user_name, login_type, email, profile_image) "
                "VALUES (%s, NULL, %s, 'kakao', %s, %s)",
                (user_login_id, name, email, picture)
            )
            user_id = cursor.lastrowid
            cursor.execute(
                "INSERT INTO social_account (user_user_id, provider, provider_user_id, email) "
                "VALUES (%s, 'kakao', %s, %s)",
                (user_id, kakao_id, email)
            )
            db.commit()
            user = {'user_id': user_id, 'id': user_login_id, 'user_name': name}

        return _build_web_login_response(cursor, user, "Kakao 로그인 성공")

    except httpx.HTTPError as e:
        raise HTTPException(status_code=502, detail=f"Kakao 서버 통신 오류: {str(e)}")
    finally:
        cursor.close()


# 이메일 인증 코드 발송
@router.post("/send-code")
async def web_send_code(request: SendCodeRequest, db=Depends(get_db)):
    cursor = db.cursor(dictionary=True)
    try:
        cursor.execute(
            "SELECT user_id FROM user WHERE email = %s AND login_type = 'email'",
            (request.email,)
        )
        if cursor.fetchone():
            raise HTTPException(status_code=409, detail="이미 가입된 이메일입니다.")

        cursor.execute(
            "SELECT created_at FROM email_verification WHERE email = %s ORDER BY created_at DESC LIMIT 1",
            (request.email,)
        )
        recent = cursor.fetchone()
        if recent:
            created = recent['created_at']
            if created.tzinfo is None:
                created = created.replace(tzinfo=timezone.utc)
            elapsed = (datetime.now(timezone.utc) - created).total_seconds()
            if elapsed < 60:
                raise HTTPException(status_code=429, detail=f"재발송은 {int(60 - elapsed)}초 후에 가능합니다.")

        code = f"{random.randint(0, 999999):06d}"
        expires_at = datetime.now(timezone.utc) + timedelta(minutes=5)

        cursor.execute(
            "DELETE FROM email_verification WHERE email = %s AND verified = FALSE",
            (request.email,)
        )
        cursor.execute(
            "INSERT INTO email_verification (email, code, expires_at) VALUES (%s, %s, %s)",
            (request.email, code, expires_at)
        )
        db.commit()

        msg = MIMEMultipart()
        msg['From'] = SMTP_EMAIL
        msg['To'] = request.email
        msg['Subject'] = '[SyncLab] 이메일 인증 코드'
        body = f"""
        <html><body style="font-family: Arial, sans-serif; padding: 20px;">
            <h2 style="color: #3366FF;">SyncLab 이메일 인증</h2>
            <p>아래 인증 코드를 입력해주세요.</p>
            <div style="background: #f4f4f4; padding: 20px; text-align: center;
                        font-size: 32px; font-weight: bold; letter-spacing: 8px;
                        border-radius: 8px; margin: 20px 0;">{code}</div>
            <p style="color: #888;">이 코드는 5분 후 만료됩니다.</p>
        </body></html>
        """
        msg.attach(MIMEText(body, 'html'))
        with smtplib.SMTP('smtp.gmail.com', 587) as server:
            server.starttls()
            server.login(SMTP_EMAIL, SMTP_PASSWORD)
            server.sendmail(SMTP_EMAIL, request.email, msg.as_string())

        return {"status": "success", "message": "인증 코드가 발송되었습니다."}

    except HTTPException:
        raise
    except smtplib.SMTPException as e:
        raise HTTPException(status_code=502, detail=f"이메일 발송 실패: {str(e)}")
    finally:
        cursor.close()


# 이메일 인증 코드 검증
@router.post("/verify-code")
async def web_verify_code(request: VerifyCodeRequest, db=Depends(get_db)):
    cursor = db.cursor(dictionary=True)
    try:
        cursor.execute(
            "SELECT id, code, expires_at, attempts FROM email_verification "
            "WHERE email = %s AND verified = FALSE ORDER BY created_at DESC LIMIT 1",
            (request.email,)
        )
        record = cursor.fetchone()

        if not record:
            raise HTTPException(status_code=404, detail="인증 요청을 찾을 수 없습니다.")

        if record['attempts'] >= 5:
            raise HTTPException(status_code=429, detail="인증 시도 횟수를 초과했습니다. 코드를 다시 발송해주세요.")

        expires = record['expires_at']
        if expires.tzinfo is None:
            expires = expires.replace(tzinfo=timezone.utc)
        if datetime.now(timezone.utc) > expires:
            raise HTTPException(status_code=410, detail="인증 코드가 만료되었습니다. 다시 발송해주세요.")

        if record['code'] != request.code:
            cursor.execute(
                "UPDATE email_verification SET attempts = attempts + 1 WHERE id = %s",
                (record['id'],)
            )
            db.commit()
            remaining = 4 - record['attempts']
            raise HTTPException(status_code=400, detail=f"인증 코드가 일치하지 않습니다. ({remaining}회 남음)")

        cursor.execute(
            "UPDATE email_verification SET verified = TRUE WHERE id = %s",
            (record['id'],)
        )
        db.commit()

        return {"status": "success", "message": "이메일 인증이 완료되었습니다."}
    finally:
        cursor.close()


# 이메일 인증 후 회원가입
@router.post("/email-signup")
async def web_email_signup(request: SignupRequest, db=Depends(get_db)):
    cursor = db.cursor(dictionary=True)
    try:
        cursor.execute(
            "SELECT id FROM email_verification WHERE email = %s AND verified = TRUE LIMIT 1",
            (request.email,)
        )
        if not cursor.fetchone():
            raise HTTPException(status_code=403, detail="이메일 인증이 완료되지 않았습니다.")

        cursor.execute(
            "SELECT user_id FROM user WHERE email = %s AND login_type = 'email'",
            (request.email,)
        )
        if cursor.fetchone():
            raise HTTPException(status_code=409, detail="이미 가입된 이메일입니다.")

        hashed_pw = bcrypt.hashpw(request.password.encode(), bcrypt.gensalt()).decode()
        cursor.execute(
            "INSERT INTO user (id, password, user_name, login_type, email) "
            "VALUES (%s, %s, %s, 'email', %s)",
            (request.email, hashed_pw, request.user_name, request.email)
        )
        user_id = cursor.lastrowid

        cursor.execute(
            "DELETE FROM email_verification WHERE email = %s",
            (request.email,)
        )
        db.commit()

        user = {'user_id': user_id, 'id': request.email, 'user_name': request.user_name}
        return _build_web_login_response(cursor, user, "회원가입 성공")

    except HTTPException:
        raise
    finally:
        cursor.close()