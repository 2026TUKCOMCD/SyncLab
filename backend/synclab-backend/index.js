const express = require('express');
const cors = require('cors');
const ffmpeg = require('fluent-ffmpeg');
const path = require('path');

const app = express();
const PORT = 8000;

app.use(cors());
app.use(express.json());

// 영상을 담아둘 폴더 경로 (videos 폴더를 미리 만들어야 합니다)
const videoPath = path.join(__dirname, 'videos', 'test.mp4');

app.get('/', (req, res) => {
    res.send('스포츠 멀티뷰 백엔드 서버가 작동 중입니다!');
});

// 영상 정보를 분석해서 알려주는 기능 (친구의 앱과 연결되는 통로)
app.get('/video-info', (req, res) => {
    ffmpeg.ffprobe(videoPath, (err, metadata) => {
        if (err) {
            return res.status(500).json({ error: "영상을 분석할 수 없습니다. videos 폴더에 test.mp4가 있는지 확인하세요!" });
        }
        res.json({
            duration: metadata.format.duration,
            width: metadata.streams[0].width,
            height: metadata.streams[0].height
        });
    });
});

app.listen(PORT, () => {
    console.log(`서버 실행 중: http://localhost:${PORT}`);
});

// index.js에 추가
const axios = require('axios');

app.get('/ask-python', async (req, res) => {
    try {
        // 8001번 포트의 FastAPI에게 데이터를 쏩니다.
        const response = await axios.post('http://localhost:8001/analyze', {
            info: "NTP 시간 정렬해줘!",
            timestamp: Date.now()
        });

        // 파이썬의 대답을 브라우저에 보여줍니다.
        res.json({
            node_msg: "파이썬에게 물어봤어요.",
            python_answer: response.data
        });
    } catch (error) {
        res.status(500).send("파이썬 서버가 꺼져있는 것 같아요!");
    }
});