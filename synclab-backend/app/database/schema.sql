-- synclab 데이터베이스 사용 설정
USE synclab;

-- ==============================================================
-- 테이블 삭제 (순서 준수: 자식 테이블부터 삭제해야 외래키 오류가 발생하지 않음)
-- ==============================================================
DROP TABLE IF EXISTS video;            -- 영상 정보 테이블 삭제
DROP TABLE IF EXISTS user_session;      -- 유저-세션 관계 테이블 삭제
DROP TABLE IF EXISTS session;           -- 세션 정보 테이블 삭제
DROP TABLE IF EXISTS user;              -- 유저 정보 테이블 삭제

-- ==============================================================
-- 1. user 테이블: 사용자 계정 정보를 관리
-- ==============================================================
CREATE TABLE user (
    user_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '내부 관리용 유저 고유 번호 (정수 PK)', --
    id VARCHAR(16) UNIQUE NOT NULL COMMENT '앱 로그인용 아이디 (String: LoginRequest.userId)', --
    password VARCHAR(16) NOT NULL COMMENT '비밀번호 (String: LoginRequest.userPw)', --
    user_name VARCHAR(100) NOT NULL COMMENT '사용자 이름 (LoginResponse.userName)', --
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '계정 생성 일시'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사용자 계정 정보';

-- ==============================================================
-- 2. session 테이블: 촬영 세션(방) 정보를 관리
-- ==============================================================
CREATE TABLE session (
    session_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '세션 고유 번호 (정수 PK: 1부터 자동 증가)', --
    session_name VARCHAR(200) NULL COMMENT '세션 이름 (SessionActionRequest.name)', --
    invite_code VARCHAR(8) UNIQUE NOT NULL COMMENT '8자리 랜덤 초대 코드 (앱 표시용: connectCode)', --
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '세션 생성 일시'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='촬영 세션(방) 정보';

-- ==============================================================
-- 3. user_session 테이블: 유저가 어떤 세션에 참가 중인지 관리 (N:M 관계)
-- ==============================================================
CREATE TABLE user_session (
    session_session_id INT NOT NULL COMMENT '참조하는 세션의 고유 번호', --
    user_user_id INT NOT NULL COMMENT '참조하는 유저의 고유 번호', --
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '세션 참가 일시',
    PRIMARY KEY (session_session_id, user_user_id), -- 복합키 설정
    CONSTRAINT fk_session_id FOREIGN KEY (session_session_id) REFERENCES session(session_id) ON DELETE CASCADE, --
    CONSTRAINT fk_user_id FOREIGN KEY (user_user_id) REFERENCES user(user_id) ON DELETE CASCADE --
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='유저-세션 참가 정보';

-- ==============================================================
-- 4. video 테이블: 업로드된 영상 파일 및 동기화 메타데이터를 관리
-- ==============================================================
-- 4. video 테이블: 코드의 변수명과 1:1 매칭되도록 수정
CREATE TABLE video (
    video_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '영상 고유 번호',
    sessionId INT NOT NULL COMMENT '영상이 소속된 세션 번호 (FK)', 
    s3_url VARCHAR(300) UNIQUE NOT NULL COMMENT '전체 경로 (fullPath)',
    video_name VARCHAR(255) NOT NULL COMMENT '파일명 (fileName)',
    upload_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '업로드 상태',
    absoluteStartTime BIGINT NOT NULL COMMENT '촬영 시작 절대 시간 (ms)', 
    absoluteEndTime BIGINT NOT NULL COMMENT '촬영 종료 절대 시간 (ms)', 
    duration DOUBLE NOT NULL COMMENT '영상 길이 (초)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 외래키 설정 (부모 테이블인 session의 session_id를 참조)
    CONSTRAINT fk_video_session FOREIGN KEY (sessionId) REFERENCES session(session_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='업로드 영상 및 동기화 메타데이터';
-- ==============================================================
-- 테스트 데이터 삽입
-- ==============================================================
INSERT INTO user (id, password, user_name) VALUES ('111', '111', '테스트 관리자'); -- 테스트용 계정 생성

SELECT '✅ SyncLab 주석 포함 스키마 생성 완료!' AS status;
SHOW TABLES;