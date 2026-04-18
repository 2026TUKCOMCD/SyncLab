import React, { useState, useEffect } from "react";
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import '../App.css';

function LoginPage() {
    const navigate = useNavigate();

    const [formData, setFormData] = useState({
        id: '',
        password: ''
    });

    // Google GSI 및 Kakao SDK 스크립트 로드
    useEffect(() => {
        const googleScript = document.createElement('script');
        googleScript.src = 'https://accounts.google.com/gsi/client';
        googleScript.async = true;
        document.body.appendChild(googleScript);

        const kakaoScript = document.createElement('script');
        kakaoScript.src = 'https://t1.kakaocdn.net/kakao_js_sdk/2.7.2/kakao.min.js';
        kakaoScript.async = true;
        kakaoScript.onload = () => {
            if (window.Kakao && !window.Kakao.isInitialized()) {
                window.Kakao.init(process.env.REACT_APP_KAKAO_JS_KEY);
            }
        };
        document.body.appendChild(kakaoScript);
    }, []);

    // Google 로그인 핸들러
    const handleGoogleLogin = async (credentialResponse) => {
        try {
            const response = await axios.post('/api/web/google', {
                id_token: credentialResponse.credential
            });
            if (response.status === 200) {
                localStorage.setItem('accessToken', response.data.access_token);
                localStorage.setItem('userID', response.data.user_id);
                localStorage.setItem('userName', response.data.user_name);
                localStorage.setItem('user_session_id', response.data.user_session_id);
                navigate('/');
            }
        } catch (error) {
            alert('Google 로그인 실패: ' + (error.response?.data?.detail || '알 수 없는 오류'));
        }
    };

    // Kakao 로그인 핸들러
    const handleKakaoLogin = () => {
        window.Kakao.Auth.login({
            success: async (authObj) => {
                try {
                    const response = await axios.post('/api/web/kakao', {
                        access_token: authObj.access_token
                    });
                    if (response.status === 200) {
                        localStorage.setItem('accessToken', response.data.access_token);
                        localStorage.setItem('userID', response.data.user_id);
                        localStorage.setItem('userName', response.data.user_name);
                        localStorage.setItem('user_session_id', response.data.user_session_id);
                        navigate('/');
                    }
                } catch (error) {
                    alert('Kakao 로그인 실패: ' + (error.response?.data?.detail || '알 수 없는 오류'));
                }
            },
            fail: (err) => {
                alert('Kakao 로그인 실패: ' + JSON.stringify(err));
            }
        });
    };

    // Google 콜백 전역 등록
    window.handleGoogleLogin = handleGoogleLogin;
    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData({ ...formData, [name]: value });
    }

    const handleLogin = async (e) => {
        e.preventDefault();

        try {
            // 1. FastAPI 서버로 로그인 요청
            const response = await axios.post('/api/web/login', {
                id: formData.id,
                password: formData.password
            });
            if(response.status == 200){ // 로그인 성공 시
                const token = response.data.access_token;
                localStorage.setItem('accessToken', token);
                localStorage.setItem('userID', response.data.user_id);
                localStorage.setItem('userName', response.data.user_name);
                localStorage.setItem('user_session_id', response.data.user_session_id);
            }

            console.log("로그인 시도:", formData);

            // 3. 메인화면으로 이동
            navigate('/');
        }
        catch (error) {
            alert("로그인 실패: 아이디나 비밀번호를 확인해주세요.");
            console.error(error);
        }
    };

    return (
        <div className="auth-wrapper"> {/* 전체 감싸기 */}
            <img src="/synclab_logo.png" alt="Synclab Logo" className="auth-logo" onClick={() => navigate('/')}/>
            <div className="auth-card">  {/* 흰색 카드 */}
                <h2>로그인</h2>
                <form onSubmit={handleLogin}>
                    <div className="input-group">
                        <label>아이디</label>
                        <input
                            type="text"
                            name="id"
                            value={formData.id}
                            onChange={handleChange}
                            placeholder="아이디를 입력하세요"
                            required
                        />
                    </div>
                    <div className="input-group">
                        <label>비밀번호</label>
                        <input
                            type="password"
                            name="password"
                            value={formData.password}
                            onChange={handleChange}
                            placeholder="비밀번호를 입력하세요"
                            required
                        />
                    </div>
                    <button type="submit" className="auth-btn">로그인</button>
                </form>

                {/* 소셜 로그인 */}
                <div className="social-divider">또는</div>

                <div
                    id="g_id_onload"
                    data-client_id={process.env.REACT_APP_GOOGLE_CLIENT_ID}
                    data-callback="handleGoogleLogin"
                    data-auto_prompt="false">
                </div>
                <div
                    className="g_id_signin"
                    data-type="standard"
                    data-size="large"
                    data-theme="outline"
                    data-text="sign_in_with"
                    data-shape="rectangular"
                    data-logo_alignment="left"
                    data-width="100%">
                </div>

                <button type="button" className="kakao-btn" onClick={handleKakaoLogin}>
                    카카오로 로그인
                </button>

                {/* 회원가입 페이지로 이동하는 링크 추가 */}
                <div className="auth-link">
                    계정이 없으신가요? 
                    <span onClick={() => navigate('/signup')}>회원가입</span>
                </div>
            </div>
        </div>
    );
}
export default LoginPage;