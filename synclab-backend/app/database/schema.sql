-- synclab 데이터베이스 사용 설정
USE synclab;

-- ==============================================================
-- 테이블 삭제 (순서 준수: 자식 테이블부터 삭제)
-- ==============================================================
DROP TABLE IF EXISTS video;
DROP TABLE IF EXISTS user_session;
DROP TABLE IF EXISTS session;
DROP TABLE IF EXISTS user;

-- ==============================================================
-- 1. user 테이블
-- ==============================================================
CREATE TABLE user (
    user_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '내부 관리용 유저 고유 번호',
    id VARCHAR(16) UNIQUE NOT NULL COMMENT '앱 로그인용 아이디',
    password VARCHAR(16) NOT NULL COMMENT '비밀번호',
    user_name VARCHAR(100) NOT NULL COMMENT '사용자 이름',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '계정 생성 일시'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사용자 계정 정보';

-- ==============================================================
-- 2. session 테이블: ID를 VARCHAR(50)으로 변경
-- ==============================================================
CREATE TABLE session (
    -- ⭐️ AUTO_INCREMENT를 제거하고 문자열 PK로 설정
    session_id VARCHAR(50) PRIMARY KEY COMMENT '세션 고유 문자열 ID (예: SID_20260204_a1b2c3)',
    session_name VARCHAR(200) NULL COMMENT '세션 이름',
    invite_code VARCHAR(8) UNIQUE NOT NULL COMMENT '8자리 랜덤 초대 코드',
    expires_at DATETIME NULL COMMENT '세션 만료 일시',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '세션 생성 일시'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='촬영 세션(방) 정보';

-- ==============================================================
-- 3. user_session 테이블: 세션 ID 타입 일치 및 문법 교정
-- ==============================================================
CREATE TABLE user_session (
    session_session_id VARCHAR(50) NOT NULL COMMENT '참조하는 세션의 고유 번호', -- ✅ COMMENT 키워드 추가
    user_user_id INT NOT NULL COMMENT '참조하는 유저의 고유 번호',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '세션 참가 일시',
    PRIMARY KEY (session_session_id, user_user_id),
    CONSTRAINT fk_session_id FOREIGN KEY (session_session_id) REFERENCES session(session_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_id FOREIGN KEY (user_user_id) REFERENCES user(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='유저-세션 참가 정보';

-- ==============================================================
-- 4. video 테이블: 중복 선언 제거 및 외래키 타입 일치
-- ==============================================================
CREATE TABLE video (
    video_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '영상 고유 번호',
    session_session_id VARCHAR(50) NOT NULL COMMENT '영상이 소속된 세션 번호', -- ✅ FK 타입 일치
    s3_url VARCHAR(300) UNIQUE NOT NULL COMMENT 'S3 저장 주소 (sessionId/fileName)',
    video_name VARCHAR(255) NOT NULL COMMENT '사용자가 설정한 영상 이름',
    upload_status VARCHAR(10) NOT NULL DEFAULT 'PENDING' COMMENT '업로드 상태 (PENDING, PROCESSING, COMPLETED)',
    absolute_start_time BIGINT NOT NULL COMMENT '촬영 시작 절대 시간 (ms)', 
    absolute_end_time BIGINT NOT NULL COMMENT '촬영 종료 절대 시간 (ms)',
    duration DOUBLE NOT NULL COMMENT '영상 길이 (초)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_video_session FOREIGN KEY (session_session_id) REFERENCES session(session_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='업로드 영상 및 동기화 메타데이터';

-- ==============================================================
-- 테스트 데이터 삽입
-- ==============================================================
INSERT INTO user (id, password, user_name) VALUES ('111', '111', '테스트 관리자');

SELECT '✅ SyncLab 스키마(문자열 ID 최적화) 생성 완료!' AS status;
SHOW TABLES;