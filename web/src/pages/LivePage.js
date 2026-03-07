import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { LiveKitRoom, useTracks, VideoTrack } from '@livekit/components-react';
import { Track } from 'livekit-client';
import '@livekit/components-styles';

const SERVER_URL = process.env.REACT_APP_LIVEKIT_URL || 'ws://localhost:7880';
// REACT_APP_API_URL 없으면 빈 문자열 → Docker nginx 상대경로 프록시 사용
const API_URL = process.env.REACT_APP_API_URL || '';

const DEFAULT_OVERLAY = {
  showScoreboard: false,
  homeTeam: 'HOME',
  awayTeam: 'AWAY',
  homeScore: 0,
  awayScore: 0,
  showLowerThird: false,
  lowerThird: '',
  subTitle: '',
};

// ─── 라이브 영상 영역 (LiveKitRoom 내부에서 사용) ───────────────────────────
function LiveVideoArea({ activeCamera, overlay }) {
  const tracks = useTracks(
    [{ source: Track.Source.Camera, withPlaceholder: false }],
    { onlySubscribed: false }
  );

  const activeTrack = tracks.find(
    (t) => t.participant?.identity === activeCamera
  );
  const otherTracks = tracks.filter(
    (t) => t.participant?.identity !== activeCamera
  );

  return (
    <div style={styles.videoArea}>
      {/* 메인 화면: 활성 카메라 */}
      <div style={styles.mainVideo}>
        {activeTrack ? (
          <VideoTrack trackRef={activeTrack} style={styles.mainVideoEl} />
        ) : tracks.length > 0 ? (
          <VideoTrack trackRef={tracks[0]} style={styles.mainVideoEl} />
        ) : (
          <div style={styles.noVideoPlaceholder}>
            <span style={{ fontSize: 48 }}>📷</span>
            <p style={{ color: '#94A3B8', marginTop: 12 }}>카메라 스트림 대기 중...</p>
          </div>
        )}

        {/* LIVE 배지 */}
        <div style={styles.liveBadge}>
          <span style={styles.liveDot}>●</span> LIVE
        </div>

        {/* ── 스코어보드 오버레이 ── */}
        {overlay.showScoreboard && (
          <div style={styles.scoreboard}>
            <div style={styles.sbTeam}>{overlay.homeTeam || 'HOME'}</div>
            <div style={styles.sbScoreBlock}>
              <span style={styles.sbScore}>{overlay.homeScore ?? 0}</span>
              <span style={styles.sbSep}>:</span>
              <span style={styles.sbScore}>{overlay.awayScore ?? 0}</span>
            </div>
            <div style={styles.sbTeam}>{overlay.awayTeam || 'AWAY'}</div>
          </div>
        )}

        {/* ── 자막 오버레이 ── */}
        {overlay.showLowerThird && overlay.lowerThird && (
          <div style={styles.lowerThird}>
            <div style={styles.ltTitle}>{overlay.lowerThird}</div>
            {overlay.subTitle ? (
              <div style={styles.ltSub}>{overlay.subTitle}</div>
            ) : null}
          </div>
        )}
      </div>

      {/* 하단 썸네일: 나머지 카메라들 */}
      {otherTracks.length > 0 && (
        <div style={styles.thumbnailRow}>
          {otherTracks.map((track) => (
            <div key={track.participant?.identity} style={styles.thumbnail}>
              <VideoTrack trackRef={track} style={styles.thumbnailVideo} />
              <div style={styles.thumbnailLabel}>
                {track.participant?.identity || '카메라'}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ─── 메인 LivePage 컴포넌트 ─────────────────────────────────────────────────
function LivePage() {
  const { sessionId } = useParams();
  const navigate = useNavigate();

  const [token, setToken] = useState(null);
  const [livekitServerUrl, setLivekitServerUrl] = useState(SERVER_URL);
  const [activeCamera, setActiveCamera] = useState(null);
  const [overlay, setOverlay] = useState(DEFAULT_OVERLAY);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [connected, setConnected] = useState(false);
  const [broadcastEnded, setBroadcastEnded] = useState(false);
  const [isLandscape, setIsLandscape] = useState(window.innerWidth > window.innerHeight);

  useEffect(() => {
    const handleResize = () => setIsLandscape(window.innerWidth > window.innerHeight);
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // viewer 토큰 발급
  useEffect(() => {
    if (!sessionId) return;

    const fetchToken = async () => {
      setIsLoading(true);
      setError(null);

      const accessToken = localStorage.getItem('accessToken');
      if (!accessToken) {
        setError('로그인이 필요합니다.');
        setIsLoading(false);
        return;
      }

      try {
        const response = await fetch(`${API_URL}/api/live/session/token`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${accessToken}`,
          },
          body: JSON.stringify({ session_id: sessionId, role: 'viewer' }),
        });

        if (!response.ok) {
          throw new Error(`토큰 발급 실패: ${response.status}`);
        }

        const data = await response.json();
        setToken(data.token);
        if (data.livekit_url) setLivekitServerUrl(data.livekit_url);
      } catch (err) {
        console.error('LivePage 토큰 발급 오류:', err);
        setError(err.message || '토큰 발급 중 오류가 발생했습니다.');
      } finally {
        setIsLoading(false);
      }
    };

    fetchToken();
  }, [sessionId]);

  // 활성 카메라 폴링 (1500ms)
  useEffect(() => {
    if (!sessionId || !token) return;

    const accessToken = localStorage.getItem('accessToken');

    const fetchStatus = async () => {
      try {
        const response = await fetch(
          `${API_URL}/api/live/session/${sessionId}/status`,
          { headers: { Authorization: `Bearer ${accessToken}` } }
        );
        if (response.ok) {
          const data = await response.json();
          if (data.active_camera) setActiveCamera(data.active_camera);
          if (data.is_live === false) setBroadcastEnded(true);
        }
      } catch (err) {
        console.error('상태 조회 오류:', err);
      }
    };

    fetchStatus();
    const interval = setInterval(fetchStatus, 1500);
    return () => clearInterval(interval);
  }, [sessionId, token]);

  // 오버레이 폴링 (500ms)
  useEffect(() => {
    if (!sessionId || !token) return;

    const accessToken = localStorage.getItem('accessToken');

    const fetchOverlay = async () => {
      try {
        const response = await fetch(
          `${API_URL}/api/live/session/${sessionId}/overlay`,
          { headers: { Authorization: `Bearer ${accessToken}` } }
        );
        if (response.ok) {
          const data = await response.json();
          setOverlay(data);
        }
      } catch (err) {
        // 오류 무시 (오버레이는 비필수)
      }
    };

    fetchOverlay();
    const interval = setInterval(fetchOverlay, 500);
    return () => clearInterval(interval);
  }, [sessionId, token]);

  // ─── 렌더링 ────────────────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <div style={styles.centerBox}>
        <div style={styles.spinner} />
        <p style={{ color: '#94A3B8', marginTop: 16 }}>연결 중...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div style={styles.centerBox}>
        <p style={{ color: '#EF4444', fontSize: 16 }}>{error}</p>
        <button style={styles.backBtn} onClick={() => navigate('/')}>
          홈으로 돌아가기
        </button>
      </div>
    );
  }

  if (broadcastEnded) {
    return (
      <div style={styles.endedBox}>
        <div style={styles.endedIcon}>■</div>
        <p style={styles.endedTitle}>방송이 종료되었습니다</p>
        <p style={styles.endedSub}>시청해 주셔서 감사합니다</p>
        <button style={styles.backBtn} onClick={() => navigate('/')}>
          홈으로 돌아가기
        </button>
      </div>
    );
  }

  return (
    <div style={styles.page}>
      {/* 헤더: 가로 모드에서 숨김 */}
      {!isLandscape && (
        <div style={styles.header}>
          <button style={styles.backBtnSmall} onClick={() => navigate(-1)}>
            ← 뒤로
          </button>
          <div>
            <h2 style={styles.title}>라이브 시청</h2>
            <p style={styles.sessionLabel}>Session: {sessionId}</p>
          </div>
          <div style={styles.statusDot}>
            {connected ? (
              <span style={{ color: '#22C55E' }}>● 연결됨</span>
            ) : (
              <span style={{ color: '#F59E0B' }}>● 연결 중</span>
            )}
          </div>
        </div>
      )}

      {/* LiveKit Room */}
      {token && (
        <LiveKitRoom
          serverUrl={livekitServerUrl}
          token={token}
          connect={true}
          video={false}
          audio={false}
          onConnected={() => setConnected(true)}
          onDisconnected={() => setConnected(false)}
          style={{ flex: 1, display: 'flex', flexDirection: 'column' }}
          options={{
            dynacast: true,
            webRTCConfig: { iceTransportPolicy: 'all' },
          }}
        >
          <LiveVideoArea activeCamera={activeCamera} overlay={overlay} />
        </LiveKitRoom>
      )}
    </div>
  );
}

// ─── 스타일 ──────────────────────────────────────────────────────────────────
const styles = {
  page: {
    display: 'flex',
    flexDirection: 'column',
    height: '100vh',
    backgroundColor: '#0F172A',
    color: '#F1F5F9',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '12px 20px',
    backgroundColor: '#1E293B',
    borderBottom: '1px solid #334155',
  },
  title: {
    margin: 0,
    fontSize: 18,
    fontWeight: 700,
    color: '#F1F5F9',
  },
  sessionLabel: {
    margin: 0,
    fontSize: 12,
    color: '#64748B',
  },
  statusDot: {
    fontSize: 13,
    fontWeight: 600,
  },
  backBtnSmall: {
    background: 'transparent',
    border: '1px solid #334155',
    borderRadius: 8,
    color: '#94A3B8',
    padding: '6px 12px',
    cursor: 'pointer',
    fontSize: 13,
  },
  videoArea: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
  },
  mainVideo: {
    flex: 1,
    position: 'relative',
    backgroundColor: '#000',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  mainVideoEl: {
    width: '100%',
    height: '100%',
    objectFit: 'contain',
  },
  liveBadge: {
    position: 'absolute',
    top: 12,
    left: 12,
    backgroundColor: '#DC2626',
    color: '#fff',
    fontWeight: 700,
    fontSize: 12,
    padding: '4px 10px',
    borderRadius: 4,
    letterSpacing: 1,
    zIndex: 10,
  },
  liveDot: {
    fontSize: 8,
    marginRight: 4,
  },
  noVideoPlaceholder: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
  },

  // ── 스코어보드 ──
  scoreboard: {
    position: 'absolute',
    top: 12,
    left: '50%',
    transform: 'translateX(-50%)',
    display: 'flex',
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(10,10,20,0.85)',
    borderRadius: 10,
    overflow: 'hidden',
    zIndex: 10,
    minWidth: 280,
    boxShadow: '0 4px 20px rgba(0,0,0,0.5)',
  },
  sbTeam: {
    color: '#fff',
    fontSize: 15,
    fontWeight: 700,
    padding: '10px 16px',
    minWidth: 90,
    textAlign: 'center',
  },
  sbScoreBlock: {
    display: 'flex',
    alignItems: 'center',
    backgroundColor: 'rgba(51,102,255,0.25)',
    padding: '6px 12px',
  },
  sbScore: {
    color: '#FFD700',
    fontSize: 28,
    fontWeight: 900,
    minWidth: 36,
    textAlign: 'center',
  },
  sbSep: {
    color: 'rgba(255,255,255,0.5)',
    fontSize: 22,
    padding: '0 8px',
  },

  // ── 자막 ──
  lowerThird: {
    position: 'absolute',
    bottom: 60,
    left: 32,
    backgroundColor: 'rgba(20,20,40,0.92)',
    borderLeft: '5px solid #3366FF',
    padding: '10px 24px 10px 16px',
    borderRadius: '0 8px 8px 0',
    zIndex: 10,
    maxWidth: '60%',
    animation: 'slideIn 0.4s ease',
  },
  ltTitle: {
    color: '#fff',
    fontSize: 20,
    fontWeight: 700,
    textShadow: '1px 1px 3px rgba(0,0,0,0.8)',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  ltSub: {
    color: '#aaaacc',
    fontSize: 13,
    marginTop: 3,
  },

  thumbnailRow: {
    display: 'flex',
    gap: 8,
    padding: '8px 12px',
    backgroundColor: '#1E293B',
    overflowX: 'auto',
  },
  thumbnail: {
    position: 'relative',
    width: 120,
    height: 90,
    flexShrink: 0,
    backgroundColor: '#0F172A',
    borderRadius: 6,
    overflow: 'hidden',
  },
  thumbnailVideo: {
    width: '100%',
    height: '100%',
    objectFit: 'cover',
  },
  thumbnailLabel: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: 'rgba(0,0,0,0.6)',
    color: '#fff',
    fontSize: 10,
    padding: '2px 6px',
    textAlign: 'center',
  },
  centerBox: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    height: '100vh',
    backgroundColor: '#0F172A',
  },
  spinner: {
    width: 40,
    height: 40,
    border: '3px solid #334155',
    borderTop: '3px solid #3366FF',
    borderRadius: '50%',
    animation: 'spin 1s linear infinite',
  },
  backBtn: {
    marginTop: 20,
    padding: '10px 24px',
    backgroundColor: '#3366FF',
    color: '#fff',
    border: 'none',
    borderRadius: 8,
    cursor: 'pointer',
    fontSize: 14,
  },
  endedBox: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    height: '100vh',
    backgroundColor: '#000',
    gap: 12,
  },
  endedIcon: {
    fontSize: 48,
    color: '#DC2626',
    marginBottom: 8,
  },
  endedTitle: {
    color: '#F1F5F9',
    fontSize: 24,
    fontWeight: 700,
    margin: 0,
  },
  endedSub: {
    color: '#64748B',
    fontSize: 14,
    margin: 0,
  },
};

export default LivePage;
