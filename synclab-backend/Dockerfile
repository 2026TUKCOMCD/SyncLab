# =============================================
# SyncLab Backend - FastAPI Dockerfile
# =============================================
FROM python:3.11-slim

# 시스템 패키지 설치 (ffmpeg 포함)
RUN apt-get update && apt-get install -y \
    ffmpeg \
    gcc \
    g++ \
    curl \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# 작업 디렉토리 설정
WORKDIR /app

# 의존성 먼저 설치 (캐시 활용)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 앱 코드 복사
COPY app/ ./app/

# exports 디렉토리 생성 (영상 출력용)
RUN mkdir -p /app/exports

# 포트 노출
EXPOSE 3000

# 서버 실행
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "3000"]
