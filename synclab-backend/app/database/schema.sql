-- synclab 데이터베이스 사용
USE synclab;

-- 기존 테이블 삭제 (순서 중요: 외래 키 제약 조건 때문)
DROP TABLE IF EXISTS videos;
DROP TABLE IF EXISTS session_participants;
DROP TABLE IF EXISTS sessions;
DROP TABLE IF EXISTS users;

-- ==============================================
-- 1. users 테이블 (LoginRequest/Response 대응)
-- ==============================================
CREATE TABLE users (
    user_id VARCHAR(50) PRIMARY KEY COMMENT '사용자 ID',
    user_name VARCHAR(100) NOT NULL COMMENT '사용자 이름',
    user_pw VARCHAR(255) NOT NULL COMMENT '비밀번호 (해시)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================
-- 2. sessions 테이블 (SessionInfo 대응)
-- ==============================================
CREATE TABLE sessions (
    session_id VARCHAR(50) PRIMARY KEY COMMENT '세션 ID',
    session_name VARCHAR(200) NOT NULL COMMENT '세션 이름',
    invite_code VARCHAR(10) UNIQUE NOT NULL COMMENT '앱의 connectCode에 해당',
    created_by VARCHAR(50) NOT NULL COMMENT '생성자 user_id',
    status ENUM('ACTIVE', 'ENDED') DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at BIGINT NULL COMMENT '앱의 expiresAt (Long) 대응',
    
    FOREIGN KEY (created_by) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================
-- 3. session_participants 테이블
-- ==============================================
CREATE TABLE session_participants (
    id INT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NULL,
    guest_name VARCHAR(100) NULL,
    is_registered BOOLEAN DEFAULT FALSE,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (session_id) REFERENCES sessions(session_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================
-- 4. videos 테이블 (CompleteUploadRequest & VideoMetadata 대응)
-- ==============================================
CREATE TABLE videos (
    video_id VARCHAR(50) PRIMARY KEY COMMENT '영상 고유 ID',
    upload_id VARCHAR(100) NULL COMMENT '앱의 uploadId 대응',
    session_id VARCHAR(50) NOT NULL COMMENT '앱의 sessionId 대응',
    user_id VARCHAR(50) NULL COMMENT '업로드한 사용자 ID',
    video_name VARCHAR(255) NOT NULL COMMENT '앱의 videoName 대응',
    file_name VARCHAR(255) NOT NULL COMMENT '앱의 fileName 대응',
    
    -- 영상 메타데이터 (VideoMetadata 클래스 필드와 1:1 매칭)
    abs_start_time BIGINT NOT NULL COMMENT '앱의 absoluteStartTime 대응',
    abs_end_time BIGINT NOT NULL COMMENT '앱의 absoluteEndTime 대응',
    duration DOUBLE NOT NULL COMMENT '앱의 duration (Double) 대응',
    
    status ENUM('UPLOADING', 'PROCESSING', 'COMPLETED', 'FAILED') DEFAULT 'UPLOADING',
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (session_id) REFERENCES sessions(session_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL,
    INDEX idx_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================
-- 테스트 데이터 및 확인
-- ==============================================
INSERT INTO users (user_id, user_name, user_pw) VALUES 
('111', '테스트 관리자', '111');

SELECT '✅ 앱 모델 기반 테이블 생성 완료!' AS status;
SHOW TABLES;