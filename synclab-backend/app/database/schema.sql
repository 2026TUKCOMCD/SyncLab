-- synclab 데이터베이스 사용
USE synclab;


DROP TABLE IF EXISTS videos;
DROP TABLE IF EXISTS session_participants;
DROP TABLE IF EXISTS sessions;
DROP TABLE IF EXISTS users;
-- ==============================================
-- 1. users 테이블 (사용자)
-- ==============================================
CREATE TABLE users (
    user_id VARCHAR(50) PRIMARY KEY COMMENT '사용자 ID',
    user_name VARCHAR(100) NOT NULL COMMENT '사용자 이름',
    user_pw VARCHAR(255) NOT NULL COMMENT '비밀번호 (해시)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 정보';

-- ==============================================
-- 2. sessions 테이블 (세션)
-- ==============================================
CREATE TABLE sessions (
    session_id VARCHAR(50) PRIMARY KEY COMMENT '세션 ID',
    session_name VARCHAR(200) NOT NULL COMMENT '세션 이름',
    invite_code VARCHAR(10) UNIQUE NOT NULL COMMENT '초대 코드',
    created_by VARCHAR(50) NOT NULL COMMENT '생성자 user_id',
    status ENUM('ACTIVE', 'ENDED') DEFAULT 'ACTIVE' COMMENT '세션 상태',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    ended_at TIMESTAMP NULL COMMENT '종료 시간',
    
    FOREIGN KEY (created_by) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_invite_code (invite_code),
    INDEX idx_created_by (created_by),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='촬영 세션';

-- ==============================================
-- 3. session_participants 테이블 (세션 참가자)
-- ==============================================
CREATE TABLE session_participants (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '참가자 ID',
    session_id VARCHAR(50) NOT NULL COMMENT '세션 ID',
    user_id VARCHAR(50) NULL COMMENT '로그인 사용자 ID (NULL이면 비로그인)',
    guest_name VARCHAR(100) NULL COMMENT '비로그인 사용자 이름',
    is_registered BOOLEAN DEFAULT FALSE COMMENT '로그인 여부 (TRUE: 로그인, FALSE: 비로그인)',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '참가 시간',
    
    FOREIGN KEY (session_id) REFERENCES sessions(session_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL,
    INDEX idx_session_id (session_id),
    INDEX idx_user_id (user_id),
    INDEX idx_is_registered (is_registered)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='세션 참가자';

-- ==============================================
-- 4. videos 테이블 (영상)
-- ==============================================
CREATE TABLE videos (
    video_id VARCHAR(50) PRIMARY KEY COMMENT '영상 ID',
    session_id VARCHAR(50) NOT NULL COMMENT '세션 ID',
    user_id VARCHAR(50) NULL COMMENT '업로드한 사용자 ID',
    guest_name VARCHAR(100) NULL COMMENT '비로그인 업로더 이름',
    file_name VARCHAR(255) NOT NULL COMMENT '파일명',
    original_url TEXT NOT NULL COMMENT '원본 S3 URL',
    proxy_url TEXT NULL COMMENT '프록시 S3 URL',
    status ENUM('UPLOADING', 'PROCESSING', 'COMPLETED', 'FAILED') DEFAULT 'UPLOADING' COMMENT '처리 상태',
    file_size_mb DECIMAL(10,2) NULL COMMENT '파일 크기 (MB)',
    duration_seconds DECIMAL(10,2) NULL COMMENT '영상 길이 (초)',
    absolute_start_time BIGINT NULL COMMENT '촬영 시작 시간 (timestamp ms)',
    absolute_end_time BIGINT NULL COMMENT '촬영 종료 시간 (timestamp ms)',
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '업로드 시간',
    
    FOREIGN KEY (session_id) REFERENCES sessions(session_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL,
    INDEX idx_session_id (session_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='영상 정보';

-- ==============================================
-- 테스트 데이터
-- ==============================================
INSERT INTO users (user_id, user_name, user_pw) VALUES 
('111', '테스트 관리자', '$2b$12$dummyhash');

-- ==============================================
-- 확인
-- ==============================================
SELECT '✅ 테이블 생성 완료!' AS status;
SHOW TABLES;