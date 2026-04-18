
"""
데이터베이스 초기화 스크립트
schema.sql 파일을 읽어서 테이블을 생성합니다.
"""

import os
import re
from app.database.connection import get_db_connection


def _parse_sql_statements(sql_script: str) -> list[str]:
    """
    SQL 스크립트를 개별 statement로 분리
    - 주석 제거 (-- 한 줄 주석)
    - 빈 줄 제거
    - USE / SELECT / SHOW 문 제외 (불필요한 결과셋 방지)
    """
    # 한 줄 주석 제거
    sql_script = re.sub(r'--[^\n]*', '', sql_script)

    # 세미콜론 기준으로 분리
    raw_statements = sql_script.split(';')

    statements = []
    for stmt in raw_statements:
        stmt = stmt.strip()
        if not stmt:
            continue

        upper = stmt.upper().lstrip()

        # USE, SELECT, SHOW 문 제외
        if upper.startswith('USE ') or upper.startswith('SELECT ') or upper.startswith('SHOW '):
            continue

        statements.append(stmt)

    return statements


def init_database():
    """데이터베이스 초기화 (테이블 생성 + 테스트 데이터 삽입)"""

    # schema.sql 파일 경로
    sql_file = os.path.join(os.path.dirname(__file__), 'schema.sql')

    if not os.path.exists(sql_file):
        print(f"❌ SQL 파일을 찾을 수 없습니다: {sql_file}")
        return False

    # SQL 파일 읽기
    with open(sql_file, 'r', encoding='utf-8') as f:
        sql_script = f.read()

    statements = _parse_sql_statements(sql_script)

    print("📋 데이터베이스 초기화 시작...\n")

    with get_db_connection() as conn:
        cursor = conn.cursor()

        for statement in statements:
            upper = statement.upper().lstrip()

            try:
                cursor.execute(statement)

                if 'CREATE TABLE' in upper:
                    # 테이블 이름 추출
                    match = re.search(r'CREATE TABLE\s+[`"]?(\w+)[`"]?', statement, re.IGNORECASE)
                    table_name = match.group(1) if match else '(unknown)'
                    print(f"  ✅ {table_name} 테이블 생성")

                elif 'DROP TABLE' in upper:
                    match = re.search(r'DROP TABLE IF EXISTS\s+[`"]?(\w+)[`"]?', statement, re.IGNORECASE)
                    table_name = match.group(1) if match else '(unknown)'
                    print(f"  🗑️  {table_name} 테이블 삭제")

                elif 'INSERT INTO' in upper:
                    match = re.search(r'INSERT INTO\s+[`"]?(\w+)[`"]?', statement, re.IGNORECASE)
                    table_name = match.group(1) if match else '(unknown)'
                    print(f"  📝 {table_name} 테스트 데이터 삽입")

            except Exception as e:
                err_msg = str(e).lower()

                # 이미 존재하는 테이블은 무시
                if 'already exists' in err_msg:
                    match = re.search(r'CREATE TABLE\s+[`"]?(\w+)[`"]?', statement, re.IGNORECASE)
                    table_name = match.group(1) if match else '(unknown)'
                    print(f"  ⏭️  {table_name} 테이블 이미 존재 (건너뜀)")
                    continue

                print(f"  ❌ SQL 실행 실패: {e}")
                print(f"     Statement: {statement[:80]}...")

        cursor.close()

    print("\n✅ 데이터베이스 초기화 완료!")
    return True


if __name__ == "__main__":
    try:
        success = init_database()
        if not success:
            exit(1)
    except Exception as e:
        print(f"\n❌ 초기화 실패: {e}")
        exit(1)
