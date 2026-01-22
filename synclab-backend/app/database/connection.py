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
    password=os.getenv("DB_PASSWORD", "root"),
    database=os.getenv("DB_NAME", "synclab")
)

@contextmanager
def get_db_connection():
    """데이터베이스 연결 컨텍스트 매니저"""
    connection = db_pool.get_connection()
    try:
        yield connection
        connection.commit()
    except Exception as e:
        connection.rollback()
        raise e
    finally:
        connection.close()

def test_connection():
    """연결 테스트"""
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