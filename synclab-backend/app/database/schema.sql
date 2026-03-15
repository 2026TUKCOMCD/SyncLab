-- synclab 데이터베이스 사용 설정
USE synclab;

-- ==============================================================
-- 테이블 삭제 (순서 준수: 자식 테이블부터 삭제)
-- ==============================================================
DROP TABLE IF EXISTS video;
DROP TABLE IF EXISTS user_session;
DROP TABLE IF EXISTS social_account;
DROP TABLE IF EXISTS email_verification;
DROP TABLE IF EXISTS session;
DROP TABLE IF EXISTS user;
DROP TABLE IF EXISTS edit;

-- ==============================================================
-- 1. user 테이블
-- ==============================================================
CREATE TABLE user (
    user_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '내부 관리용 유저 고유 번호',
    id VARCHAR(128) UNIQUE NOT NULL COMMENT '앱 로그인용 아이디 또는 소셜 고유 ID',
    password VARCHAR(128) NULL COMMENT '비밀번호 (소셜 로그인 시 NULL)',
    user_name VARCHAR(16) NOT NULL COMMENT '사용자 이름',
    login_type VARCHAR(10) NOT NULL DEFAULT 'local' COMMENT '로그인 유형 (local, google, kakao)',
    email VARCHAR(255) NULL COMMENT '이메일 (소셜 로그인 시 제공됨)',
    profile_image VARCHAR(512) NULL COMMENT '프로필 이미지 URL',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '계정 생성 일시'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사용자 계정 정보';

-- ==============================================================
-- 1-1. social_account 테이블: 소셜 로그인 연동 정보
-- ==============================================================
CREATE TABLE social_account (
    social_account_id INT AUTO_INCREMENT PRIMARY KEY,
    user_user_id INT NOT NULL COMMENT '연동된 유저의 user_id',
    provider VARCHAR(10) NOT NULL COMMENT '소셜 제공자 (google, kakao)',
    provider_user_id VARCHAR(255) NOT NULL COMMENT '소셜 제공자 고유 사용자 ID',
    email VARCHAR(255) NULL COMMENT '소셜 계정 이메일',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_provider_user (provider, provider_user_id),
    CONSTRAINT fk_social_user FOREIGN KEY (user_user_id) REFERENCES user(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='소셜 로그인 연동 정보';

-- ==============================================================
-- 2. session 테이블: ID를 VARCHAR(50)으로 변경
-- ==============================================================
CREATE TABLE session (
    -- ⭐️ AUTO_INCREMENT를 제거하고 문자열 PK로 설정
    session_id VARCHAR(20) PRIMARY KEY COMMENT '세션 고유 문자열 ID (예: SID_20260204_a1b2c3)',
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
    video_name VARCHAR(255) NOT NULL COMMENT '사용자가 설정한 영상 이름',
    upload_status VARCHAR(10) NOT NULL DEFAULT 'PENDING' COMMENT '업로드 상태 (PENDING, PROCESSING, COMPLETED)',
    absolute_start_time BIGINT NOT NULL COMMENT '촬영 시작 절대 시간 (ms)', 
    absolute_end_time BIGINT NOT NULL COMMENT '촬영 종료 절대 시간 (ms)',
    duration DOUBLE NOT NULL COMMENT '영상 길이 (초)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_video_session FOREIGN KEY (session_session_id) REFERENCES session(session_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='업로드 영상 및 동기화 메타데이터';

-- ==============================================================
-- 5. edit 테이블
-- ==============================================================
CREATE TABLE edit (
  `edit_id` INT NOT NULL AUTO_INCREMENT,
  `edit_data` JSON NOT NULL,
  `session_session_id` VARCHAR(20) NOT NULL,
  PRIMARY KEY (`edit_id`),
  INDEX `fk_edit_session1_idx` (`session_session_id` ASC) VISIBLE,
  CONSTRAINT `fk_edit_session1`
    FOREIGN KEY (`session_session_id`)
    REFERENCES `synclab`.`session` (`session_id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4;
-- ==============================================================
-- 6. email_verification 테이블: 이메일 인증 코드
-- ==============================================================
CREATE TABLE email_verification (
    id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL COMMENT '인증 대상 이메일',
    code VARCHAR(6) NOT NULL COMMENT '6자리 인증 코드',
    expires_at DATETIME NOT NULL COMMENT '인증 코드 만료 시간',
    verified BOOLEAN DEFAULT FALSE COMMENT '인증 완료 여부',
    attempts INT DEFAULT 0 COMMENT '인증 시도 횟수 (5회 초과 시 차단)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='이메일 인증 코드 관리';

-- ==============================================================
-- 테스트 데이터 삽입
-- ==============================================================
-- 테스트 계정 (id/password: 111/111, 222/222, 333/333, 444/444)
INSERT INTO user (id, password, user_name, login_type) VALUES ('111', '$2b$12$hBB2uM7IjHdD8v8732GFX.cwPDWzV./A2sNa8/m/E2jt3x8P9xBBS', 'User1', 'local');
INSERT INTO user (id, password, user_name, login_type) VALUES ('222', '$2b$12$QbTZL9AzotiC5Rgj6lM.leB.XU4qHss42N4.Lvubeb1c4NWPiHuU6', 'User2', 'local');
INSERT INTO user (id, password, user_name, login_type) VALUES ('333', '$2b$12$brLoSTl0ZboBUmXMLo75RurnDmPajfbwMD3RBpaqph3/pzjnJNyFC', 'User3', 'local');
INSERT INTO user (id, password, user_name, login_type) VALUES ('444', '$2b$12$kL2xiSj1X/qtGvbT.gVZYe4LpyBUbnSgpl6CjS6w/frUdllSCKzC2', 'User4', 'local');

SELECT '✅ SyncLab 스키마(문자열 ID 최적화) 생성 완료!' AS status;
SHOW TABLES;