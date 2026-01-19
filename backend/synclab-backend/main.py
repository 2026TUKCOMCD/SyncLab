# main.py
from fastapi import FastAPI


app = FastAPI()

@app.get("/")
def read_root():
    return {"message": "여기는 FastAPI(파이썬) 서버입니다!"}

@app.post("/analyze")
def analyze_data(data: dict):
    # Node.js에서 보낸 데이터를 받아서 처리하는 부분
    print(f"Node.js로부터 받은 데이터: {data}")
    return {
        "status": "success",
        "result": "파이썬이 NTP 분석을 완료했습니다!",
        "received": data
    }