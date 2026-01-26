import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';

function RegisterPage() {
    const navigate = useNavigate();

    const [formData, setFormData] = useState({
        id: '',
        password: ''
    });

    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData({ ...formData, [name]: value });
    };

    const handleSignup = async (e) => {
        e.preventDefault();

        try{
            const response = await axios.post('/api/users/signup', {
                user_id: formData.id,
                password: formData.password
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
        <div className="auth-container">
            <div className="auth-box">
                <h2>회원가입</h2>
                <p>테스트용 계정을 생성합니다.</p>

                <form onSubmit={handleSignup}>
                    {/* 아이디 입력 */}
                    <div className="input-group">
                        <label>아이디 (User ID)</label>
                        <input
                            type="text"
                            name="id"
                            placeholder="아이디 입력"
                            onChange={handleChange}
                            required
                        />
                    </div>

                    {/* 비밀번호 입력 */}
                    <div className="input-group">
                        <label>비밀번호 (Password)</label>
                        <input
                            type="password"
                            name="password"
                            placeholder="비밀번호 입력"
                            onChange={handleChange}
                            required
                        />
                    </div>

                    <button type="submit" className="primary-btn">가입하기</button>
                </form>

                <div className="auth-footer">
                    <button className="link-btn" onClick={() => navigate('/login')}>
                        로그인 하러 가기
                    </button>
                </div>
            </div>
        </div>
    );
}

export default RegisterPage;