import { Play, Pause, SkipBack, SkipForward, Save, Plus } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import '../App.css';
import axios from 'axios'
import React, { useState, useRef, useEffect } from 'react';

// API 베이스 URL: REACT_APP_API_URL 환경변수 → 없으면 빈 문자열(상대경로, Docker nginx 프록시 사용)
const API_BASE = process.env.REACT_APP_API_URL || '';
// useEffect()는 화면이 구성된 뒤에 실행되어 재랜더링 하는 방식이지만 useState는 랜더링 이전에 수행되어 불필요한 랜더링이 발생하지 않음.
// useRef()는 랜더링 되어도 변하지 않는 참조 변수 또는 객체

function EditPage() {
  const navigate = useNavigate();
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(180);
  const [selectedSourceCam, setSelectedSourceCam] = useState(null);
  const [inPoint, setInPoint] = useState(null);
  const [outPoint, setOutPoint] = useState(null);
  const [savedClips, setSavedClips] = useState([]);
  const [isDraggingIn, setIsDraggingIn] = useState(false);
  const [isDraggingOut, setIsDraggingOut] = useState(false);
  const videoRefs = useRef({});
  const programVideoRef = useRef(null);
  const timelineRef = useRef(null);
  const animationRef = useRef(null);
  const [multiviewCameras, setMultiviewCameras] = useState([null, null, null, null]);
  const [cameras, setCameras] = useState([]);
  const [sessionList, setSessionList] = useState([]); // 사용자가 참여한 여러 세션 목록을 위한 변수
  const [activeSessionId, setActiveSessionId] = useState(null); // 현재 사용자가 선택한 세션에 대한 변수

  // 모든 카메라 중 시작 시간이 가장 빠른 비디오의 시작 시간과 session의 총 영상 길이 찾기
  const minAbsStart = React.useMemo(() => {
    if (!cameras || cameras.length === 0) return 0;
    return Math.min(...cameras.map(c => c.start_time));
  }, [cameras]);
  const totalSessionDuration = React.useMemo(() => {
    if (!cameras || cameras.length === 0) return 180;
    const maxEnd = Math.max(...cameras.map(c => c.end_time));
    return (maxEnd - minAbsStart) / 1000;
  }, [cameras, minAbsStart]);

  // 페이지 접속 시 user_id로 Session 불러오기
  useEffect(() => {
    const fetchSessions = async () => {
      const token = localStorage.getItem('accessToken');
      try {
        const response = await axios.get(`${API_BASE}/api/web/sessions`, { // Token에 들어있는 user_id로 세션 조회 시도
          headers: { Authorization: `Bearer ${token}` }
        });
        const sessions = response.data?.sessions || [];
        setSessionList(sessions); // session_id가 저장되어 있는 response를 리스트로 생성

        if (sessions.length > 0) {
          setActiveSessionId(sessions[0]?.session_session_id); // 배열 중 가장 상위에 있는 session_id로 초기값 설정 -> null 이나 가장 최근에 생성한 session으로 변경 가능할 듯
        }
      }
      catch (error) {
        console.error(error);
      }
    };
    fetchSessions();
  }, []);

  // session으로 반환받은 사용자의 user_session_id로 해당 세션의 동영상 목록을 출력하는 함수
  useEffect(() => {
    if (activeSessionId === null || activeSessionId === undefined) return;

    const fetchVideos = async () => { // fetchVideos 라는 비동기 함수를 작성하고 useEffect() 끝에서 함수 호출하는 방식
      const token = localStorage.getItem('accessToken');
      console.log("현재 토큰:", token);
      if (!token) {
        alert("로그인이 필요합니다.");
        navigate("/login");
      }
      // GET 요청 수행
      try {
        const response = await axios.get(`${API_BASE}/api/web/list/${activeSessionId}`, { // activeSessionId(현재 선택한 session)를 통해 해당 세션의 비디오 목록 조회
          headers: {
            Authorization: `Bearer ${token}` // Header에 토큰 값 실어서 보내기
          }
        });
        const videoData = response.data?.videos || [];
        const color = ['#ef4444', '#facc15', '#22c55e', '#3b82f6'];
        const coloredVideos = videoData.map((video, index) => ({
          ...video,
          color: color[index % color.length]
        }));

        setCameras(coloredVideos);
        setSavedClips([]); // 세션 재선택 시 클립 초기화
        setMultiviewCameras([null, null, null, null]); // 세션 재선택 시 멀티뷰 화면 초기화
        setSelectedSourceCam(null);
      }
      catch (error) {
        console.error('네트워크 에러:', error);

        if (error.response && error.response.status === 401) {
          alert("인증이 만료되었습니다. 다시 로그인해주세요.");
        }
      }
    };
    fetchVideos();
  }, [activeSessionId]); // 컴포넌트 렌더링을 activedSessionId가 변경될 떄 마다 수행해줘야 함.

  // 타임라인 In, Out 값 드래그 기능
  useEffect(() => {
    if (isDraggingIn || isDraggingOut) { // In 또는 Out이 드래그 중일 때
      window.addEventListener('mousemove', handleMarkerDrag);   //"mousemove" 이벤트에 대해 handleMarkerDrag 리스너 등록
      window.addEventListener('mouseup', handleMarkerMouseUp); // "mouseup" 이벤트에 대해 handleMarkerMouseUp 리스너 등록

      return () => { // 등록했던 이벤트 리스너 해제 -> 마우스를 이동시켜도 마커가 움직이지 않도록 제어
        window.removeEventListener('mousemove', handleMarkerDrag);
        window.removeEventListener('mouseup', handleMarkerMouseUp);
      };
    }
  }, [isDraggingIn, isDraggingOut]); // 드래그 할 때 마다 렌더링?

  // 현재 재생 중인 동영상의 시간을 보여주기 위한 함수로 타임라인의 Bar가 이동하거나 숫자 표시
  useEffect(() => {
    let animationId;

    /* 동기화 재생 로직 */
    if (isPlaying && selectedSourceCam !== null) {
      const updateAllSync = () => {
        const masterVideo = videoRefs.current[selectedSourceCam];
        const mainCam = cameras.find(c => c.id === selectedSourceCam);

        if (masterVideo && mainCam && !masterVideo.paused) {
          const actualVideoTime = masterVideo.currentTime;
          const mainOffset = (Number(mainCam.start_time) - Number(minAbsStart)) / 1000;
          const calculateMasterTime = actualVideoTime + mainOffset;
          setCurrentTime(calculateMasterTime);

          multiviewCameras.forEach(cam => {
            if (!cam || cam.id === selectedSourceCam) return;
            const v = videoRefs.current[cam.id];
            if (!v) return;

            const camOffset = (Number(cam.start_time) - Number(minAbsStart)) / 1000;
            const targetTime = calculateMasterTime - camOffset;

            if (targetTime >= 0 && targetTime <= (cam.duration || 10000)) {
              if (v.paused) v.play().catch(() => { });

              const diff = v.currentTime - targetTime;
              if (Math.abs(diff) > 0.5) {
                v.currentTime = targetTime;
              }
              else if (Math.abs(diff) > 0.05) {
                v.playbackRate = diff > 0 ? 0.98 : 1.02;
              }
              else {
                v.playbackRate = 1.0;
              }
            }
          });
          animationId = requestAnimationFrame(updateAllSync);
        }
      };
      animationRef.current = requestAnimationFrame(updateAllSync);
    }

    return () => {
      if (animationId) cancelAnimationFrame(animationId);
    };
  }, [isPlaying, selectedSourceCam, cameras, minAbsStart, multiviewCameras]); // 이 useEffect의 실행 시점은 Mount, Update로 isPlaying이나 selectedSourceCam 값이 변경될 때 렌더링, 하지만 내부 코드인 requestAnimationFrame 함수로 인해 초당 60번의 랜더링이 이루어짐

  // play, pause 버튼 클릭 시 발생하는 이벤트 함수이고 모든 동영상의 재생, 정지를 제어
  const togglePlay = () => {
    if (selectedSourceCam === null) return;
    const mainVideo = videoRefs.current[selectedSourceCam];
    const programVideo = programVideoRef.current; // 선택되어 프로그램 모니터 부분에 나타난 비디오

    // 일시 정지 상태일 때 버튼 클릭 시 동시 재생
    if (!isPlaying) {
      if (mainVideo) mainVideo.play().catch(() => { });
      if (programVideo) programVideo.play().catch(() => { });
      setIsPlaying(true);
    }
    // 재생 중일 때 버튼 클릭시 일시 정지
    else {
      Object.values(videoRefs.current).forEach(v => { if (v) v.pause(); });
      if (programVideo) programVideo.pause();
      setIsPlaying(false);
    }
  };

  // second 매개변수로 시간, 분, 초로 계산하여 문자열 출력
  const formatTime = (seconds) => {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = Math.floor(seconds % 60);
    const f = Math.floor((seconds % 1) * 60); // 60프레임
    return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}:${f.toString().padStart(2, '0')}`;
  };

  // 멀티뷰 소스에서 비디오 선택 시 프로그램 모니터의 비디오로 설정 및 초기화 함수
  const handleSourceCamClick = (camId) => {
    if (selectedSourceCam === camId) return;
    setSelectedSourceCam(camId);
    setCurrentTime(0);
    setInPoint(null);
    setOutPoint(null);
    setIsPlaying(false);
    Object.values(videoRefs.current).forEach(v => {
      if (v) v.currentTime = 0;
    });
    if (programVideoRef.current) programVideoRef.current.currentTime = 0;
  };

  // 비디오 리소스에서 멀티뷰 소스로 비디오 선택 시 multiCamers 배열로 비디오를 추가하는 함수
  const addCameraToMultiview = (camera) => {
    const emptySlotIndex = multiviewCameras.findIndex(cam => cam === null);
    if (emptySlotIndex !== -1) {
      const newMultiview = [...multiviewCameras];
      newMultiview[emptySlotIndex] = camera;
      setMultiviewCameras(newMultiview);
    } else {
      alert('멀티뷰가 가득 찼습니다. (최대 4개)');
    }
  };

  // 멀티뷰 리소스에서 선택했던 비디오를 제외하는 함수
  const removeCameraFromMultiview = (slotIndex) => {
    const newMultiview = [...multiviewCameras];
    const removedCam = newMultiview[slotIndex];
    newMultiview[slotIndex] = null;
    setMultiviewCameras(newMultiview);
    if (selectedSourceCam === removedCam?.id) {
      setSelectedSourceCam(null);
      setInPoint(null);
      setOutPoint(null);
    }
  };

  // 멀티뷰 소스에서 비디오 클릭 시 handleSourceCamClick 함수 호출
  const handleMultiviewSlotClick = (slotIndex) => {
    const camera = multiviewCameras[slotIndex];
    if (camera) handleSourceCamClick(camera.id);
  };

  // 타임라인 지점 클릭 시 원하는 시점으로 영상을 점프시키는 함수
  const handleTimelineClick = (e) => {
    if (selectedSourceCam === null || !timelineRef.current) return;
    if (isDraggingIn || isDraggingOut) return;

    const rect = timelineRef.current.getBoundingClientRect();
    const x = Math.max(0, Math.min(e.clientX - rect.left, rect.width));
    const percent = x / rect.width;

    const newMasterTime = percent * totalSessionDuration;
    setCurrentTime(newMasterTime);

    // 모든 비디오 강제 싱크
    multiviewCameras.forEach(cam => {
      if (!cam) return;
      const v = videoRefs.current[cam.id];
      if (!v) return;

      const offset = (Number(cam.start_time) - Number(minAbsStart)) / 1000;
      const target = newMasterTime - offset;

      v.currentTime = Math.max(0, target);
    });

    // 메인 비디오도 싱크
    if (programVideoRef.current) {
      const mainCam = cameras.find(c => c.id === selectedSourceCam);
      const mainOffset = (Number(mainCam.start_time) - Number(minAbsStart)) / 1000;
      programVideoRef.current.currentTime = Math.max(0, newMasterTime - mainOffset);
    }
  };

  // 편집 시작점과 편집 종료점을 드래그하여 이동시키는 함수, MouseDown -> MouseUp으로 드래그 구현
  const handleMarkerMouseDown = (type, e) => {
    e.stopPropagation(); // stopPropagation 함수를 통해 MouseDown만 동작하게 함 -> click과 같은 다른 이벤트가 동작하는 것을 방지
    if (type === 'in') setIsDraggingIn(true); // In의 드래그 상태 업데이트(드래그 중인지)
    else setIsDraggingOut(true);
  };

  // In/Out 마커 드래그 중 마커의 시간 계산 및 제어 함수
  const handleMarkerDrag = (e) => {
    if (!isDraggingIn && !isDraggingOut) return;
    if (!timelineRef.current) return;

    // 타임라인 내부의 시간으로 변환하는 수학 로직 -> handleTimeLineCLick의 로직과 유사함
    const rect = timelineRef.current.getBoundingClientRect();
    const x = Math.max(0, Math.min(e.clientX - rect.left, rect.width));
    const percent = x / rect.width;
    let newTime = Math.max(0, Math.min(percent * duration, duration));

    if (isDraggingIn) {
      if (outPoint !== null && newTime >= outPoint) newTime = outPoint - 0.1; // 새로운 시작점은 앞선 종료점보다 앞에 올 수 없도록 제어
      const conflictClip = savedClips.find(clip => newTime >= clip.start_seek && newTime < clip.end_seek);
      if (conflictClip) newTime = conflictClip.end_seek; // 이미 생성되어 있는 클립과 겹칠 경우 클립의 종료점을 시작점으로 설정
      setInPoint(newTime);
    }
    else if (isDraggingOut) {
      if (inPoint !== null && newTime <= inPoint) newTime = inPoint + 0.1; // 새로운 종료점은 시작점보다 앞에 올 수 없도록 제어
      const conflictClip = savedClips.find(clip => newTime > clip.start_seek && newTime <= clip.end_seek);
      if (conflictClip) newTime = conflictClip.start_seek; // 이미 생성되어 있는 클립과 겹칠 경우 클립의 종료지점을 앞선 클립의 시작지점으로 이동
      setOutPoint(newTime);
    }
  };

  // 드래그가 종료됐을 때(마우스를 뗐을 때?) isDraggingIn 또는 isDraggingOut을 false로 업데이트
  const handleMarkerMouseUp = () => {
    setIsDraggingIn(false);
    setIsDraggingOut(false);
  };

  // In 버튼 클릭 시 수행되는 함수로 타임라인의 현재 시점에 In 마커 생성
  const setIn = () => {
    if (selectedSourceCam === null) return;
    setInPoint(currentTime);
    if (outPoint !== null && currentTime > outPoint) setOutPoint(null);
  };

  // Out 버튼 클릭 시 수행되는 함수로 타임라인의 현재 시점에 Out 마커 생성
  const setOut = () => {
    if (selectedSourceCam === null) return;
    setOutPoint(currentTime);
    if (inPoint !== null && currentTime < inPoint) setInPoint(null);
  };

  // 클립 생성 함수
  const addClip = () => {
    if (selectedSourceCam === null || inPoint === null || outPoint === null) {
      alert('In점과 Out점을 모두 설정해주세요.');
      return;
    }
    if (inPoint >= outPoint) {
      alert('Out점이 In점보다 뒤에 있어야 합니다.');
      return;
    }
    const hasOverlap = savedClips.some(clip => {
      if (inPoint >= clip.start_seek && inPoint < clip.end_seek) return true;
      if (outPoint > clip.start_seek && outPoint <= clip.end_seek) return true;
      if (inPoint <= clip.start_seek && outPoint >= clip.end_seek) return true;
      return false;
    });
    if (hasOverlap) {
      alert('선택한 구간이 이미 추가된 클립과 겹칩니다. 다른 구간을 선택해주세요.');
      return;
    }
    const currentCamData = cameras.find(c => c.id === selectedSourceCam);

    // 영상 마다 오차 계산 후 보정
    const camOffset = (Number(currentCamData.start_time) - Number(minAbsStart)) / 1000;
    const relativeStartSeek = Math.max(0, inPoint - camOffset); // 시작 상대값
    const relativeEndSeek = Math.max(0, outPoint - camOffset); // 종료 상대값

    const newClip = { // 클립 정보 값 -> 편집 정보로 넘길 때 사용할 예정
      id: Date.now(),
      sequence: 0,
      video_url: currentCamData?.videoUrl || "",
      cam: selectedSourceCam,
      start_seek: relativeStartSeek,
      end_seek: relativeEndSeek,
      duration: outPoint - inPoint,
      global_in: inPoint,
      global_out: outPoint
    };
    const sortedClips = [...savedClips, newClip].sort((a, b) => a.start_seek - b.start_seek); // 저장된 클립들을 시작 시점과 종료 시점으로 정렬 -> 사용자가 클립의 순서를 꼬아놔도 영상의 진행 순서대로 정렬됨
    const updatedClips = sortedClips.map((clip, index) => ({
      ...clip,
      sequence: index + 1
    }));

    console.table(updatedClips);
    setSavedClips(updatedClips);
    setInPoint(outPoint); // 클립 생성 후 Out 지점을 새로운 In 지점으로 지정
    setOutPoint(null);
    setCurrentTime(outPoint); // 현재 시점 또한 Out 지점으로 설정
  };

  // 생성된 클립 제거
  const removeClip = (clipId) => {
    setSavedClips(savedClips.filter(clip => clip.id !== clipId));
  };

  const handleVideoLoaded = (camId, e) => {
    const videoDuration = e.target.duration;
    if (videoDuration && !isNaN(videoDuration)) setDuration(videoDuration);
  };

  const totalClipDuration = savedClips.reduce((sum, clip) => sum + clip.duration, 0);

  // 영상 생성 
  const handleSavedClips = async () => {
    if (savedClips.length === 0) {
      alert("저장된 클립이 없습니다. 먼저 클립을 생성해 주세요.");
      return;
    }
    const clipArrays = savedClips.map(clip => ({
      sequence: clip.sequence,
      video_url: clip.video_url,
      start_seek: clip.start_seek,
      end_seek: clip.end_seek,
      duration: clip.duration
    }));

    const payload = {
      session_id: activeSessionId,
      edit_data: clipArrays
    }
    try {
      const token = localStorage.getItem('accessToken');
      const response = await axios.post(`${API_BASE}/api/web/save_edit_data`, payload, {
        headers: { Authorization: `Bearer ${token}` }
      });
      alert("편집 정보가 성공적으로 저장되었습니다!");
    }
    catch (error) {
      console.error("저장 실패 : ", error);
    }
  };

  // 1080p 영상 주소를 480p 주소로 변환
  const getProxyUrl = (originalUrl) => {
    if (!originalUrl) return "";
    const proxyUrl = originalUrl
      .replace(".mp4", "_proxy.mp4")
      .replace("1080p", "480p")

    return proxyUrl;
  };

  /* 화면 구성 부분 */
  return (
    <div className="edit-page-container">
      {/* 헤더 부분 */}
      <div className="header-container">
        <div className="header-left">
          <img src="/synclab_logo.png" alt="synclab_logo" className="logo-img" onClick={() => navigate('/')} />
        </div>
        <div className="header-right">
          <div className="header-info-text">
            총 클립: {savedClips.length}개 | 총 길이: {formatTime(totalClipDuration)}
          </div>
          <button className="btn-base btn-primary" onClick={handleSavedClips}>
            <Save size={18} />
            프로젝트 저장
          </button>
        </div>
      </div>

      {/* 메인 작업 화면 */}
      <div className="main-content">
        {/* 좌측 사이드 바 */}
        <div className="sidebar">
          {/* 세션 선택 영역 추가 */}
          <div className="session-select-wrapper">
            <h2 className="section-title">📅 세션 관리</h2>
            <div className="session-info-box">
              <span className="session-label">현재 선택된 세션 ID</span>
              <div className="session-value">{activeSessionId || "세션을 선택하세요"}</div>
            </div>

            <select
              value={activeSessionId || ""}
              onChange={(e) => setActiveSessionId(e.target.value)}
              className="session-select-dropdown"
            >
              <option value="" disabled>목록에서 세션 변경</option>
              {sessionList?.map((session) => (
                <option key={session.session_session_id} value={session.session_session_id}>
                  ID: {session.session_session_id}
                </option>
              ))}
            </select>
          </div>

          <h2 className="section-title">
            📁 리소스 보관함
            {/* (선택사항) 개수 표시 */}
            <span style={{ fontSize: '0.8em', marginLeft: '8px', color: '#666' }}>
              ({cameras.length})
            </span>
          </h2>

          <div className="resource-list">
            {/* 1. 카메라 목록이 비어있을 경우 처리 */}
            {cameras.length === 0 ? (
              <div style={{ padding: '20px', textAlign: 'center', color: '#999' }}>
                저장된 영상이 없습니다.
              </div>
            ) : (
              /* 2. 카메라 목록 매핑 */
              cameras.map((cam) => {
                // 현재 이 카메라가 멀티뷰에 선택되었는지 확인
                const isSelected = multiviewCameras.some((c) => c?.id === cam.id);

                return (
                  <div
                    key={cam.id}
                    className="resource-item"
                    style={{
                      // 선택된 경우 투명도와 테두리 스타일 적용
                      opacity: isSelected ? 0.5 : 1,
                      border: isSelected ? '2px solid #10b981' : '1px solid #eee',
                      cursor: 'pointer',
                      marginBottom: '10px',
                      borderRadius: '8px',
                      overflow: 'hidden',
                      backgroundColor: '#fff'
                    }}
                    onClick={() => addCameraToMultiview(cam)}
                  >
                    {/* 비디오 썸네일 영역 */}
                    <div className="video-thumb-wrapper" style={{ position: 'relative', width: '100%', height: '100px', backgroundColor: '#000' }}>
                      {cam.videoUrl ? (
                        <video
                          src={`${getProxyUrl(cam.videoUrl)}#t=0.1`} // 0.1초 지점 썸네일 사용
                          className="video-thumb"
                          preload="metadata"
                          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                          // 마우스 올리면 재생, 떼면 멈춤 (선택사항 효과)
                          onMouseOver={e => e.target.play()}
                          onMouseOut={e => e.target.pause()}
                          muted
                        />
                      ) : (
                        <div className="video-placeholder" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: '#fff' }}>
                          🎥
                        </div>
                      )}

                      {/* 선택됨 체크 표시 */}
                      {isSelected && (
                        <div className="check-badge" style={{
                          position: 'absolute', top: '5px', right: '5px',
                          backgroundColor: '#10b981', color: 'white',
                          borderRadius: '50%', width: '20px', height: '20px',
                          display: 'flex', alignItems: 'center', justifyContent: 'center',
                          fontSize: '12px'
                        }}>
                          ✓
                        </div>
                      )}
                    </div>

                    {/* [추가됨] 비디오 정보 텍스트 (이름, 파일명) */}
                    <div style={{ padding: '8px' }}>
                      <div style={{ fontWeight: 'bold', fontSize: '14px', marginBottom: '2px' }}>
                        {cam.name} {/* 예: CAM 1 */}
                      </div>
                      <div style={{ fontSize: '11px', color: '#666', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {cam.file_name} {/* 예: cam_01_session2.mp4 */}
                      </div>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
        {/* 멀티뷰 & 프로그램 모니터 */}
        <div className="workspace-area">
          <div className="monitor-section">
            <div className="multiview-wrapper">
              <h2 className="section-title" style={{ fontSize: '20px', marginBottom: '8px' }}>멀티뷰 소스</h2>
              <div className="multiview-grid">
                {multiviewCameras.map((cam, slotIndex) => (
                  <div
                    key={slotIndex}
                    className="grid-slot"
                    onClick={() => handleMultiviewSlotClick(slotIndex)}
                    style={{
                      position: 'relative',
                      cursor: cam ? 'pointer' : 'default',
                      border: cam && selectedSourceCam === cam.id ? `4px solid ${cam.color}` : '2px solid #4b5563',
                      boxShadow: cam && selectedSourceCam === cam.id ? `0 0 16px 2px ${cam.color}60` : 'none',
                      backgroundColor: '#000',
                      overflow: 'hidden',
                      minHeight: '150px' // 하얀 화면 방지를 위한 최소 높이
                    }}
                  >
                    {cam ? (
                      <>
                        {/* 동기화 로직: 현재 시간이 비디오 실제 시작 시점보다 전인지 체크 */}
                        {currentTime < (Number(cam.start_time) - Number(minAbsStart)) / 1000 ? (
                          <div className="waiting-signal" style={{
                            position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column',
                            alignItems: 'center', justifyContent: 'center', background: 'rgba(0,0,0,0.85)',
                            color: 'white', zIndex: 10
                          }}>
                            <div className="spinner"></div>
                            <span style={{ fontWeight: 'bold' }}>신호 대기 중...</span>
                            <span style={{ fontSize: '11px', marginTop: '4px', opacity: 0.8 }}>
                              {Math.max(0, Math.ceil((cam.start_time - Number(minAbsStart || 0)) / 1000 - currentTime))}초 후 시작
                            </span>
                          </div>
                        ) : (
                          <video
                            ref={el => { if (el) videoRefs.current[cam.id] = el; }}
                            src={getProxyUrl(cam.videoUrl)}
                            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                            muted
                            playsInline
                            onLoadedMetadata={(e) => handleVideoLoaded(cam.id, e)}
                          />
                        )}

                        <div className="cam-label" style={{ backgroundColor: cam.color || '#333' }}>
                          {cam.name}
                        </div>
                        <button
                          className="btn-remove-cam"
                          onClick={(e) => {
                            e.stopPropagation();
                            removeCameraFromMultiview(slotIndex);
                          }}
                        >
                          ×
                        </button>
                      </>
                    ) : (
                      <div className="empty-slot">
                        <div style={{ fontSize: '40px' }}>➕</div>
                        <div style={{ fontSize: '14px' }}>왼쪽에서 카메라 선택</div>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>

            {/* 선택한 메인 비디오 화면 (프로그램 모니터) */}
            <div className="program-wrapper">
              <h2 className="section-title" style={{ fontSize: '20px', marginBottom: '8px' }}>프로그램 모니터</h2>
              <div className="program-screen">
                {selectedSourceCam === null ? (
                  <div className="empty-slot" style={{ color: '#6b7280' }}>
                    <div style={{ fontSize: '18px' }}>왼쪽에서 카메라를 선택하세요</div>
                  </div>
                ) : (
                  <>
                    <video
                      ref={programVideoRef}
                      src={getProxyUrl(cameras.find(c => c.id === selectedSourceCam)?.videoUrl)}
                      className="program-video"
                      playsInline
                    />
                    <div className="cam-label" style={{ backgroundColor: cameras.find(c => c.id === selectedSourceCam)?.color, top: '16px', left: '16px', fontSize: '14px' }}>
                      {cameras.find(c => c.id === selectedSourceCam)?.name}
                    </div>
                    <div className="timecode-overlay">
                      {formatTime(currentTime)}
                    </div>
                    {(inPoint !== null || outPoint !== null) && (
                      <div className="info-overlay">
                        {inPoint !== null && <div style={{ color: '#34d399' }}>IN: {formatTime(inPoint)}</div>}
                        {outPoint !== null && <div style={{ color: '#f87171' }}>OUT: {formatTime(outPoint)}</div>}
                        {inPoint !== null && outPoint !== null && <div style={{ color: '#fbbf24' }}>길이: {formatTime(outPoint - inPoint)}</div>}
                      </div>
                    )}
                  </>
                )}
              </div>
            </div>
          </div>

          <div className="timeline-container">
            <div className="timeline-header">
              <h2 className="section-title" style={{ margin: 0 }}>타임라인</h2>
              <div className="timeline-controls">
                <button
                  className="btn-icon"
                  onClick={() => {
                    const newTime = Math.max(0, currentTime - 1);
                    setCurrentTime(newTime);
                    Object.values(videoRefs.current).forEach(v => { if (v) v.currentTime = newTime; });
                    if (programVideoRef.current) programVideoRef.current.currentTime = newTime;
                  }}
                  disabled={selectedSourceCam === null}
                  style={{ opacity: selectedSourceCam === null ? 1 : 1 }}
                >
                  <SkipBack size={20} />
                </button>
                <button
                  className="btn-icon"
                  onClick={togglePlay}
                  disabled={selectedSourceCam === null}
                  style={{ opacity: selectedSourceCam === null ? 1 : 1 }}
                >
                  {isPlaying ? <Pause size={20} /> : <Play size={20} />}
                </button>
                <button
                  className="btn-icon"
                  onClick={() => {
                    const newTime = Math.min(duration, currentTime + 1);
                    setCurrentTime(newTime);
                    Object.values(videoRefs.current).forEach(v => { if (v) v.currentTime = newTime; });
                    if (programVideoRef.current) programVideoRef.current.currentTime = newTime;
                  }}
                  disabled={selectedSourceCam === null}
                  style={{ opacity: selectedSourceCam === null ? 1 : 1 }}
                >
                  <SkipForward size={20} />
                </button>
                <div className="divider" />
                <button
                  className="btn-base btn-in"
                  onClick={setIn}
                  disabled={selectedSourceCam === null}
                  style={{ opacity: selectedSourceCam === null ? 0.9 : 1 }}
                >
                  In 설정
                </button>
                <button
                  className="btn-base btn-out"
                  onClick={setOut}
                  disabled={selectedSourceCam === null}
                  style={{ opacity: selectedSourceCam === null ? 0.9 : 1 }}
                >
                  Out 설정
                </button>
                <button
                  className="btn-base btn-add"
                  onClick={addClip}
                  disabled={selectedSourceCam === null || inPoint === null || outPoint === null}
                  style={{ opacity: (selectedSourceCam === null || inPoint === null || outPoint === null) ? 0.9 : 1 }}
                >
                  <Plus size={16} />
                  클립 추가
                </button>
              </div>
            </div>

            {selectedSourceCam !== null && (
              <div className="source-timeline-wrapper">
                <div style={{ fontSize: '12px', color: '#9ca3af', marginBottom: '4px' }}>소스 타임라인 - {cameras.find(c => c.id === selectedSourceCam)?.name}</div>
                <div className="time-ruler">
                  {[...Array(13)].map((_, i) => (
                    <span key={i}>{formatTime((duration / 12) * i).slice(0, 8)}</span>
                  ))}
                </div>
                <div className="timeline-track" ref={timelineRef} onClick={handleTimelineClick}>
                  <div className="track-bg" style={{ backgroundColor: '#ffffff', border: '2px solid rgb(0,0,0)' }} />

                  {savedClips.map((clip) => (
                    <div
                      key={clip.id}
                      className="clip-region"
                      style={{
                        left: `${(clip.global_in / totalSessionDuration) * 100}%`,
                        width: `${((clip.global_out - clip.global_in) / totalSessionDuration) * 100}%`,
                        backgroundColor: cameras.find(c => c.id === clip.cam)?.color,
                        opacity: 0.6
                      }}
                    >
                      {cameras[clip.cam].name}
                    </div>
                  ))}

                  {inPoint !== null && outPoint !== null && (
                    <div
                      className="selection-region"
                      style={{
                        left: `${(inPoint / duration) * 100}%`,
                        width: `${((outPoint - inPoint) / duration) * 100}%`,
                      }}
                    />
                  )}

                  {inPoint !== null && (
                    <div
                      className="marker"
                      onMouseDown={(e) => handleMarkerMouseDown('in', e)}
                      style={{
                        left: `${(inPoint / totalSessionDuration) * 100}%`,
                        backgroundColor: '#10b981',
                      }}
                    >
                      <div className="marker-label" style={{ backgroundColor: '#10b981' }}>IN</div>
                    </div>
                  )}

                  {outPoint !== null && (
                    <div
                      className="marker"
                      onMouseDown={(e) => handleMarkerMouseDown('out', e)}
                      style={{
                        left: `${(outPoint / totalSessionDuration) * 100}%`,
                        backgroundColor: '#ef4444',
                      }}
                    >
                      <div className="marker-label" style={{ backgroundColor: '#ef4444' }}>OUT</div>
                    </div>
                  )}

                  <div className="playhead" style={{ left: `${(currentTime / totalSessionDuration) * 100}%` }}>
                    <div className="playhead-head" />
                  </div>
                </div>
              </div>
            )}

            <div style={{ flex: 1 }}>
              <div className="edit-timeline-label">편집 타임라인 (저장된 클립)</div>
              <div className="clips-container">
                {savedClips.length === 0 ? (
                  <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'black', fontSize: '16px' }}>
                    저장된 클립이 없습니다. 소스에서 구간을 선택하고 "클립 추가"를 눌러주세요.
                  </div>
                ) : (
                  /* EditPage.js 내 타임라인 렌더링 부분 */

                  <div className="clips-track">
                    {savedClips.map((clip) => (
                      <div
                        key={clip.id}
                        className="clip-wrapper"
                        style={{
                          left: `${(clip.global_in / totalSessionDuration) * 100}%`,
                          width: `${(clip.duration / totalSessionDuration) * 100}%`,
                        }}
                      >
                        <div
                          className="clip-item-content"
                          style={{
                            backgroundColor: cameras[clip.cam].color,
                            border: `2px solid ${cameras[clip.cam].color}`,
                            boxShadow: `0 0 12px ${cameras[clip.cam].color}, inset 0 0 6px ${cameras[clip.cam].color}`,
                          }}
                        >
                          <div className="clip-info" style={{ textAlign: 'center' }}>
                            <div className="clip-info-text-main">
                              {cameras[clip.cam].name}
                            </div>
                            <div className="clip-info-text-sub">
                              {formatTime(clip.duration)}
                            </div>
                          </div>

                          <button
                            className="btn-clip-delete"
                            onClick={(e) => {
                              e.stopPropagation();
                              removeClip(clip.id);
                            }}
                          >
                            ×
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default EditPage;