// src/App.js
import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';

// 페이지들 불러오기
import LoginPage from './pages/LoginPage';
import MainPage from './pages/MainPage';
import RegisterPage from './pages/RegisterPage';
import EditPage from './pages/EditPage';
import LivePage from './pages/LivePage';
import LiveListPage from './pages/LiveListPage';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* 기본 주소(/)로 오면 LandingPage를 보여줌 */}
        <Route path="/" element={<MainPage />} />

        {/* /login 으로 오면 LoginPage 보여줌 */}
        <Route path="/login" element={<LoginPage />} />

        {/* /signup 으로 오면 SignupPage 보여줌 */}
        <Route path="/signup" element={<RegisterPage />} />

        {/* /editor 로 오면 EditorPage(편집화면) 보여줌 */}
        <Route path="/editor" element={<EditPage />} />

        {/* /live/:sessionId 로 오면 LivePage(라이브 시청) 보여줌 */}
        <Route path="/live/:sessionId" element={<LivePage />} />

        {/* /lives 로 오면 LiveListPage(라이브 목록) 보여줌 */}
        <Route path="/lives" element={<LiveListPage />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;