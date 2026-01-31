
"""
MySQL 데이터베이스 연결 관리
- 연결 풀 생성
- 컨텍스트 매니저 (get_db_connection)
- FastAPI Depends용 (get_db)
"""
from mysql.connector import pooling
from contextlib import contextmanager
import os
from dotenv import load_dotenv

load_dotenv()

# MySQL 연결 풀 생성
db_pool = pooling.MySQLConnectionPool(
    pool_name="synclab_pool",
    pool_size=10,
    pool_reset_session=True,
    host=os.getenv("DB_HOST", "127.0.0.1"),
    port=int(os.getenv("DB_PORT", 3306)),
    user=os.getenv("DB_USER", "root"),
    password=os.getenv("DB_PASSWORD", "rootroot"),
    database=os.getenv("DB_NAME", "synclab")
)


@contextmanager
def get_db_connection():
    """
    데이터베이스 연결 컨텍스트 매니저
    
    사용법:
    with get_db_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM user")
        # 자동 commit/rollback
    """
    connection = db_pool.get_connection()
    try:
        yield connection
        connection.commit()  # ✅ 자동 커밋
    except Exception as e:
        connection.rollback()  # ✅ 에러 시 롤백
        raise e
    finally:
        connection.close()


def get_db():
    """
    FastAPI Depends용 DB 연결
    
    사용법:
    @router.post("/example")
    def example(db = Depends(get_db)):
        cursor = db.cursor()
        cursor.execute("SELECT * FROM user")
        db.commit()  # 수동 커밋 필요
    """
    conn = db_pool.get_connection()
    try:
        yield conn
        conn.commit()  # ✅ 자동 커밋 추가
    except Exception as e:
        conn.rollback()  # ✅ 에러 시 롤백 추가
        raise e
    finally:
        conn.close()


def test_connection():
    """
    연결 테스트
    
    반환:
    - True: 연결 성공
    - False: 연결 실패
    """
    try:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT 1")
            result = cursor.fetchone()
            cursor.close()
            return result is not None
    except Exception as e:
        print(f"❌ DB 연결 실패: {e}")
        return False