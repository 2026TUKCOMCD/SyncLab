// src/App.js
import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';

// 페이지들 불러오기
import LoginPage from './pages/LoginPage';
import MainPage from './pages/MainPage';
import RegisterPage from './pages/RegisterPage';
import EditPage from './pages/EditPage';

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
      </Routes>
    </BrowserRouter>
  );
}

export default App;