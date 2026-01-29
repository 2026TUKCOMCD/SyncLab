
"""
데이터베이스 초기화 스크립트
schema.sql 파일을 읽어서 테이블을 생성합니다.
"""

from app.database.connection import get_db_connection
import os

def init_database():
    """데이터베이스 초기화 (테이블 생성)"""
    
    # schema.sql 파일 경로
    sql_file = os.path.join(os.path.dirname(__file__), 'schema.sql')
    
    if not os.path.exists(sql_file):
        print(f"❌ SQL 파일을 찾을 수 없습니다: {sql_file}")
        return
    
    # SQL 파일 읽기
    with open(sql_file, 'r', encoding='utf-8') as f:
        sql_script = f.read()
    
    print("📋 데이터베이스 초기화 시작...\n")
    
    # SQL 실행
    with get_db_connection() as conn:
        cursor = conn.cursor()
        
        # 세미콜론으로 분리된 SQL 문 실행
        statements = [s.strip() for s in sql_script.split(';') if s.strip()]
        
        for statement in statements:
            # 주석이나 빈 줄 건너뛰기
            if statement.startswith('--') or not statement:
                continue
            
            try:
                cursor.execute(statement)
                
                # 테이블 생성 문만 출력
                if 'CREATE TABLE' in statement:
                    # 테이블 이름 추출
                    table_name = statement.split('CREATE TABLE')[1].split('(')[0].strip()
                    print(f"✅ {table_name} 테이블 생성")
                elif 'INSERT INTO' in statement:
                    print(f"✅ 테스트 데이터 삽입")
                    
            except Exception as e:
                # 이미 존재하는 테이블은 무시
                if 'already exists' in str(e).lower():
                    continue
                print(f"❌ SQL 실행 실패: {e}")
                print(f"   Statement: {statement[:100]}...")
        
        cursor.close()
    
    print("\n✅ 데이터베이스 초기화 완료!")
    print("\n확인 명령어:")
    print("  mysql -u root -p")
    print("  USE synclab;")
    print("  SHOW TABLES;")

if __name__ == "__main__":
    try:
        init_database()
    except Exception as e:
        print(f"\n❌ 초기화 실패: {e}")