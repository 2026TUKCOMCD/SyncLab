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

    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData({ ...formData, [name]: value });
    };

    const handleSignup = async (e) => {
        e.preventDefault();

        try{
            const response = await axios.post('/api/users/signup', {
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
                <div className="auth-link">
                    이미 계정이 있으신가요? 
                    <span onClick={() => navigate('/login')}>로그인</span>
                </div>
            </div>
        </div>
    );
}

export default RegisterPage;