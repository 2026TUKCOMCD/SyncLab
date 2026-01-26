import React from 'react';
import { useNavigate } from 'react-router-dom';

function MainPage() {
  const navigate = useNavigate();

  return (
    <div className="landing-container">
      <header>
        <h1>SyncLab에 오신 것을 환영합니다</h1>
        <p>어디서든 쉽고 빠르게 영상을 편집하세요.</p>
      </header>

      <section className="description">
        <h3>로그인이 왜 필요한가요?</h3>
        <ul>
          <li>작업 중인 프로젝트를 클라우드(S3)에 안전하게 저장합니다.</li>
          <li>모바일 앱에서 촬영한 영상을 웹에서 바로 불러올 수 있습니다.</li>
        </ul>
      </section>

      <div className="action-buttons">
        <button onClick={() => navigate('/login')}>로그인</button>
        <button onClick={() => navigate('/signup')}>회원가입</button>
      </div>
    </div>
  );
}
export default MainPage;