import pytest
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

# 테스트 간에 공유할 변수
test_context = {
    "token": None,
    "session_id": None
}

def test_path_check():
    assert True

# 1. 로그인 테스트
def test_login_success():
    response = client.post("/api/mobile/auth/login", json={
        "id": "111",
        "password": "111"
    })
    
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "success"
    assert "access_token" in data
    test_context["token"] = data["access_token"]

# 2. 세션 생성 테스트 (404 방지를 위한 URL 및 로직 강화)
def test_create_session():
    if not test_context["token"]:
        pytest.fail("로그인 토큰이 없어 세션 생성 테스트를 진행할 수 없습니다.")

    headers = {"Authorization": f"Bearer {test_context['token']}"}
    
    # URL 끝에 슬래시(/) 유무에 따른 404를 방지하기 위해 정확한 경로 입력
    # 만약 계속 404가 난다면 서버 main.py의 라우터 등록 순서를 확인해야 합니다.
    url = "/api/mobile/session/create"
    
    response = client.post(url, json={
        "name": "Pytest 세션"
    }, headers=headers)
    
    # 에러 메시지 가독성을 위해 assert 문 수정
    assert response.status_code == 200, f"Expected 200 but got {response.status_code}. Response: {response.text}"
    
    data = response.json()
    assert data["status"] == "success"
    
    # ⭐️ 서버에서 생성한 SID_ 문자열 ID 검증
    session_id = data["session"]["session_id"]
    assert isinstance(session_id, str), f"session_id should be string, but got {type(session_id)}"
    assert session_id.startswith("SID_"), f"session_id {session_id} does not start with SID_"
    
    test_context["session_id"] = session_id

# 3. 비디오 업로드 완료 스키마 테스트
def test_complete_upload_schema():
    sid = test_context["session_id"] or "SID_20260204_TEMP"
    
    payload = {
        "session_id": sid, # 이제 서버 스키마(schemas.py)에서도 str이어야 합니다.
        "upload_id": "test_upload_id_12345",
        "video_name": "test_video.mp4",
        "etags": ["etag_part_1", "etag_part_2"],
        "metadata": {
            "file_name": "SyncLab_test.mp4",
            "video_name": "test_video.mp4",
            "absolute_start_time": 1707000000000,
            "absolute_end_time": 1707000060000,
            "duration": 60.5,
            "session_id": sid
        }
    }
    
    headers = {"Authorization": f"Bearer {test_context['token']}"} if test_context["token"] else {}

    # 주소가 /api/mobile/video/upload/complete 인지 확인 필요
    response = client.post("/api/mobile/video/upload/complete", json=payload, headers=headers)
    
    # 422(Validation Error)가 발생한다면 서버의 Pydantic 모델과 payload 필드명이 맞는지 확인해야 함
    assert response.status_code != 422, f"Schema Validation Failed: {response.text}"

# 4. 홈 데이터 조회 테스트
def test_get_home_data():
    headers = {"Authorization": f"Bearer {test_context['token']}"} if test_context["token"] else {}
        
    response = client.get("/api/mobile/home/data", headers=headers)
    assert response.status_code == 200
    data = response.json()
    
    assert "current_session" in data
    assert "history" in data