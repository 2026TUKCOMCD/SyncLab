import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import '../App.css';

function LiveListPage() {
  const navigate = useNavigate();
  const [sessions, setSessions] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [lastUpdated, setLastUpdated] = useState(null);

  const fetchSessions = async () => {
    const token = localStorage.getItem('accessToken');
    if (!token) {
      navigate('/login');
      return;
    }
    try {
      const response = await axios.get('/api/live/sessions', {
        headers: { Authorization: `Bearer ${token}` },
      });
      setSessions(response.data.sessions || []);
      setLastUpdated(new Date());
    } catch (err) {
      console.error('라이브 목록 조회 오류:', err);
      if (err.response?.status === 401) {
        navigate('/login');
      }
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchSessions();
    const interval = setInterval(fetchSessions, 5000);
    return () => clearInterval(interval);
  }, []);

  const formatElapsed = (isoString) => {
    if (!isoString) return '';
    const diff = Math.floor((Date.now() - new Date(isoString).getTime()) / 1000);
    if (diff < 60) return `${diff}초 전 시작`;
    if (diff < 3600) return `${Math.floor(diff / 60)}분 전 시작`;
    return `${Math.floor(diff / 3600)}시간 전 시작`;
  };

  return (
    <div style={s.page}>
      {/* 헤더 */}
      <div style={s.header}>
        <div style={s.headerLeft}>
          <img
            src="/synclab_logo.png"
            alt="SyncLab"
            style={s.logo}
            onClick={() => navigate('/')}
          />
        </div>
        <div style={s.headerRight}>
          <button style={s.editorBtn} onClick={() => navigate('/editor')}>
            편집하러 가기
          </button>
        </div>
      </div>

      {/* 페이지 타이틀 */}
      <div style={s.titleBar}>
        <div style={s.titleLeft}>
          <span style={s.liveDot}>●</span>
          <h1 style={s.pageTitle}>라이브 방송</h1>
          {!isLoading && (
            <span style={s.countBadge}>{sessions.length}개 방송 중</span>
          )}
        </div>
        <button style={s.refreshBtn} onClick={fetchSessions}>
          새로고침
        </button>
      </div>

      {/* 컨텐츠 */}
      <div style={s.content}>
        {isLoading ? (
          <div style={s.center}>
            <div style={s.spinner} />
            <p style={{ color: '#94A3B8', marginTop: 16 }}>불러오는 중...</p>
          </div>
        ) : sessions.length === 0 ? (
          <div style={s.empty}>
            <span style={{ fontSize: 64 }}>📡</span>
            <p style={s.emptyTitle}>현재 진행 중인 라이브가 없습니다</p>
            <p style={s.emptyDesc}>세션에서 라이브 → 카메라를 시작하면 여기에 표시됩니다.</p>
          </div>
        ) : (
          <div style={s.grid}>
            {sessions.map((session) => (
              <div
                key={session.session_id}
                style={s.card}
                onClick={() => navigate(`/live/${session.session_id}`)}
                onMouseEnter={(e) => {
                  e.currentTarget.style.transform = 'translateY(-4px)';
                  e.currentTarget.style.boxShadow = '0 8px 32px rgba(0,0,0,0.4)';
                  e.currentTarget.style.borderColor = '#3366FF';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.transform = 'translateY(0)';
                  e.currentTarget.style.boxShadow = '0 2px 8px rgba(0,0,0,0.2)';
                  e.currentTarget.style.borderColor = '#1E293B';
                }}
              >
                {/* 썸네일 영역 */}
                <div style={s.thumbnail}>
                  <div style={s.thumbnailInner}>
                    <span style={{ fontSize: 40 }}>📷</span>
                    <p style={{ color: '#64748B', fontSize: 13, marginTop: 8 }}>
                      카메라 {session.camera_count}대 연결 중
                    </p>
                  </div>
                  {/* LIVE 배지 */}
                  <div style={s.liveBadge}>
                    <span style={{ fontSize: 7, marginRight: 4 }}>●</span>LIVE
                  </div>
                  {/* 카메라 수 배지 */}
                  <div style={s.cameraBadge}>
                    📷 {session.camera_count}
                  </div>
                </div>

                {/* 카드 정보 */}
                <div style={s.cardBody}>
                  <div style={s.cardTop}>
                    {/* 아바타 */}
                    <div style={s.avatar}>
                      {(session.host_name || '?')[0].toUpperCase()}
                    </div>
                    <div style={s.cardInfo}>
                      <p style={s.sessionName}>
                        {session.session_name}
                      </p>
                      <p style={s.hostName}>{session.host_name}</p>
                    </div>
                  </div>
                  <div style={s.cardBottom}>
                    <span style={s.viewerCount}>
                      👁 {session.viewer_count}명 시청 중
                    </span>
                    <span style={s.elapsed}>
                      {formatElapsed(session.created_at)}
                    </span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 하단 업데이트 시각 */}
      {lastUpdated && (
        <div style={s.footer}>
          마지막 업데이트: {lastUpdated.toLocaleTimeString()} · 5초마다 자동 갱신
        </div>
      )}
    </div>
  );
}

const s = {
  page: {
    minHeight: '100vh',
    backgroundColor: '#0F172A',
    color: '#F1F5F9',
    display: 'flex',
    flexDirection: 'column',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '12px 24px',
    backgroundColor: '#1E293B',
    borderBottom: '1px solid #334155',
  },
  headerLeft: { display: 'flex', alignItems: 'center' },
  headerRight: { display: 'flex', alignItems: 'center', gap: 12 },
  logo: { height: 32, cursor: 'pointer', objectFit: 'contain' },
  editorBtn: {
    padding: '8px 16px',
    backgroundColor: '#3366FF',
    color: '#fff',
    border: 'none',
    borderRadius: 8,
    cursor: 'pointer',
    fontSize: 14,
    fontWeight: 600,
  },
  titleBar: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '20px 24px 8px',
  },
  titleLeft: { display: 'flex', alignItems: 'center', gap: 10 },
  liveDot: { color: '#EF4444', fontSize: 18, animation: 'pulse 1.5s infinite' },
  pageTitle: { margin: 0, fontSize: 22, fontWeight: 700, color: '#F1F5F9' },
  countBadge: {
    backgroundColor: '#1E293B',
    color: '#94A3B8',
    fontSize: 13,
    padding: '3px 10px',
    borderRadius: 20,
    border: '1px solid #334155',
  },
  refreshBtn: {
    padding: '6px 14px',
    backgroundColor: 'transparent',
    color: '#94A3B8',
    border: '1px solid #334155',
    borderRadius: 8,
    cursor: 'pointer',
    fontSize: 13,
  },
  content: { flex: 1, padding: '16px 24px 24px' },
  grid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
    gap: 20,
  },
  card: {
    backgroundColor: '#1E293B',
    borderRadius: 12,
    border: '1px solid #1E293B',
    overflow: 'hidden',
    cursor: 'pointer',
    transition: 'transform 0.2s, box-shadow 0.2s, border-color 0.2s',
    boxShadow: '0 2px 8px rgba(0,0,0,0.2)',
  },
  thumbnail: {
    position: 'relative',
    width: '100%',
    paddingTop: '56.25%', // 16:9
    backgroundColor: '#0F172A',
  },
  thumbnailInner: {
    position: 'absolute',
    inset: 0,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
  },
  liveBadge: {
    position: 'absolute',
    top: 10,
    left: 10,
    backgroundColor: '#DC2626',
    color: '#fff',
    fontSize: 11,
    fontWeight: 700,
    padding: '3px 8px',
    borderRadius: 4,
    letterSpacing: 1,
    display: 'flex',
    alignItems: 'center',
  },
  cameraBadge: {
    position: 'absolute',
    top: 10,
    right: 10,
    backgroundColor: 'rgba(0,0,0,0.6)',
    color: '#fff',
    fontSize: 12,
    padding: '3px 8px',
    borderRadius: 4,
  },
  cardBody: { padding: '12px 14px' },
  cardTop: { display: 'flex', gap: 10, marginBottom: 10 },
  avatar: {
    width: 38,
    height: 38,
    borderRadius: '50%',
    backgroundColor: '#3366FF',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontWeight: 700,
    fontSize: 16,
    flexShrink: 0,
  },
  cardInfo: { flex: 1, overflow: 'hidden' },
  sessionName: {
    margin: 0,
    fontSize: 14,
    fontWeight: 700,
    color: '#F1F5F9',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  hostName: {
    margin: '3px 0 0',
    fontSize: 12,
    color: '#64748B',
  },
  cardBottom: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  viewerCount: { fontSize: 12, color: '#94A3B8' },
  elapsed: { fontSize: 11, color: '#475569' },
  center: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    paddingTop: 100,
  },
  empty: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    paddingTop: 80,
    gap: 12,
  },
  emptyTitle: { fontSize: 18, fontWeight: 700, color: '#94A3B8', margin: 0 },
  emptyDesc: { fontSize: 14, color: '#475569', margin: 0, textAlign: 'center' },
  spinner: {
    width: 40,
    height: 40,
    border: '3px solid #334155',
    borderTop: '3px solid #3366FF',
    borderRadius: '50%',
    animation: 'spin 1s linear infinite',
  },
  footer: {
    textAlign: 'center',
    padding: '12px',
    fontSize: 12,
    color: '#475569',
    borderTop: '1px solid #1E293B',
  },
};

export default LiveListPage;