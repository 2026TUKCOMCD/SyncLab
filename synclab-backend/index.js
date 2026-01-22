const express = require('express');
const mysql = require('mysql2');
const cors = require('cors');
const { S3Client, PutObjectCommand } = require('@aws-sdk/client-s3');
const { getSignedUrl } = require('@aws-sdk/s3-request-presigner');
require('dotenv').config(); // 보안을 위해 .env 파일 사용 권장

const app = express();
app.use(cors());
app.use(express.json());

// 1. MySQL 연결 설정
const db = mysql.createPool({
    host: '127.0.0.1',
    user: 'root',
    password: process.env.DB_PASSWORD || 'root', 
    database: 'synclab'
});

// 2. AWS S3 클라이언트 설정
const s3Client = new S3Client({
    region: 'ap-northeast-2', // 서울 리전
    credentials: {
        accessKeyId: process.env.AWS_ACCESS_KEY_ID, // .env에 저장
        secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY
    }
});

/**
 * [POST] 프론트엔드가 호출하는 API
 * 기능: 1. DB에 영상 정보 예약 저장  2. S3 업로드용 통행증(URL) 발급
 */
app.post('/api/video/presigned-url', async (req, res) => {
    const { sessionId, cameraId, ntpStartTime, ntpEndTime, fileName } = req.body;
    
    // S3에 저장될 경로 설정 (예: session1/cam1_1737525000.mp4)
    const s3Key = `${sessionId}/${Date.now()}_${fileName}`;

    try {
        // A. MySQL에 메타데이터 먼저 저장
        const sql = `
            INSERT INTO videos (session_id, s3_key, ntp_start_time, ntp_end_time, status) 
            VALUES (?, ?, ?, ?, 'pending')
        `;

        const [result] = await db.promise().query(sql, [sessionId, s3Key, ntpStartTime, ntpEndTime]);

        // B. S3 Presigned URL 생성 (유효시간 5분)
        const command = new PutObjectCommand({
            Bucket: process.env.S3_BUCKET_NAME,
            Key: s