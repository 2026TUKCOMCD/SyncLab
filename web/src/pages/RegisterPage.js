import React, { useState } from 'react';

import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import '../App.css';

function RegisterPage() {
    const navigate = useNavigate();

    const [formData, setFormData] = useState({
        id: '',
        password: '',
        user_name: ''
    });

    // 탭 및 이메일 인증 state
    const [tab, setTab] = useState('general');
    const [emailStep, setEmailStep] = useState(1);
    const [emailForm, setEmailForm] = useState({
        email: '',
        code: '',
        user_name: '',
        password: ''
    });

    // 이메일 인증 코드 발송
    const handleSendCode = async () => {
        try {
            await axios.post('/api/web/send-code', { email: emailForm.email });
            alert('인증 코드가 발송되었습니다.');
            setEmailStep(2);
        } catch (error) {
            alert(error.response?.data?.detail || '발송 실패');
        }
    };

    // 이메일 인증 코드 확인
    const handleVerifyCode = async () => {
        try {
            await axios.post('/api/web/verify-code', {
                email: emailForm.email,
                code: emailForm.code
            });
            alert('인증 완료!');
            setEmailStep(3);
        } catch (error) {
            alert(error.response?.data?.detail || '인증 실패');
        }
    };

    // 이메일 회원가입 완료
    const handleEmailSignup = async (e) => {
        e.preventDefault();
        try {
            await axios.post('/api/web/email-signup', {
                email: emailForm.email,
                password: emailForm.password,
                user_name: emailForm.user_name
            });
            alert('회원가입 성공! 로그인 해주세요.');
            navigate('/login');
        } catch (error) {
            alert(error.response?.data?.detail || '회원가입 실패');
        }
    };

    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData({ ...formData, [name]: value });
    };

    const handleSignup = async (e) => {
        e.preventDefault();

        try{
            const response = await axios.post('/api/web/signup', {
                id: formData.id,
                password: formData.password,
                user_name: formData.user_name
            });
            if(response.status == 200){
                alert("회원가입 성공! 로그인 해주세요.");
                navigate('/login');
            }
        }
    catch(error){
        console.error(error);
        alert("회원가입 실패: " + (error.response?.data?.detail || "알 수 없는 오류"));
    }
  };

    return (
        <div className="auth-wrapper">
            <img src="/synclab_logo.png" alt="synclab_logo" className="auth-logo" onClick={() => navigate('/')}/>
            <div className="auth-card">
                <h2>회원가입</h2>

                {/* 탭 전환 */}
                <div className="tab-group">
                    <button
                        type="button"
                        className={tab === 'general' ? 'tab active' : 'tab'}
                        onClick={() => setTab('general')}>
                        일반 가입
                    </button>
                    <button
                        type="button"
                        className={tab === 'email' ? 'tab active' : 'tab'}
                        onClick={() => setTab('email')}>
                        이메일 가입
                    </button>
                </div>

                {/* 이메일 인증 회원가입 */}
                {tab === 'email' && (
                    <div>
                        {emailStep === 1 && (
                            <div className="input-group">
                                <label>이메일</label>
                                <input
                                    type="email"
                                    value={emailForm.email}
                                    onChange={(e) => setEmailForm({...emailForm, email: e.target.value})}
                                    placeholder="이메일 입력"
                                />
                                <button type="button" className="auth-btn" onClick={handleSendCode}>
                                    인증 코드 발송
                                </button>
                            </div>
                        )}
                        {emailStep === 2 && (
                            <div className="input-group">
                                <label>인증 코드</label>
                                <input
                                    type="text"
                                    value={emailForm.code}
                                    onChange={(e) => setEmailForm({...emailForm, code: e.target.value})}
                                    placeholder="6자리 코드 입력"
                                    maxLength={6}
                                />
                                <button type="button" className="auth-btn" onClick={handleVerifyCode}>
                                    코드 확인
                                </button>
                            </div>
                        )}
                        {emailStep === 3 && (
                            <form onSubmit={handleEmailSignup}>
                                <div className="input-group">
                                    <label>닉네임</label>
                                    <input
                                        type="text"
                                        value={emailForm.user_name}
                                        onChange={(e) => setEmailForm({...emailForm, user_name: e.target.value})}
                                        placeholder="닉네임 입력"
                                        required
                                    />
                                </div>
                                <div className="input-group">
                                    <label>비밀번호</label>
                                    <input
                                        type="password"
                                        value={emailForm.password}
                                        onChange={(e) => setEmailForm({...emailForm, password: e.target.value})}
                                        placeholder="비밀번호 입력"
                                        required
                                    />
                                </div>
                                <button type="submit" className="auth-btn">가입하기</button>
                            </form>
                        )}
                    </div>
                )}

                {/* 일반 회원가입 */}
                {tab === 'general' &&
                <form onSubmit={handleSignup}>
                    <div className="input-group">
                        <label>닉네임</label>
                        <input
                            type="text"
                            name="user_name"
                            onChange={handleChange}
                            placeholder="닉네임 입력"
                            required
                        />
                    </div>
                    <div className="input-group">
                        <label>아이디</label>
                        <input
                            type="text"
                            name="id"
                            onChange={handleChange}
                            placeholder="사용할 아이디"
                            required
                        />
                    </div>
                    <div className="input-group">
                        <label>비밀번호</label>
                        <input
                            type="password"
                            name="password"
                            onChange={handleChange}
                            placeholder="비밀번호"
                            required
                        />
                    </div>
                    
                    <button type="submit" className="auth-btn">가입하기</button>
                </form>
                }
                <div className="auth-link">
                    이미 계정이 있으신가요? 
                    <span onClick={() => navigate('/login')}>로그인</span>
                </div>
            </div>
        </div>
    );
}

export default RegisterPage;