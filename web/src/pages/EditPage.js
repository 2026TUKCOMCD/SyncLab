import { useNavigate, useLocation } from 'react-router-dom';
import '../App.css';
import axios from 'axios'
import React, { useState, useRef, useEffect } from 'react';
import EditHeader from '../components/edit/EditHeader';
import CameraResourceList from '../components/edit/CameraResourceList';
import MultiviewGrid from '../components/edit/MultiviewGrid';
import ProgramMonitor from '../components/edit/ProgramMonitor';
import ControlBar from '../components/edit/ControlBar';
import SourceTimeline from '../components/edit/SourceTimeline';
import EditTimeline from '../components/edit/EditTimeline';
import HighlightPanel from '../components/edit/HighlightPanel';

// API 베이스 URL: REACT_APP_API_URL 환경변수 → 없으면 빈 문자열(상대경로, Docker nginx 프록시 사용)
const API_BASE = process.env.REACT_APP_API_URL || '';
// useEffect()는 화면이 구성된 뒤에 실행되어 재랜더링 하는 방식이지만 useState는 랜더링 이전에 수행되어 불필요한 랜더링이 발생하지 않음.
// useRef()는 랜더링 되어도 변하지 않는 참조 변수 또는 객체

function EditPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(180);
  const [selectedSourceCam, setSelectedSourceCam] = useState(null);
  const [inPoint, setInPoint] = useState(null);
  const [outPoint, setOutPoint] = useState(null);
  const [savedClips, setSavedClips] = useState(location.state?.restoredClips || []);
  const [isDraggingIn, setIsDraggingIn] = useState(false);
  const [isDraggingOut, setIsDraggingOut] = useState(false);
  const videoRefs = useRef({});
  const programVideoRef = useRef(null);
  const timelineRef = useRef(null);
  const animationRef = useRef(null);
  const pendingProgramSeek = useRef(null); // 카메라 전환 시 새 프로그램 비디오 seek 대기값
  const replayEndRef = useRef(null); // 리플레이 종료 시점 저장
  const previewClipIndexRef = useRef(null); // 전체 미리보기 현재 클립 인덱스
  const isPreviewModeRef = useRef(false); // 전체 미리보기 모드 여부
  const settingsPanelRef = useRef(null);   // 드롭다운 외부 클릭 감지용
  const [showSettings, setShowSettings] = useState(false);
  const [playbackRate, setPlaybackRate] = useState(1);
  const playbackRateRef = useRef(1); // 동기화 루프(rAF)에서 최신 재생 속도를 읽기 위한 ref
  const [fps, setFps] = useState(30);
  const [skipFrames, setSkipFrames] = useState(1);
  const [compactTimeline, setCompactTimeline] = useState(false);
  const [highlightClips, setHighlightClips] = useState([]);
  const [showHighlightPanel, setShowHighlightPanel] = useState(false);
  const [multiviewCameras, setMultiviewCameras] = useState([null, null, null, null]);
  const [cameras, setCameras] = useState([]);
  const [sessionList, setSessionList] = useState([]); // 사용자가 참여한 여러 세션 목록을 위한 변수
  const [activeSessionId, setActiveSessionId] = useState(null); // 현재 사용자가 선택한 세션에 대한 변수
  const skipClipReset = useRef(!!location.state?.restoredClips);

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
          const restoredSessionId = location.state?.restoredSessionId;
          const target = sessions.find(s => s.session_session_id === restoredSessionId) || sessions[0];
          setActiveSessionId(target?.session_session_id);
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
        if (skipClipReset.current) {
          skipClipReset.current = false;
        } else {
          setSavedClips([]); // 세션 재선택 시 클립 초기화
        }
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

  // 재생 속도 변경 시 모든 비디오에 즉시 반영
  useEffect(() => {
    playbackRateRef.current = playbackRate;
    Object.values(videoRefs.current).forEach(v => { if (v) v.playbackRate = playbackRate; });
    if (programVideoRef.current) programVideoRef.current.playbackRate = playbackRate;
  }, [playbackRate]);

  // 설정 드롭다운 외부 클릭 시 닫기
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (settingsPanelRef.current && !settingsPanelRef.current.contains(e.target)) {
        setShowSettings(false);
      }
    };
    if (showSettings) document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [showSettings]);

  // 현재 재생 중인 동영상의 시간을 보여주기 위한 함수로 타임라인의 Bar가 이동하거나 숫자 표시
  useEffect(() => {
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

          // 리플레이/미리보기 종료 시점 도달 시 처리
          if (replayEndRef.current !== null && calculateMasterTime >= replayEndRef.current) {
            if (isPreviewModeRef.current) {
              const sorted = [...savedClips].sort((a, b) => a.sequence - b.sequence);
              const nextIndex = previewClipIndexRef.current + 1;
              if (nextIndex < sorted.length) {
                const next = sorted[nextIndex];
                const isInMultiview = multiviewCameras.some(cam => cam && cam.id === next.cam);
                if (isInMultiview) {
                  previewClipIndexRef.current = nextIndex;
                  replayEndRef.current = next.global_out;
                  const masterTime = next.global_in;
                  setCurrentTime(masterTime);
                  setSelectedSourceCam(next.cam);
                  multiviewCameras.forEach(cam => {
                    if (!cam) return;
                    const v = videoRefs.current[cam.id];
                    if (!v) return;
                    const offset = (Number(cam.start_time) - Number(minAbsStart)) / 1000;
                    v.currentTime = Math.max(0, masterTime - offset);
                  });
                  const nextCam = cameras.find(c => c.id === next.cam);
                  if (nextCam && programVideoRef.current) {
                    const offset = (Number(nextCam.start_time) - Number(minAbsStart)) / 1000;
                    pendingProgramSeek.current = Math.max(0, masterTime - offset);
                    programVideoRef.current.currentTime = Math.max(0, masterTime - offset);
                  }
                  // 클립별 슬로우 모션(slow_rate) 반영
                  playbackRateRef.current = next.slow_rate || 1.0;
                  const nextMaster = videoRefs.current[next.cam];
                  if (nextMaster) {
                    nextMaster.playbackRate = playbackRateRef.current;
                    nextMaster.play().catch(() => {});
                  }
                  if (programVideoRef.current) {
                    programVideoRef.current.playbackRate = playbackRateRef.current;
                    programVideoRef.current.play().catch(() => {});
                  }
                  return;
                }
              }
              isPreviewModeRef.current = false;
              previewClipIndexRef.current = null;
            }
            Object.values(videoRefs.current).forEach(v => { if (v) v.pause(); });
            if (programVideoRef.current) programVideoRef.current.pause();
            replayEndRef.current = null;
            setIsPlaying(false);
            // 클립 재생 종료 → 전역 재생 속도로 복원 (ref뿐 아니라 실제 video 엘리먼트에도 반영)
            playbackRateRef.current = playbackRate;
            Object.values(videoRefs.current).forEach(v => { if (v) v.playbackRate = playbackRate; });
            if (programVideoRef.current) programVideoRef.current.playbackRate = playbackRate;
            return;
          }

          multiviewCameras.forEach(cam => {
            if (!cam || cam.id === selectedSourceCam) return;
            const v = videoRefs.current[cam.id];
            if (!v) return;

            const camOffset = (Number(cam.start_time) - Number(minAbsStart)) / 1000;
            const targetTime = calculateMasterTime - camOffset;

            if (targetTime >= 0 && targetTime <= (cam.duration || 10000)) {
              if (v.paused) v.play().catch(() => { });

              const diff = v.currentTime - targetTime;
              const baseRate = playbackRateRef.current;
              if (Math.abs(diff) > 2.0) {
                v.currentTime = targetTime;
                v.playbackRate = baseRate;
              }
              else if (Math.abs(diff) > 0.05) {
                v.playbackRate = diff > 0 ? baseRate * 0.97 : baseRate * 1.03;
              }
              else {
                v.playbackRate = baseRate;
              }
            } else {
              // 시작 시간 전이거나 영상 범위 밖 — 정지 및 위치 초기화
              if (!v.paused) v.pause();
              v.currentTime = 0;
            }
          });
          animationRef.current = requestAnimationFrame(updateAllSync);
        } else {
          animationRef.current = null; // 루프 자연 종료 (버퍼링 등)
        }
      };

      animationRef.current = requestAnimationFrame(updateAllSync);

      const masterVideo = videoRefs.current[selectedSourceCam];

      // 버퍼링 해소 시 루프 재시작
      const handleCanPlay = () => {
        if (animationRef.current === null) {
          animationRef.current = requestAnimationFrame(updateAllSync);
        }
      };

      // 마스터 버퍼링 시작 → 슬레이브 같이 정지 (drift 방지)
      const handleWaiting = () => {
        multiviewCameras.forEach(cam => {
          if (!cam || cam.id === selectedSourceCam) return;
          const v = videoRefs.current[cam.id];
          if (v && !v.paused) v.pause();
        });
      };

      // 마스터 버퍼링 해소 → 슬레이브 같이 재개 (seek 불필요)
      const handlePlaying = () => {
        multiviewCameras.forEach(cam => {
          if (!cam || cam.id === selectedSourceCam) return;
          const v = videoRefs.current[cam.id];
          if (v && v.paused) v.play().catch(() => {});
        });
      };

      masterVideo?.addEventListener('canplay', handleCanPlay);
      masterVideo?.addEventListener('waiting', handleWaiting);
      masterVideo?.addEventListener('playing', handlePlaying);

      return () => {
        cancelAnimationFrame(animationRef.current);
        animationRef.current = null;
        masterVideo?.removeEventListener('canplay', handleCanPlay);
        masterVideo?.removeEventListener('waiting', handleWaiting);
        masterVideo?.removeEventListener('playing', handlePlaying);
      };
    }

    return () => {
      cancelAnimationFrame(animationRef.current);
      animationRef.current = null;
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
    const f = Math.floor((seconds % 1) * fps); // 선택한 fps 기준 프레임
    return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}:${f.toString().padStart(2, '0')}`;
  };

  // 멀티뷰 소스에서 비디오 선택 시 프로그램 모니터의 비디오로 설정 및 초기화 함수
  const handleSourceCamClick = (camId) => {
    if (selectedSourceCam === camId) return;

    // state(currentTime)는 rAF 루프에 의해 최대 16ms 지연될 수 있어
    // 현재 마스터 비디오에서 실제 시간을 직접 읽어 정확한 masterTime 계산
    const masterVideo = videoRefs.current[selectedSourceCam];
    const oldCam = cameras.find(c => c.id === selectedSourceCam);
    let actualMasterTime = currentTime;
    if (masterVideo && oldCam) {
      const oldOffset = (Number(oldCam.start_time) - Number(minAbsStart)) / 1000;
      actualMasterTime = masterVideo.currentTime + oldOffset;
    }

    const newCam = cameras.find(c => c.id === camId);
    if (newCam) {
      const newOffset = (Number(newCam.start_time) - Number(minAbsStart)) / 1000;
      const target = Math.max(0, actualMasterTime - newOffset);

      // 프로그램 모니터는 src가 바뀌면 브라우저가 currentTime을 0으로 리셋함
      // → onCanPlay에서 seek할 시간을 ref에 저장해두고 로드 완료 후 적용
      pendingProgramSeek.current = target;

      // 멀티뷰 비디오 전체 싱크
      multiviewCameras.forEach(cam => {
        if (!cam) return;
        const v = videoRefs.current[cam.id];
        if (!v) return;
        const camOffset = (Number(cam.start_time) - Number(minAbsStart)) / 1000;
        v.currentTime = Math.max(0, actualMasterTime - camOffset);
      });

      // 새 마스터 비디오 싱크 (멀티뷰에 포함되어 위에서 처리되지만 명시적으로 보장)
      const newMasterVideo = videoRefs.current[camId];
      if (newMasterVideo) {
        newMasterVideo.currentTime = target;
        if (isPlaying) newMasterVideo.play().catch(() => {});
      }

      setCurrentTime(actualMasterTime);
    }

    setSelectedSourceCam(camId);
    setInPoint(null);
    setOutPoint(null);
  };

  // 비디오 리소스에서 멀티뷰 소스로 비디오 선택 시 multiCamers 배열로 비디오를 추가하는 함수
  const addCameraToMultiview = (camera) => {
    const alreadyExists = multiviewCameras.some(cam => cam?.id === camera.id);  //2026.06.15 리소스 중복 추가 차단
    if (alreadyExists) return;

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

    const rect = timelineRef.current.getBoundingClientRect();
    const x = Math.max(0, Math.min(e.clientX - rect.left, rect.width));
    const percent = x / rect.width;

    // 기준을 전체 세션 시간으로 통일
    let newTime = Math.max(0, Math.min(percent * totalSessionDuration, totalSessionDuration));

    if (isDraggingIn) {
      if (outPoint !== null && newTime >= outPoint) newTime = outPoint - 0.1;

      // [수정] 이미 생성된 클립의 'global_out'보다 앞으로 갈 수 없게 제한
      // 현재 드래그하는 시간보다 앞에 있는 클립 중 가장 마지막(가장 큰) out 값을 찾음
      const previousClips = savedClips.filter(clip => clip.global_out <= (outPoint || totalSessionDuration));
      if (previousClips.length > 0) {
        const latestOut = Math.max(...previousClips.map(c => c.global_out));
        if (newTime < latestOut) newTime = latestOut;
      }

      setInPoint(newTime);
    }
    else if (isDraggingOut) {
      if (inPoint !== null && newTime <= inPoint) newTime = inPoint + 0.1;

      // [수정] 뒤에 있는 클립의 'global_in'을 넘지 못하게 제한
      const futureClips = savedClips.filter(clip => clip.global_in >= inPoint);
      if (futureClips.length > 0) {
        const earliestIn = Math.min(...futureClips.map(c => c.global_in));
        if (newTime > earliestIn) newTime = earliestIn;
      }

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
    let t = currentTime;
    // 현재 위치가 기존 클립 내부에 있으면 해당 클립 끝점으로 스냅 (재생 중 부동소수점 오차로 인한 중복 방지)
    for (const clip of savedClips) {
      if (t >= clip.global_in && t < clip.global_out) {
        t = clip.global_out;
        break;
      }
    }
    setInPoint(t);
    if (outPoint !== null && t > outPoint) setOutPoint(null);
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
      if (inPoint >= clip.global_in && inPoint < clip.global_out) return true;
      if (outPoint > clip.global_in && outPoint <= clip.global_out) return true;
      if (inPoint <= clip.global_in && outPoint >= clip.global_out) return true;
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
    const sortedClips = [...savedClips, newClip].sort((a, b) => a.global_in - b.global_in); // 저장된 클립들을 시작 시점과 종료 시점으로 정렬 -> 사용자가 클립의 순서를 꼬아놔도 영상의 진행 순서대로 정렬됨
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

  // 하이라이트 마킹 버튼 클릭 시 현재 시점 ±3초 구간을 카메라별로 자동 생성
  const handleHighlightMark = () => {
    if (cameras.length === 0) {
      alert('카메라가 없습니다.');
      return;
    }
    const RADIUS = 3;
    const generated = cameras
      .filter(cam => cam.videoUrl)
      .map((cam, index) => {
        const camGlobalStart = (Number(cam.start_time) - Number(minAbsStart)) / 1000;
        const camGlobalEnd = (Number(cam.end_time) - Number(minAbsStart)) / 1000;
        const globalIn = Math.max(camGlobalStart, currentTime - RADIUS);
        const globalOut = Math.min(camGlobalEnd, currentTime + RADIUS);
        if (globalOut <= globalIn) return null;
        return {
          id: Date.now() + index,
          camId: cam.id,
          camName: cam.name,
          camColor: cam.color,
          video_url: cam.videoUrl,
          global_in: globalIn,
          global_out: globalOut,
          start_seek: Math.max(0, globalIn - camGlobalStart),
          end_seek: Math.max(0, globalOut - camGlobalStart),
          duration: globalOut - globalIn,
          included: true,
          slow_rate: 1.0,
          sequence: index + 1,
        };
      })
      .filter(Boolean);

    if (generated.length === 0) {
      alert('현재 시점에서 생성 가능한 하이라이트 클립이 없습니다.');
      return;
    }
    setHighlightClips(generated);
    setShowHighlightPanel(true);
  };

  // 하이라이트 패널에서 확인 클릭 시 savedClips에 추가
  const handleHighlightConfirm = (selectedClips) => {
    const newClips = selectedClips.map((clip, idx) => ({
      id: Date.now() + idx,
      sequence: 0,
      video_url: clip.video_url,
      cam: clip.camId,
      start_seek: clip.start_seek,
      end_seek: clip.end_seek,
      duration: clip.duration,
      global_in: clip.global_in,
      global_out: clip.global_out,
      slow_rate: clip.slow_rate,
      _insertOrder: idx,
    }));

    const combined = [...savedClips, ...newClips];
    const sorted = [...combined].sort((a, b) => {
      if (a.global_in !== b.global_in) return a.global_in - b.global_in;
      const aOrder = a._insertOrder ?? -1;
      const bOrder = b._insertOrder ?? -1;
      return aOrder - bOrder;
    });
    const updated = sorted.map(({ _insertOrder, ...rest }, idx) => ({
      ...rest,
      sequence: idx + 1,
    }));

    setSavedClips(updated);
    setShowHighlightPanel(false);
    setHighlightClips([]);
  };

  // 전체 클립 연속 재생 함수
  const handlePreviewPlay = () => {
    if (savedClips.length === 0) {
      alert('저장된 클립이 없습니다.');
      return;
    }
    const sorted = [...savedClips].sort((a, b) => a.sequence - b.sequence);
    const first = sorted[0];
    const isInMultiview = multiviewCameras.some(cam => cam && cam.id === first.cam);
    if (!isInMultiview) {
      alert(`클립 ①의 카메라가 멀티뷰에 없습니다.`);
      return;
    }

    if (isPlaying) {
      Object.values(videoRefs.current).forEach(v => { if (v) v.pause(); });
      if (programVideoRef.current) programVideoRef.current.pause();
      setIsPlaying(false);
    }

    isPreviewModeRef.current = true;
    previewClipIndexRef.current = 0;

    setSelectedSourceCam(first.cam);
    replayEndRef.current = first.global_out;

    const masterTime = first.global_in;
    setCurrentTime(masterTime);

    multiviewCameras.forEach(cam => {
      if (!cam) return;
      const v = videoRefs.current[cam.id];
      if (!v) return;
      const offset = (Number(cam.start_time) - Number(minAbsStart)) / 1000;
      v.currentTime = Math.max(0, masterTime - offset);
    });

    const firstCam = cameras.find(c => c.id === first.cam);
    if (firstCam && programVideoRef.current) {
      const offset = (Number(firstCam.start_time) - Number(minAbsStart)) / 1000;
      pendingProgramSeek.current = Math.max(0, masterTime - offset);
      programVideoRef.current.currentTime = Math.max(0, masterTime - offset);
    }

    // 클립별 슬로우 모션(slow_rate) 반영
    playbackRateRef.current = first.slow_rate || 1.0;
    const masterVideo = videoRefs.current[first.cam];
    if (masterVideo) {
      masterVideo.playbackRate = playbackRateRef.current;
      masterVideo.play().catch(() => {});
    }
    if (programVideoRef.current) {
      programVideoRef.current.playbackRate = playbackRateRef.current;
      programVideoRef.current.play().catch(() => {});
    }
    setIsPlaying(true);
  };

  // 클립 클릭 시 해당 구간 리플레이 함수
  const handleClipReplay = (clip) => {
    const isInMultiview = multiviewCameras.some(cam => cam && cam.id === clip.cam);
    if (!isInMultiview) {
      alert('해당 클립의 카메라가 멀티뷰에 없습니다.');
      return;
    }

    if (isPlaying) {
      Object.values(videoRefs.current).forEach(v => { if (v) v.pause(); });
      if (programVideoRef.current) programVideoRef.current.pause();
      setIsPlaying(false);
    }

    setSelectedSourceCam(clip.cam);
    replayEndRef.current = clip.global_out;

    const masterTime = clip.global_in;
    setCurrentTime(masterTime);

    multiviewCameras.forEach(cam => {
      if (!cam) return;
      const v = videoRefs.current[cam.id];
      if (!v) return;
      const offset = (Number(cam.start_time) - Number(minAbsStart)) / 1000;
      v.currentTime = Math.max(0, masterTime - offset);
    });

    const clipCam = cameras.find(c => c.id === clip.cam);
    if (clipCam && programVideoRef.current) {
      const offset = (Number(clipCam.start_time) - Number(minAbsStart)) / 1000;
      pendingProgramSeek.current = Math.max(0, masterTime - offset);
      programVideoRef.current.currentTime = Math.max(0, masterTime - offset);
    }

    // 클립별 슬로우 모션(slow_rate) 반영
    playbackRateRef.current = clip.slow_rate || 1.0;
    const masterVideo = videoRefs.current[clip.cam];
    if (masterVideo) {
      masterVideo.playbackRate = playbackRateRef.current;
      masterVideo.play().catch(() => {});
    }
    if (programVideoRef.current) {
      programVideoRef.current.playbackRate = playbackRateRef.current;
      programVideoRef.current.play().catch(() => {});
    }
    setIsPlaying(true);
  };


  const handleVideoLoaded = (camId, e) => {
    const videoDuration = e.target.duration;
    if (videoDuration && !isNaN(videoDuration)) setDuration(videoDuration);
  };

  const totalClipDuration = savedClips.reduce((sum, clip) => sum + clip.duration, 0);

  // 이어붙이기 ON 시 각 클립의 표시 위치를 갭 없이 순서대로 계산
  const compactPositions = React.useMemo(() => {
    if (!compactTimeline || savedClips.length === 0) return {};
    let cursor = 0;
    const positions = {};
    [...savedClips]
      .sort((a, b) => a.sequence - b.sequence)
      .forEach(clip => {
        positions[clip.id] = { left: cursor, width: clip.duration };
        cursor += clip.duration;
      });
    return positions;
  }, [compactTimeline, savedClips]);

  // 영상 생성 - ExportPage로 이동하여 렌더링 진행
  const handleSavedClips = () => {
    if (savedClips.length === 0) {
      alert("저장된 클립이 없습니다. 먼저 클립을 생성해 주세요.");
      return;
    }
    const clipArrays = savedClips.map(clip => ({
      id: clip.id,
      sequence: clip.sequence,
      cam: clip.cam,
      video_url: clip.video_url,
      start_seek: clip.start_seek,
      end_seek: clip.end_seek,
      duration: clip.duration,
      global_in: clip.global_in,
      global_out: clip.global_out,
      slow_rate: clip.slow_rate || 1.0,
    }));
    navigate('/export', { state: { clips: clipArrays, sessionId: activeSessionId } });
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
      {showHighlightPanel && (
        <HighlightPanel
          highlightClips={highlightClips}
          onConfirm={handleHighlightConfirm}
          onClose={() => setShowHighlightPanel(false)}
        />
      )}

      <EditHeader
        savedClips={savedClips}
        totalClipDuration={totalClipDuration}
        formatTime={formatTime}
        onSave={handleSavedClips}
        onLogoClick={() => navigate('/')}
        onPreviewPlay={handlePreviewPlay}
      />

      <div className="main-content">
        <CameraResourceList
          sessionList={sessionList}
          activeSessionId={activeSessionId}
          onSessionChange={setActiveSessionId}
          cameras={cameras}
          multiviewCameras={multiviewCameras}
          onCameraClick={addCameraToMultiview}
          getProxyUrl={getProxyUrl}
        />

        <div className="workspace-area">
          <div className="monitor-section">
            <MultiviewGrid
              multiviewCameras={multiviewCameras}
              selectedSourceCam={selectedSourceCam}
              currentTime={currentTime}
              minAbsStart={minAbsStart}
              videoRefs={videoRefs}
              onSlotClick={handleMultiviewSlotClick}
              onRemoveCamera={removeCameraFromMultiview}
              onVideoLoaded={handleVideoLoaded}
              getProxyUrl={getProxyUrl}
            />
            <ProgramMonitor
              selectedSourceCam={selectedSourceCam}
              cameras={cameras}
              programVideoRef={programVideoRef}
              pendingProgramSeek={pendingProgramSeek}
              isPlaying={isPlaying}
              currentTime={currentTime}
              inPoint={inPoint}
              outPoint={outPoint}
              formatTime={formatTime}
              getProxyUrl={getProxyUrl}
            />
          </div>

          <div className="timeline-container">
            <ControlBar
              selectedSourceCam={selectedSourceCam}
              isPlaying={isPlaying}
              currentTime={currentTime}
              totalSessionDuration={totalSessionDuration}
              skipFrames={skipFrames}
              fps={fps}
              playbackRate={playbackRate}
              compactTimeline={compactTimeline}
              videoRefs={videoRefs}
              programVideoRef={programVideoRef}
              settingsPanelRef={settingsPanelRef}
              showSettings={showSettings}
              inPoint={inPoint}
              outPoint={outPoint}
              onSetCurrentTime={setCurrentTime}
              onTogglePlay={togglePlay}
              onSetIn={setIn}
              onSetOut={setOut}
              onAddClip={addClip}
              onHighlightMark={handleHighlightMark}
              onSetShowSettings={setShowSettings}
              onSetPlaybackRate={setPlaybackRate}
              onSetFps={setFps}
              onSetSkipFrames={setSkipFrames}
              onToggleCompact={() => setCompactTimeline(v => !v)}
            />
            <SourceTimeline
              selectedSourceCam={selectedSourceCam}
              cameras={cameras}
              duration={duration}
              savedClips={savedClips}
              totalSessionDuration={totalSessionDuration}
              inPoint={inPoint}
              outPoint={outPoint}
              currentTime={currentTime}
              timelineRef={timelineRef}
              onTimelineClick={handleTimelineClick}
              onMarkerMouseDown={handleMarkerMouseDown}
              formatTime={formatTime}
            />
            <EditTimeline
              savedClips={savedClips}
              cameras={cameras}
              compactTimeline={compactTimeline}
              compactPositions={compactPositions}
              totalSessionDuration={totalSessionDuration}
              onRemoveClip={removeClip}
              onClipReplay={handleClipReplay}
            />
          </div>
        </div>
      </div>
    </div>
  );
};

export default EditPage;