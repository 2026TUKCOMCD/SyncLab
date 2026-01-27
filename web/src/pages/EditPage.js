import React, { useState, useRef, useEffect } from 'react';
import { Scissors, Play, Pause, SkipBack, SkipForward, Save, Plus, Theater } from 'lucide-react';

function EditPage() {

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

  const THEME = {
    bgMain: '#16181dff',      // 아주 깊은 남색 계열 블랙
    bgPanel: '#16181dff',     // 패널 배경
    bgElement: '#1E2330',   // 리스트 아이템 배경
    border: '#2A3441',      // 경계선
    textMain: '#F3F4F6',    // 메인 텍스트
    textMuted: '#9CA3AF',   // 보조 텍스트
    accent: '#3B82F6',      // 기본 강조 (파랑)
    danger: '#EF4444',      // 경고/삭제/Live (빨강)
    success: '#10B981',     // 성공/In점 (초록)
    neonGlow: '0 0 10px rgba(59, 130, 246, 0.5)', // 네온 효과
  };

  const cameras = [
    { id: 0, name: 'CAM 1', color: '#F87171', videoUrl: 'https://synclab-1080p-mp4.s3.ap-northeast-2.amazonaws.com/KakaoTalk_Video_2026-01-20-14-05-14.mp4' },
    { id: 1, name: 'CAM 2', color: '#60A5FA', videoUrl: 'https://synclab-1080p-mp4.s3.ap-northeast-2.amazonaws.com/KakaoTalk_Video_2026-01-20-14-58-57.mp4' },
    { id: 2, name: 'CAM 3', color: '#34D399', videoUrl: 'https://synclab-1080p-mp4.s3.ap-northeast-2.amazonaws.com/KakaoTalk_Video_2026-01-21-13-47-08.mp4' },
    { id: 3, name: 'CAM 4', color: '#FBBF24', videoUrl: 'https://synclab-1080p-mp4.s3.ap-northeast-2.amazonaws.com/KakaoTalk_Video_2026-01-21-13-47-35.mp4' },
  ];

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
      if (animationRef.current) {
        cancelAnimationFrame(animationRef.current);
      }
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
    if (programVideoRef.current) {
      programVideoRef.current.currentTime = 0;
    }
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
    if (camera) {
      handleSourceCamClick(camera.id);
    }
  };
  const handleTimelineClick = (e) => {
    if (selectedSourceCam === null) return;
    if (isDraggingIn || isDraggingOut) return;

    const rect = e.currentTarget.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const percent = x / rect.width;
    const newTime = percent * duration;

    setCurrentTime(newTime);

    Object.values(videoRefs.current).forEach(v => {
      if (v) v.currentTime = newTime;
    });
    if (programVideoRef.current) {
      programVideoRef.current.currentTime = newTime;
    }
  };

  const handleMarkerMouseDown = (type, e) => {
    e.stopPropagation();
    if (type === 'in') {
      setIsDraggingIn(true);
    } else {
      setIsDraggingOut(true);
    }
  };

  const handleMarkerDrag = (e) => {
    if (!isDraggingIn && !isDraggingOut) return;
    if (!timelineRef.current) return;

    const rect = timelineRef.current.getBoundingClientRect();
    const x = Math.max(0, Math.min(e.clientX - rect.left, rect.width));
    const percent = x / rect.width;
    let newTime = Math.max(0, Math.min(percent * duration, duration));

    if (isDraggingIn) {
      if (outPoint !== null && newTime >= outPoint) {
        newTime = outPoint - 0.1;
      }

      const conflictClip = savedClips.find(clip =>
        newTime >= clip.inPoint && newTime < clip.outPoint
      );
      if (conflictClip) {
        newTime = conflictClip.outPoint;
      }

      setInPoint(newTime);
    } else if (isDraggingOut) {
      if (inPoint !== null && newTime <= inPoint) {
        newTime = inPoint + 0.1;
      }

      const conflictClip = savedClips.find(clip =>
        newTime > clip.inPoint && newTime <= clip.outPoint
      );
      if (conflictClip) {
        newTime = conflictClip.inPoint;
      }

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
    if (outPoint !== null && currentTime > outPoint) {
      setOutPoint(null);
    }
  };

  const setOut = () => {
    if (selectedSourceCam === null) return;
    setOutPoint(currentTime);
    if (inPoint !== null && currentTime < inPoint) {
      setInPoint(null);
    }
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
    if (videoDuration && !isNaN(videoDuration)) {
      setDuration(videoDuration);
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('userName');

    alert("로그아웃 되었습니다.");
    window.location.href = '/';
  };

  const totalClipDuration = savedClips.reduce((sum, clip) => sum + clip.duration, 0);

  return (
    <div style={{ minheight: '100vh', backgroundColor: THEME.bgMain, color: THEME.textMain, display: 'flex', flexDirection: 'column' }}>
      {/* 헤더 부분 */}
      <div style={{ backgroundColor: THEME.bgPanel, borderBottom: '1px solid #374151', padding: '12px 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{ width: '32px', height: '32px', background: 'linear-gradient(135deg, #8aea5eff, #5f9de9ff)', borderRadius: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Scissors size={18} color="white" />
          </div>
          <h1 style={{ fontSize: '20px', fontWeight: 'bold' }}>SyncLab Editor</h1>
        </div>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <div style={{ fontSize: '14px', color: '#9ca3af' }}>
            총 클립: {savedClips.length}개 | 총 길이: {formatTime(totalClipDuration)}
          </div>
          <button style={{ padding: '8px 16px', backgroundColor: '#2563eb', borderRadius: '6px', display: 'flex', alignItems: 'center', gap: '8px', border: 'none', color: 'white', cursor: 'pointer' }}>
            <Save size={18} />
            프로젝트 저장
          </button>
          <button onClick={handleLogout}>로그아웃</button>
        </div>
      </div>

      {/* 메인 작업 화면 */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>

        {/* 좌측 사이드 바 */}
        <div style={{ width: '200px', backgroundColor: THEME.bgPanel, borderRight: `1px solid ${THEME.border}`, padding: '16px', overflowY: 'auto' }}>
          <h2 style={{ fontSize: '18px', fontWeight: '600', marginBottom: '16px' }}>💾 리소스 보관함</h2>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {cameras.map((cam) => (
              <div key={cam.id} style={{
                backgroundColor: '#374151', borderRadius: '8px', padding: '12px', cursor: 'pointer',
                opacity: multiviewCameras.some(c => c?.id === cam.id) ? 0.5 : 1,
                border: multiviewCameras.some(c => c?.id === cam.id) ? '2px solid #10b981' : 'none'
              }}
                onClick={() => addCameraToMultiview(cam)}
              >
                <div style={{ aspectRatio: '16/9', overflow: 'hidden', position: 'relative' }}>
                  {cam.videoUrl ? (
                    <video
                      src={`${cam.videoUrl}#t=0.1`}  // 0.1초 지점의 프레임 로드
                      muted
                      style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                      preload="metadata"
                    />
                  ) : (
                    <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '32px' }}>
                      🎥
                    </div>
                  )}
                  {multiviewCameras.some(c => c?.id === cam.id) && (
                    <div style={{ position: 'absolute', top: '4px', right: '4px', backgroundColor: '#10b981', color: 'white', borderRadius: '50%', width: '24px', height: '24px', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '14px', fontWeight: 'bold' }}>
                      ✓
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
        {/* 멀티뷰 부분 */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
          <div style={{ flex: 1, display: 'flex', padding: '16px', gap: '16px' }}>
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
              <h2 style={{ fontSize: '18px', fontWeight: '600', marginBottom: '8px', color: '#9ca3af' }}>멀티뷰 소스</h2>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', flex: 1, gap: '4px' }}>
                {multiviewCameras.map((cam, slotIndex) => (
                  <div
                    key={slotIndex}
                    onClick={() => handleMultiviewSlotClick(slotIndex)}
                    style={{
                      position: 'relative',
                      backgroundColor: 'black',
                      height: '300px',
                      overflow: 'hidden',
                      cursor: cam ? 'pointer' : 'default',
                      border: cam && selectedSourceCam === cam.id
                        ? `4px solid ${cam.color}`
                        : '2px #4b5563',
                      boxShadow: cam && selectedSourceCam === cam.id
                        ? `0 0 16px 2px ${cam.color}60`
                        : 'none',
                      transition: 'all 0.1s'
                    }}
                  >
                    {cam ? (
                      <>
                        <video
                          ref={el => {
                            if (el) videoRefs.current[cam.id] = el;  // ref에 비디오 등록
                          }}
                          src={cam.videoUrl}
                          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                          muted                    // 음소거 (멀티뷰는 음소거)
                          playsInline             // 모바일 대응
                          onLoadedMetadata={(e) => handleVideoLoaded(cam.id, e)}  // 비디오 로드 시
                        />
                        <div style={{ position: 'absolute', top: '8px', left: '8px', padding: '4px 8px', borderRadius: '4px', fontSize: '12px', fontWeight: 'bold', backgroundColor: cam.color }}>
                          {cam.name}
                        </div>
                        {selectedSourceCam === cam.id && (
                          <div style={{ position: 'absolute', top: '8px', right: '8px' }}>
                            <div style={{ width: '12px', height: '12px', backgroundColor: '#dc2626', borderRadius: '50%', animation: 'pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite' }} />
                          </div>
                        )}
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            removeCameraFromMultiview(slotIndex);
                          }}
                          style={{
                            position: 'absolute',
                            top: '8px',
                            right: '8px',
                            backgroundColor: '#dc2626',
                            borderRadius: '50%',
                            width: '24px',
                            height: '24px',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            fontSize: '16px',
                            border: 'none',
                            color: 'white',
                            cursor: 'pointer',
                            opacity: 0,
                            transition: 'opacity 0.2s'
                          }}
                          onMouseEnter={(e) => e.currentTarget.style.opacity = '1'}
                          onMouseLeave={(e) => e.currentTarget.style.opacity = '0'}
                        >
                          ×
                        </button>
                      </>
                    ) : (
                      <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column', gap: '8px', color: '#6b7280' }}>
                        <div style={{ fontSize: '40px' }}>➕</div>
                        <div style={{ fontSize: '12px' }}>왼쪽에서 카메라 선택</div>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
            {/* 선택한 메인 비디오 화면 */}
            <div style={{ width: '50%', display: 'flex', flexDirection: 'column' }}>
              <h2 style={{ fontSize: '18px', fontWeight: '600', marginBottom: '8px', color: '#9ca3af' }}>프로그램 모니터</h2>
              <div style={{ flex: 1, backgroundColor: 'black', borderRadius: '8px', overflow: 'hidden', position: 'relative', height: '600px' }}>
                {selectedSourceCam === null ? (
                  <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column', gap: '16px', color: '#6b7280' }}>
                    <div style={{ fontSize: '18px' }}>왼쪽에서 카메라를 선택하세요</div>
                  </div>
                ) : (
                  <>
                    <video
                      ref={programVideoRef}
                      src={cameras.find(c => c.id === selectedSourceCam)?.videoUrl}
                      style={{ width: '100%', height: '100%', objectFit: 'contain' }}
                      playsInline
                    />
                    <div style={{ position: 'absolute', top: '16px', left: '16px', padding: '8px 12px', borderRadius: '6px', fontSize: '14px', fontWeight: 'bold', backgroundColor: cameras.find(c => c.id === selectedSourceCam)?.color }}>
                      {cameras.find(c => c.id === selectedSourceCam)?.name}
                    </div>
                    <div style={{ position: 'absolute', bottom: '16px', left: '16px', backgroundColor: 'rgba(0,0,0,0.8)', padding: '8px 16px', borderRadius: '6px', fontFamily: 'monospace', fontSize: '24px' }}>
                      {formatTime(currentTime)}
                    </div>
                    {(inPoint !== null || outPoint !== null) && (
                      <div style={{ position: 'absolute', top: '16px', right: '16px', backgroundColor: 'rgba(0,0,0,0.8)', padding: '8px 12px', borderRadius: '6px', fontSize: '12px' }}>
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

          <div style={{ minHeight: '320px', maxHeight: '400px', backgroundColor: THEME.bgPanel, borderTop: '1px solid #374151', padding: '16px', display: 'flex', flexDirection: 'column', overflowY: 'auto' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '12px' }}>
              <h2 style={{ fontSize: '20px', fontWeight: '600', color: '#9ca3af' }}>타임라인</h2>
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginRight: '12px' }}>
                <button onClick={() => {
                  const newTime = Math.max(0, currentTime - 1);
                  setCurrentTime(newTime);
                  Object.values(videoRefs.current).forEach(v => { if (v) v.currentTime = newTime; });
                  if (programVideoRef.current) programVideoRef.current.currentTime = newTime;
                }} disabled={selectedSourceCam === null} style={{ padding: '8px', borderRadius: '6px', backgroundColor: 'transparent', border: 'none', color: 'white', cursor: 'pointer', opacity: selectedSourceCam === null ? 0.5 : 1 }}>
                  <SkipBack size={20} />
                </button>
                <button onClick={togglePlay} disabled={selectedSourceCam === null} style={{ padding: '12px', backgroundColor: '#2563eb', borderRadius: '8px', border: 'none', color: 'white', cursor: 'pointer', opacity: selectedSourceCam === null ? 0.5 : 1 }}>
                  {isPlaying ? <Pause size={20} /> : <Play size={20} />}
                </button>
                <button onClick={() => {
                  const newTime = Math.min(duration, currentTime + 1);
                  setCurrentTime(newTime);
                  Object.values(videoRefs.current).forEach(v => { if (v) v.currentTime = newTime; });
                  if (programVideoRef.current) programVideoRef.current.currentTime = newTime;
                }} disabled={selectedSourceCam === null} style={{ padding: '8px', borderRadius: '6px', backgroundColor: 'transparent', border: 'none', color: 'white', cursor: 'pointer', opacity: selectedSourceCam === null ? 0.5 : 1 }}>
                  <SkipForward size={20} />
                </button>
                <div style={{ width: '1px', height: '24px', backgroundColor: '#4b5563', margin: '0 8px' }} />
                <button onClick={setIn} disabled={selectedSourceCam === null} style={{ padding: '8px 12px', backgroundColor: '#059669', borderRadius: '6px', fontSize: '14px', border: 'none', color: 'white', cursor: 'pointer', opacity: selectedSourceCam === null ? 0.5 : 1 }}>
                  In 설정
                </button>
                <button onClick={setOut} disabled={selectedSourceCam === null} style={{ padding: '8px 12px', backgroundColor: '#dc2626', borderRadius: '6px', fontSize: '14px', border: 'none', color: 'white', cursor: 'pointer', opacity: selectedSourceCam === null ? 0.5 : 1 }}>
                  Out 설정
                </button>
                <button onClick={addClip} disabled={selectedSourceCam === null || inPoint === null || outPoint === null} style={{ padding: '8px 12px', backgroundColor: '#d97706', borderRadius: '6px', fontSize: '14px', display: 'flex', alignItems: 'center', gap: '8px', border: 'none', color: 'white', cursor: 'pointer', opacity: (selectedSourceCam === null || inPoint === null || outPoint === null) ? 0.5 : 1 }}>
                  <Plus size={16} />
                  클립 추가
                </button>
              </div>
            </div>

            {selectedSourceCam !== null && (
              <div style={{ marginBottom: '16px' }}>
                <div style={{ fontSize: '12px', color: '#9ca3af', marginBottom: '4px' }}>소스 타임라인 - {cameras.find(c => c.id === selectedSourceCam)?.name}</div>
                <div style={{ position: 'relative' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', color: '#6b7280', marginBottom: '4px', padding: '0 4px' }}>
                    {[...Array(13)].map((_, i) => (
                      <span key={i}>{formatTime((duration / 12) * i).slice(0, 8)}</span>
                    ))}
                  </div>
                  <div
                    ref={timelineRef}
                    onClick={handleTimelineClick}
                    style={{ position: 'relative', height: '64px', backgroundColor: '#111827', borderRadius: '8px', overflow: 'hidden', cursor: 'pointer' }}
                  >
                    <div style={{ position: 'absolute', inset: 0, opacity: 0.5, backgroundColor: cameras.find(c => c.id === selectedSourceCam)?.color }} />

                    {savedClips.map((clip) => (
                      <div
                        key={clip.id}
                        style={{
                          position: 'absolute',
                          top: 0,
                          bottom: 0,
                          left: `${(clip.inPoint / duration) * 100}%`,
                          width: `${((clip.outPoint - clip.inPoint) / duration) * 100}%`,
                          backgroundColor: 'rgba(107, 114, 128, 0.5)',
                          border: '1px solid #9ca3af',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: '12px',
                          color: '#d1d5db'
                        }}
                      >
                        {cameras[clip.cam].name}
                      </div>
                    ))}

                    {inPoint !== null && outPoint !== null && (
                      <div
                        style={{
                          position: 'absolute',
                          top: 0,
                          bottom: 0,
                          left: `${(inPoint / duration) * 100}%`,
                          width: `${((outPoint - inPoint) / duration) * 100}%`,
                          border: '2px solid #fbbf24',
                          backgroundColor: 'rgba(251, 191, 36, 0.3)'
                        }}
                      />
                    )}

                    {inPoint !== null && (
                      <div
                        onMouseDown={(e) => handleMarkerMouseDown('in', e)}
                        style={{
                          position: 'absolute',
                          top: 0,
                          bottom: 0,
                          left: `${(inPoint / duration) * 100}%`,
                          width: '4px',
                          backgroundColor: '#10b981',
                          cursor: 'ew-resize',
                          zIndex: 20
                        }}
                      >
                        <div style={{ position: 'absolute', top: '-8px', left: '50%', transform: 'translateX(-50%)', fontSize: '12px', backgroundColor: '#10b981', padding: '0 4px', borderRadius: '4px', whiteSpace: 'nowrap', pointerEvents: 'none' }}>
                          IN
                        </div>
                      </div>
                    )}

                    {outPoint !== null && (
                      <div
                        onMouseDown={(e) => handleMarkerMouseDown('out', e)}
                        style={{
                          position: 'absolute',
                          top: 0,
                          bottom: 0,
                          left: `${(outPoint / duration) * 100}%`,
                          width: '4px',
                          backgroundColor: '#ef4444',
                          cursor: 'ew-resize',
                          zIndex: 20
                        }}
                      >
                        <div style={{ position: 'absolute', top: '-8px', left: '50%', transform: 'translateX(-50%)', fontSize: '12px', backgroundColor: '#ef4444', padding: '0 4px', borderRadius: '4px', whiteSpace: 'nowrap', pointerEvents: 'none' }}>
                          OUT
                        </div>
                      </div>
                    )}

                    <div
                      style={{
                        position: 'absolute',
                        top: 0,
                        bottom: 0,
                        left: `${(currentTime / duration) * 100}%`,
                        width: '2px',
                        backgroundColor: 'white',
                        zIndex: 10,
                        pointerEvents: 'none'
                      }}
                    >
                      <div style={{ position: 'absolute', top: '-8px', left: '50%', transform: 'translateX(-50%) rotate(45deg)', width: '12px', height: '12px', backgroundColor: 'white' }} />
                    </div>
                  </div>
                </div>
              </div>
            )}

            <div style={{ flex: 1 }}>
              <div style={{ fontSize: '12px', color: '#9ca3af', marginBottom: '4px' }}>편집 타임라인 (저장된 클립)</div>
              <div style={{ position: 'relative', height: '120px', backgroundColor: '#111827', borderRadius: '8px', padding: '8px', overflowX: 'auto', minheight: '100px', maxHeight: '160px', overflowY: 'hidden' }}>
                {savedClips.length === 0 ? (
                  <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#6b7280', fontSize: '14px' }}>
                    저장된 클립이 없습니다. 소스에서 구간을 선택하고 "클립 추가"를 눌러주세요.
                  </div>
                ) : (
                  <div style={{ display: 'flex', gap: '8px', height: '100%', alignItems: 'center' }}>
                    {savedClips.map((clip) => (
                      <div
                        key={clip.id}
                        style={{
                          position: 'relative',
                          height: '80px',
                          borderRadius: '6px',
                          border: '2px solid black',
                          flexShrink: 0,
                          width: `${Math.max(clip.duration * 3, 60)}px`,
                          backgroundColor: cameras[clip.cam].color
                        }}
                      >
                        <div style={{ padding: '8px', fontSize: '12px', height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                          <div style={{ fontWeight: '600' }}>{cameras[clip.cam].name}</div>
                          <div style={{ fontSize: '10px', opacity: 0.8 }}>{formatTime(clip.duration)}</div>
                        </div>
                        <button
                          onClick={() => removeClip(clip.id)}
                          style={{
                            position: 'absolute',
                            top: '-8px',
                            right: '-8px',
                            backgroundColor: '#dc2626',
                            borderRadius: '50%',
                            width: '20px',
                            height: '20px',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            fontSize: '12px',
                            border: 'none',
                            color: 'white',
                            cursor: 'pointer'
                          }}
                        >
                          ×
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      <style>{`
        @keyframes pulse {
          0%, 100% {
            opacity: 1;
          }
          50% {
            opacity: 0.5;
          }
        }
      `}</style>
    </div>
  );
};
export default EditPage;