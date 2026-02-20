# -*- coding: utf-8 -*-
from fastapi import APIRouter, HTTPException, Depends
from app.database.connection import get_db
from app.models.schemas import (
    UserLogin, LoginResponse, GoogleLoginRequest, KakaoLoginRequest,
    SendCodeRequest, VerifyCodeRequest, SignupRequest
)
from datetime import datetime, timedelta
from jose import jwt
import os
import random
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
import httpx
from dotenv import load_dotenv

load_dotenv()

router = APIRouter(prefix="/api/mobile/auth", tags=["Mobile Authentication"])

# ============================================
# JWT 설정
# ============================================
SECRET_KEY = os.getenv("SECRET_KEY")
ALGORITHM = os.getenv("ALGORITHM", "HS256")
ACCESS_TOKEN_EXPIRE_MINUTES = int(os.getenv("ACCESS_TOKEN_EXPIRE_MINUTES", 1440))
GOOGLE_CLIENT_ID = os.getenv("GOOGLE_CLIENT_ID")
KAKAO_REST_API_KEY = os.getenv("KAKAO_REST_API_KEY")
SMTP_EMAIL = os.getenv("SMTP_EMAIL")
SMTP_PASSWORD = os.getenv("SMTP_PASSWORD")

def create_access_token(data: dict):
    to_encode = data.copy()
    expire = datetime.utcnow() + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)


def _build_login_response(cursor, user: dict, message: str = "로그인 성공") -> dict:
    """JWT 생성 + 세션 조회 + 응답 구성 공통 헬퍼"""
    token_data = {"sub": str(user['user_id']), "user_id": user['user_id']}
    access_token = create_access_token(token_data)

    session_query = """
        SELECT session_session_id, joined_at
        FROM user_session
        WHERE user_user_id = %s
        ORDER BY joined_at DESC LIMIT 1
    """
    cursor.execute(session_query, (user['user_id'],))
    session_info = cursor.fetchone()

    current_session_id = session_info['session_session_id'] if session_info else None
    last_joined_at = int(session_info['joined_at'].timestamp() * 1000) if session_info and session_info['joined_at'] else None

    return {
        "status": "success",
        "message": message,
        "access_token": access_token,
        "id": user['id'],
        "user_pk": user['user_id'],
        "user_name": user['user_name'],
        "profile_image": user.get('profile_image'),
        "email": user.get('email'),
        "current_session_id": current_session_id,
        "last_joined_at": last_joined_at
    }


# ============================================
# API 엔드포인트
# ============================================
@router.post("/login")
async def login(request: UserLogin, db=Depends(get_db)):
    cursor = db.cursor(dictionary=True)

    try:
        query = "SELECT user_id, id, user_name, email FROM user WHERE id = %s AND password = %s"
        cursor.execute(query, (request.id, request.password))
        user = cursor.fetchone()

        if not user:
            raise HTTPException(status_code=401, detail="아이디 또는 비밀번호가 틀렸습니다.")

        return _build_login_response(cursor, user)

    finally:
        cursor.close()


# ============================================
# Google 소셜 로그인
# ============================================
@router.post("/google")
async def google_login(request: GoogleLoginRequest, db=Depends(get_db)):
    from google.oauth2 import id_token
    from google.auth.transport import requests as google_requests

    cursor = db.cursor(dictionary=True)

    try:
        # 1. Google ID 토큰 서버사이드 검증
        idinfo = id_token.verify_oauth2_token(
            request.id_token,
            google_requests.Request(),
            GOOGLE_CLIENT_ID
        )

        google_sub = idinfo['sub']
        email = idinfo.get('email', '')
        name = idinfo.get('name', email.split('@')[0] if email else 'Google User')
        picture = idinfo.get('picture', '')

        # 2. 기존 소셜 계정 조회
        cursor.execute(
            "SELECT user_user_id FROM social_account WHERE provider = 'google' AND provider_user_id = %s",
            (google_sub,)
        )
        existing = cursor.fetchone()

        if existing:
            user_id = existing['user_user_id']
            cursor.execute("SELECT user_id, id, user_name, profile_image, email FROM user WHERE user_id = %s", (user_id,))
            user = cursor.fetchone()
        else:
            # 3. 첫 로그인 - 유저 + 소셜 계정 생성
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
            user = {'user_id': user_id, 'id': user_login_id, 'user_name': name, 'profile_image': picture, 'email': email}

        return _build_login_response(cursor, user, "Google 로그인 성공")

    except ValueError as e:
        raise HTTPException(status_code=401, detail=f"유효하지 않은 Google 토큰입니다: {str(e)}")
    finally:
        cursor.close()


