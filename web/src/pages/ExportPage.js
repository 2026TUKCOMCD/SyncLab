import { useEffect, useState, useRef, useCallback } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import axios from 'axios';
import '../App.css';

const API_BASE = process.env.REACT_APP_API_URL || '';

function ExportPage() {
  const { state } = useLocation();
  const navigate = useNavigate();
  const [status, setStatus] = useState('starting');
  const [progress, setProgress] = useState(0);
  const [videoUrl, setVideoUrl] = useState(null);
  const [error, setError] = useState(null);
  const [elapsed, setElapsed] = useState(0);
  const eventSourceRef = useRef(null);
  const timerRef = useRef(null);

  const startExport = useCallback(async () => {
    // 이전 연결/타이머 정리
    if (eventSourceRef.current) eventSourceRef.current.close();
    clearInterval(timerRef.current);

    // 상태 초기화
    setStatus('starting');
    setProgress(0);
    setVideoUrl(null);
    setError(null);
    setElapsed(0);

    // 경과 시간 타이머 시작
    timerRef.current = setInterval(() => {
      setElapsed(prev => prev + 1);
    }, 1000);

    try {
      const token = localStorage.getItem('accessToken');
      const response = await axios.post(
        `${API_BASE}/api/web/export`,
        { session_id: state.sessionId, edit_data: state.clips },
        { headers: { Authorization: `Bearer ${token}` } }
      );

      const { job_id } = response.data;
      setStatus('running');

      const es = new EventSource(`${API_BASE}/api/web/export/stream/${job_id}`);
      eventSourceRef.current = es;

      es.onmessage = (e) => {
        const data = JSON.parse(e.data);
        setProgress(data.progress ?? 0);
        setStatus(data.status);

        if (data.status === 'done') {
          setVideoUrl(data.video_url);
          clearInterval(timerRef.current);
          es.close();
        } else if (data.status === 'error') {
          setError(data.error || '알 수 없는 오류');
          clearInterval(timerRef.current);
          es.close();
        }
      };

      es.onerror = () => {
        setStatus('error');
        setError('서버 연결이 끊어졌습니다.');
        clearInterval(timerRef.current);
        es.close();
      };
    } catch (err) {
      setStatus('error');
      setError(err.response?.data?.detail || err.message);
      clearInterval(timerRef.current);
    }
  }, [state]);

  useEffect(() => {
    if (!state?.clips || !state?.sessionId) {
      navigate('/editor');
      return;
    }
    startExport();
    return () => {
      if (eventSourceRef.current) eventSourceRef.current.close();
      clearInterval(timerRef.current);
    };
  }, []);

  // 편집 화면으로 돌아갈 때 클립 데이터를 함께 전달
  const goBackToEditor = () => {
    navigate('/editor', { state: { restoredClips: state?.clips, restoredSessionId: state?.sessionId } });
  };

  const formatElapsed = (sec) => {
    const m = Math.floor(sec / 60).toString().padStart(2, '0');
    const s = (sec % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  };

  return (
    <div className="export-page-container">
      <div className="export-card">
        <img
          src="/synclab_logo.png"
          alt="synclab_logo"
          className="export-logo"
          onClick={() => navigate('/')}
        />

        {/* 렌더링 중 */}
        {(status === 'starting' || status === 'running') && (
          <div className="export-status-section">
            <div className="export-spinner" />
            <h2 className="export-title">영상 렌더링 중...</h2>
            <p className="export-subtitle">편집하신 클립을 고화질로 합치고 있습니다.</p>
            <div className="export-progress-bar-wrapper">
              <div className="export-progress-bar-fill" style={{ width: `${progress}%` }} />
            </div>
            <div className="export-progress-text">{progress}%</div>
            <div className="export-elapsed">경과 시간: {formatElapsed(elapsed)}</div>
            <button className="btn-base btn-secondary export-back-btn" onClick={goBackToEditor}>
              편집으로 돌아가기
            </button>
          </div>
        )}

        {/* 완료 */}
        {status === 'done' && (
          <div className="export-status-section">
            <div className="export-done-icon">✓</div>
            <h2 className="export-title">렌더링 완료!</h2>
            <p className="export-subtitle">총 소요 시간: {formatElapsed(elapsed)}</p>
            <a
              className="btn-base btn-primary export-download-btn"
              href={`${API_BASE}${videoUrl}`}
              download
            >
              완성 영상 다운로드
            </a>
            <button className="btn-base btn-secondary export-back-btn" onClick={goBackToEditor}>
              편집으로 돌아가기
            </button>
          </div>
        )}

        {/* 실패 */}
        {status === 'error' && (
          <div className="export-status-section">
            <div className="export-error-icon">✕</div>
            <h2 className="export-title">렌더링 실패</h2>
            <p className="export-error-msg">{error}</p>
            <button className="btn-base btn-primary export-download-btn" onClick={startExport}>
              다시 시도
            </button>
            <button className="btn-base btn-secondary export-back-btn" onClick={goBackToEditor}>
              편집으로 돌아가기
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

export default ExportPage;
