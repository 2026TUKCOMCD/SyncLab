import { Scissors, Play, Pause, SkipBack, SkipForward, Save, Plus, Theater, Radius } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import '../App.css';
import axios from 'axios'
import React, { useState, useRef, useEffect } from 'react';
// useEffect()는 화면이 구성된 뒤에 실행되어 재랜더링 하는 방식이지만 
// useState는 랜더링 이전에 수행되어 불필요한 랜더링이 발생하지 않음.

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
  const [loading, setLoading] = useState(true);
  const colors = ['#F87171', '#60A5FA', '#34D399', '#FBBF24'];

  useEffect(() => {
    const fetchVideos = async () => {
      const token = localStorage.getItem('accessToken');
      console.log("현재 토큰:", token);
      if (!token) {
        alert("로그인이 필요합니다.");
        navigate("/login");
      }
      // GET 요청 수행
      try {
        const response = await axios.get('http://localhost:8000/api/web/list', {
          headers: {
            Authorization: `Bearer ${token}` // Header에 토큰 값 실어서 보내기
          }
        });
        console.log("서버 응답: ", response.data);
        setCameras(response.data.videos);
      }
      catch (error) {
        console.error('네트워크 에러:', error);

        if(error.response && error.response.status == 401){
          alert("인증이 만료되었습니다. 다시 로그인해주세요.");
        }
      }
      finally {
        setLoading(false);
      }
    };
    fetchVideos();
  }, []);

  useEffect(() => {
    if (isDraggingIn || isDraggingOut) {
      window.addEventListener('mousemove', handleMarkerDrag);
      window.addEventListener('mouseup', handleMarkerMouseUp);
      return () => {
        window.removeEventListener('mousemove', handleMarkerDrag);
        window.removeEventListener('mouseup', handleMarkerMouseUp);
      };
    }
  }, [isDraggingIn, isDraggingOut]);

  useEffect(() => {
    if (isPlaying && selectedSourceCam !== null) {
      const updateTime = () => {
        const video = videoRefs.current[selectedSourceCam];
        if (video && !video.paused && !video.ended) {
          setCurrentTime(video.currentTime);
          animationRef.current = requestAnimationFrame(updateTime);
        }
      };
      animationRef.current = requestAnimationFrame(updateTime);
    }
    return () => {
      if (animationRef.current) cancelAnimationFrame(animationRef.current);
    };
  }, [isPlaying, selectedSourceCam]);

  const togglePlay = () => {
    if (selectedSourceCam === null) return;
    const allVideos = Object.values(videoRefs.current).filter(v => v);
    const programVideo = programVideoRef.current;
    if (!isPlaying) {
      allVideos.forEach(v => v.play().catch(() => { }));
      if (programVideo) programVideo.play().catch(() => { });
      setIsPlaying(true);
    } else {
      allVideos.forEach(v => v.pause());
      if (programVideo) programVideo.pause();
      setIsPlaying(false);
    }
  };

  const formatTime = (seconds) => {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = Math.floor(seconds % 60);
    const f = Math.floor((seconds % 1) * 30);
    return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}:${f.toString().padStart(2, '0')}`;
  };

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

  const handleMultiviewSlotClick = (slotIndex) => {
    const camera = multiviewCameras[slotIndex];
    if (camera) handleSourceCamClick(camera.id);
  };

  const handleTimelineClick = (e) => {
    if (selectedSourceCam === null) return;
    if (isDraggingIn || isDraggingOut) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const percent = x / rect.width;
    const newTime = percent * duration;
    setCurrentTime(newTime);
    Object.values(videoRefs.current).forEach(v => { if (v) v.currentTime = newTime; });
    if (programVideoRef.current) programVideoRef.current.currentTime = newTime;
  };

  const handleMarkerMouseDown = (type, e) => {
    e.stopPropagation();
    if (type === 'in') setIsDraggingIn(true);
    else setIsDraggingOut(true);
  };

  const handleMarkerDrag = (e) => {
    if (!isDraggingIn && !isDraggingOut) return;
    if (!timelineRef.current) return;
    const rect = timelineRef.current.getBoundingClientRect();
    const x = Math.max(0, Math.min(e.clientX - rect.left, rect.width));
    const percent = x / rect.width;
    let newTime = Math.max(0, Math.min(percent * duration, duration));

    if (isDraggingIn) {
      if (outPoint !== null && newTime >= outPoint) newTime = outPoint - 0.1;
      const conflictClip = savedClips.find(clip => newTime >= clip.inPoint && newTime < clip.outPoint);
      if (conflictClip) newTime = conflictClip.outPoint;
      setInPoint(newTime);
    } else if (isDraggingOut) {
      if (inPoint !== null && newTime <= inPoint) newTime = inPoint + 0.1;
      const conflictClip = savedClips.find(clip => newTime > clip.inPoint && newTime <= clip.outPoint);
      if (conflictClip) newTime = conflictClip.inPoint;
      setOutPoint(newTime);
    }
  };

  const handleMarkerMouseUp = () => {
    setIsDraggingIn(false);
    setIsDraggingOut(false);
  };

  const setIn = () => {
    if (selectedSourceCam === null) return;
    setInPoint(currentTime);
    if (outPoint !== null && currentTime > outPoint) setOutPoint(null);
  };

  const setOut = () => {
    if (selectedSourceCam === null) return;
    setOutPoint(currentTime);
    if (inPoint !== null && currentTime < inPoint) setInPoint(null);
  };

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
      if (inPoint >= clip.inPoint && inPoint < clip.outPoint) return true;
      if (outPoint > clip.inPoint && outPoint <= clip.outPoint) return true;
      if (inPoint <= clip.inPoint && outPoint >= clip.outPoint) return true;
      return false;
    });
    if (hasOverlap) {
      alert('선택한 구간이 이미 추가된 클립과 겹칩니다. 다른 구간을 선택해주세요.');
      return;
    }
    const newClip = {
      id: Date.now(),
      cam: selectedSourceCam,
      inPoint,
      outPoint,
      duration: outPoint - inPoint,
    };
    const updatedClips = [...savedClips, newClip].sort((a, b) => a.inPoint - b.inPoint);
    setSavedClips(updatedClips);
    setInPoint(outPoint);
    setOutPoint(null);
    setCurrentTime(outPoint);
  };

  const removeClip = (clipId) => {
    setSavedClips(savedClips.filter(clip => clip.id !== clipId));
  };

  const handleVideoLoaded = (camId, e) => {
    const videoDuration = e.target.duration;
    if (videoDuration && !isNaN(videoDuration)) setDuration(videoDuration);
  };

  const totalClipDuration = savedClips.reduce((sum, clip) => sum + clip.duration, 0);

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
          <button className="btn-base btn-primary">
            <Save size={18} />
            프로젝트 저장
          </button>
        </div>
      </div>

      {/* 메인 작업 화면 */}
      <div className="main-content">
        {/* 좌측 사이드 바 */}
        <div className="sidebar">
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
                          src={`${cam.videoUrl}#t=0.1`} // 0.1초 지점 썸네일 사용
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
                      cursor: cam ? 'pointer' : 'default',
                      border: cam && selectedSourceCam === cam.id ? `4px solid ${cam.color}` : '2px solid #4b5563',
                      boxShadow: cam && selectedSourceCam === cam.id ? `0 0 16px 2px ${cam.color}60` : 'none',
                    }}
                  >
                    {cam ? (
                      <>
                        <video
                          ref={el => { if (el) videoRefs.current[cam.id] = el; }}
                          src={cam.videoUrl}
                          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                          muted
                          playsInline
                          onLoadedMetadata={(e) => handleVideoLoaded(cam.id, e)}
                        />
                        <div className="cam-label" style={{ backgroundColor: cam.color }}>
                          {cam.name}
                        </div>
                        {selectedSourceCam === cam.id && (
                          <div style={{ position: 'absolute', top: '8px', right: '8px' }}>
                            <div className="recording-dot" />
                          </div>
                        )}
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
                      src={cameras.find(c => c.id === selectedSourceCam)?.videoUrl}
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
                  <div className="track-bg" style={{ backgroundColor: cameras.find(c => c.id === selectedSourceCam)?.color }} />

                  {savedClips.map((clip) => (
                    <div
                      key={clip.id}
                      className="clip-region"
                      style={{
                        left: `${(clip.inPoint / duration) * 100}%`,
                        width: `${((clip.outPoint - clip.inPoint) / duration) * 100}%`,
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
                        left: `${(inPoint / duration) * 100}%`,
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
                        left: `${(outPoint / duration) * 100}%`,
                        backgroundColor: '#ef4444',
                      }}
                    >
                      <div className="marker-label" style={{ backgroundColor: '#ef4444' }}>OUT</div>
                    </div>
                  )}

                  <div className="playhead" style={{ left: `${(currentTime / duration) * 100}%` }}>
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
                  <div className="clips-track">
                    {savedClips.map((clip) => (
                      <div
                        key={clip.id}
                        className="clip-item"
                        style={{
                          width: `${Math.max(clip.duration * 3, 60)}px`,
                          backgroundColor: cameras[clip.cam].color
                        }}
                      >
                        <div className="clip-info">
                          <div style={{ fontWeight: '600' }}>{cameras[clip.cam].name}</div>
                          <div style={{ fontSize: '10px', opacity: 0.8 }}>{formatTime(clip.duration)}</div>
                        </div>
                        <button className="btn-clip-close" onClick={() => removeClip(clip.id)}>×</button>
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