# ============================================
# Kakao 소셜 로그인
# ============================================
@router.post("/kakao")
async def kakao_login(request: KakaoLoginRequest, db=Depends(get_db)):
    cursor = db.cursor(dictionary=True)

    try:
        # 1. Kakao 액세스 토큰으로 사용자 정보 조회
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

        # 2. 기존 소셜 계정 조회
        cursor.execute(
            "SELECT user_user_id FROM social_account WHERE provider = 'kakao' AND provider_user_id = %s",
            (kakao_id,)
        )
        existing = cursor.fetchone()

        if existing:
            user_id = existing['user_user_id']
            cursor.execute("SELECT user_id, id, user_name, profile_image, email FROM user WHERE user_id = %s", (user_id,))
            user = cursor.fetchone()
        else:
            # 3. 첫 로그인 - 유저 + 소셜 계정 생성
            user_login_id = f"kakao_{kakao_id}"
            cursor.execute(
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
            user = {'user_id': user_id, 'id': user_login_id, 'user_name': name, 'profile_image': picture, 'email': email}

        return _build_login_response(cursor, user, "Kakao 로그인 성공")

    except httpx.HTTPError as e:
        raise HTTPException(status_code=502, detail=f"Kakao 서버 통신 오류: {str(e)}")
    finally:
        cursor.close()


# ============================================
# 이메일 인증 회원가입
# ============================================

def _send_verification_email(to_email: str, code: str):
    """Gmail SMTP로 인증 코드 이메일 발송"""
    if not SMTP_EMAIL or not SMTP_PASSWORD:
        raise HTTPException(status_code=500, detail="SMTP 설정이 되어 있지 않습니다.")

    msg = MIMEMultipart()
    msg['From'] = SMTP_EMAIL
    msg['To'] = to_email
    msg['Subject'] = '[SyncLab] 이메일 인증 코드'

    body = f"""
    <html>
    <body style="font-family: Arial, sans-serif; padding: 20px;">
        <h2 style="color: #3366FF;">SyncLab 이메일 인증</h2>
        <p>아래 인증 코드를 앱에 입력해주세요.</p>
        <div style="background: #f4f4f4; padding: 20px; text-align: center;
                    font-size: 32px; font-weight: bold; letter-spacing: 8px;
                    border-radius: 8px; margin: 20px 0;">
            {code}
        </div>
        <p style="color: #888;">이 코드는 5분 후 만료됩니다.</p>
    </body>
    </html>
    """
    msg.attach(MIMEText(body, 'html'))

    with smtplib.SMTP('smtp.gmail.com', 587) as server:
        server.starttls()
        server.login(SMTP_EMAIL, SMTP_PASSWORD)
        server.sendmail(SMTP_EMAIL, to_email, msg.as_string())


@router.post("/send-code")
async def send_code(request: SendCodeRequest, db=Depends(get_db)):
    """이메일로 6자리 인증 코드 발송"""
    cursor = db.cursor(dictionary=True)

    try:
        # 이미 가입된 이메일인지 확인
        cursor.execute("SELECT user_id FROM user WHERE email = %s AND login_type = 'email'", (request.email,))
        if cursor.fetchone():
            raise HTTPException(status_code=409, detail="이미 가입된 이메일입니다.")

        # 6자리 인증 코드 생성
        code = f"{random.randint(0, 999999):06d}"
        expires_at = datetime.utcnow() + timedelta(minutes=5)

        # 기존 미인증 코드 삭제 후 새 코드 저장
        cursor.execute("DELETE FROM email_verification WHERE email = %s AND verified = FALSE", (request.email,))
        cursor.execute(
            "INSERT INTO email_verification (email, code, expires_at) VALUES (%s, %s, %s)",
            (request.email, code, expires_at)
        )
        db.commit()

        # 이메일 발송
        _send_verification_email(request.email, code)

        return {"status": "success", "message": "인증 코드가 발송되었습니다."}

    except HTTPException:
        raise
    except smtplib.SMTPException as e:
        raise HTTPException(status_code=502, detail=f"이메일 발송에 실패했습니다: {str(e)}")
    finally:
        cursor.close()


@router.post("/verify-code")
async def verify_code(request: VerifyCodeRequest, db=Depends(get_db)):
    """인증 코드 검증"""
    cursor = db.cursor(dictionary=True)

    try:
        cursor.execute(
            "SELECT id, code, expires_at FROM email_verification "
            "WHERE email = %s AND verified = FALSE ORDER BY created_at DESC LIMIT 1",
            (request.email,)
        )
        record = cursor.fetchone()

        if not record:
            raise HTTPException(status_code=404, detail="인증 요청을 찾을 수 없습니다.")

        if datetime.utcnow() > record['expires_at']:
            raise HTTPException(status_code=410, detail="인증 코드가 만료되었습니다. 다시 발송해주세요.")

        if record['code'] != request.code:
            raise HTTPException(status_code=400, detail="인증 코드가 일치하지 않습니다.")

        # 인증 완료 처리
        cursor.execute("UPDATE email_verification SET verified = TRUE WHERE id = %s", (record['id'],))
        db.commit()

        return {"status": "success", "message": "이메일 인증이 완료되었습니다."}

    finally:
        cursor.close()


@router.post("/signup")
async def signup(request: SignupRequest, db=Depends(get_db)):
    """이메일 인증 후 회원가입 완료"""
    cursor = db.cursor(dictionary=True)

    try:
        # 이메일 인증 완료 여부 확인
        cursor.execute(
            "SELECT id FROM email_verification WHERE email = %s AND verified = TRUE LIMIT 1",
            (request.email,)
        )
        if not cursor.fetchone():
            raise HTTPException(status_code=403, detail="이메일 인증이 완료되지 않았습니다.")

        # 이미 가입된 이메일 확인
        cursor.execute("SELECT user_id FROM user WHERE email = %s AND login_type = 'email'", (request.email,))
        if cursor.fetchone():
            raise HTTPException(status_code=409, detail="이미 가입된 이메일입니다.")

        # 유저 생성
        cursor.execute(
            "INSERT INTO user (id, password, user_name, login_type, email) "
            "VALUES (%s, %s, %s, 'email', %s)",
            (request.email, request.password, request.user_name, request.email)
        )
        user_id = cursor.lastrowid

        # 사용된 인증 코드 정리
        cursor.execute("DELETE FROM email_verification WHERE email = %s", (request.email,))
        db.commit()

        user = {'user_id': user_id, 'id': request.email, 'user_name': request.user_name}
        return _build_login_response(cursor, user, "회원가입 성공")

    except HTTPException:
        raise
    finally:
        cursor.close()
