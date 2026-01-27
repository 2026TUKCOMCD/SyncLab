import React, { useState } from "react";
import { useNavigate } from 'react-router-dom';
import axios from 'axios';

function LoginPage() {
    const navigate = useNavigate();

    const [formData, setFormData] = useState({
        id: '',
        password: ''
    });
    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData({ ...formData, [name]: value });
    }

    const handleLogin = async (e) => {
        e.preventDefault();

        try {
            // 1. FastAPI 서버로 로그인 요청
            const response = await axios.post('/api/users/login', {
                id: formData.id,
                password: formData.password
            });
            if(response.status == 200){ // 로그인 성공 시
                const token = response.data.access_token;
                localStorage.setItem('accessToken', token);

                localStorage.setItem('userName', response.data.user_name);
            }

            console.log("로그인 시도:", formData);
            alert("로그인 성공!");

            // 3. 편집화면으로 이동
            navigate('/editor');
        }
        catch (error) {
            alert("로그인 실패: 아이디나 비밀번호를 확인해주세요.");
            console.error(error);
        }
    };

    return (
        <div className="auth-container">
            <div className="auth-box">
                <h2>로그인</h2>
                <p>아이디와 비밀번호를 입력하세요.</p>

                <form onSubmit={handleLogin}>
                    {/* 아이디 입력 */}
                    <div className="input-group">
                        <label>아이디</label>
                        <input
                            type="text"
                            name="id"
                            placeholder="User_ID"
                            value={formData.id}
                            onChange={handleChange}
                            required
                        />
                    </div>

                    {/* 비밀번호 입력 */}
                    <div className="input-group">
                        <label>비밀번호</label>
                        <input
                            type="password"
                            name="password"
                            placeholder="Password"
                            value={formData.password}
                            onChange={handleChange}
                            required
                        />
                    </div>

                    <button type="submit" className="primary-btn">로그인</button>
                </form>

                <div className="auth-footer">
                    <button className="link-btn" onClick={() => navigate('/signup')}>
                        계정이 없으신가요? 회원가입
                    </button>
                </div>
            </div>
        </div>
    );
}
export default LoginPage;