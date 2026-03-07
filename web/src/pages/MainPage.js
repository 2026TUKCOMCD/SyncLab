import React, { useState ,useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import '../App.css';

function MainPage() {
  const navigate = useNavigate();
  const [userName, setUserName] = useState('');

  useEffect(() => {
    const storedName = localStorage.getItem('userName');
    if(storedName){
      setUserName(storedName);
    }
  }, []);

  const handleLogout = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('userName');
    setUserName('')
    navigate('/');
  }

  return (
    <div>
      {/* 1. 상단 네비게이션 바 */}
      <nav className="navbar">
        <div className="nav-logo-center" onClick={() => navigate('/')}>
          <img src="/synclab_logo.png" alt="SyncLab Logo" className="nav-logo-img" />
        </div>
        <div className="nav-buttons">
          {userName ? (
            <>
              <span style={{ marginRight: '15px', fontWeight: 'bold' }}>
                {userName}님 환영합니다
              </span>
              <button onClick={handleLogout} className="logout-btn">로그아웃</button>
            </>
          ) : (
            <div>
            <button onClick={() => navigate('/signup')}>회원가입</button>
            <button onClick={() => navigate('/login')}>로그인</button>
            </div>
          )}
        </div>
      </nav>

      {/* 2. 메인 컨텐츠 영역 */}
      <div className="main-container">

        {/* 섹션 A: 프로젝트 소개 & 로그인 유도 (Hero Section) */}
        <div className="hero-section">
          <h1 className="hero-title">다양한 시각, 완벽한 동기화된 편집</h1>
          <p className="hero-description">
            SyncLab은 스마트폰으로 촬영한 다각도 영상을 하나의 영상으로 편집하는 플랫폼입니다.<br />
            지금 바로 시작하여 여러분만의 편집 영상을 생성해 보세요!
          </p>

          {userName ? (
            <button className='hero-btn' onClick={() => navigate('/editor')}>
              편집하러 가기 🚀
            </button>
          ): (
            <button className='hero-btn' onClick={() => navigate('/login')}>
              지금 시작하기(로그인)
            </button>
          )}
        </div>

        {/* 섹션 B: 라이브 방송 */}
        <div style={{
          margin: '0 auto 48px',
          maxWidth: 900,
          background: 'linear-gradient(135deg, #0F172A 0%, #1E293B 100%)',
          borderRadius: 16,
          border: '1px solid #334155',
          padding: '32px 40px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 24,
        }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
              <span style={{ color: '#EF4444', fontSize: 20 }}>●</span>
              <span style={{ color: '#EF4444', fontWeight: 700, fontSize: 13, letterSpacing: 1 }}>LIVE</span>
            </div>
            <h2 style={{ margin: '0 0 8px', fontSize: 22, fontWeight: 700, color: '#F1F5F9' }}>
              지금 진행 중인 라이브 방송
            </h2>
            <p style={{ margin: 0, fontSize: 14, color: '#94A3B8' }}>
              다양한 각도로 촬영 중인 실시간 방송을 시청하세요.
            </p>
          </div>
          <button
            onClick={() => navigate('/lives')}
            style={{
              flexShrink: 0,
              padding: '14px 28px',
              backgroundColor: '#DC2626',
              color: '#fff',
              border: 'none',
              borderRadius: 10,
              cursor: 'pointer',
              fontSize: 15,
              fontWeight: 700,
              whiteSpace: 'nowrap',
            }}
          >
            📺 라이브 보러가기
          </button>
        </div>

        {/* 섹션 C: 기술적 특징 3가지 (Tech Grid) */}
        <h2 className="tech-section-title">어떤 기술이 있나요?</h2>

        <div className="tech-grid">
          {/* 카드 1 */}
          <div className="tech-card">
            <span className="tech-icon">🛠️</span>
            <h3>타임라인 편집</h3>
            <p>
              여러 개의 동영상에서 원하는 부분을 추출하여 원하는대로 편집할 수 있습니다.
              사용자가 원하는 시각으로 영상을 생성할 수 있는거죠!
            </p>
          </div>

          {/* 카드 2 */}
          <div className="tech-card">
            <span className="tech-icon">👻</span>
            <h3>비로그인 회원 제공</h3>
            <p>
              혼자서 여러 기기로 촬영한다구요?<br></br>
              비회원으로 세션코드만 입력해보세요!<br></br>
              로그인하지 않아도 동영상이 저장될 거에요.

            </p>
          </div>

          {/* 카드 3 */}
          <div className="tech-card">
            <span className="tech-icon">🎬</span>
            <h3>동기화된 동시 재생</h3>
            <p>
              모든 동영상을 동시에 촬영하는 것은 어렵죠...
              하지만 원활한 편집을 위해 동기화를 제공하여 사용자에게 편의성을 제공합니다!
            </p>
          </div>
        </div>

      </div>
    </div>
  );
}
export default MainPage;