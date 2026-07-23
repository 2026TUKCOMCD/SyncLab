import React, { useState, useEffect, useRef } from 'react';
import { X, Sparkles, Cpu, Zap } from 'lucide-react';

function formatShortTime(seconds) {
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, '0')}`;
}

// AI 하이라이트 자동 생성 결과 패널
// status: 'running' (분석 진행 중) | 'done' (결과 표시)
function AIHighlightPanel({
  status, progress, device, estimatedSeconds, error,
  highlights, cameras, cameraRoles = {}, onConfirm, onClose, onRetry,
}) {
  const useRoleMode = Object.values(cameraRoles).some(Boolean);
  const [elapsed, setElapsed] = useState(0);
  const timerRef = useRef(null);

  useEffect(() => {
    if (status === 'running') {
      timerRef.current = setInterval(() => setElapsed(prev => prev + 1), 1000);
      return () => clearInterval(timerRef.current);
    }
    clearInterval(timerRef.current);
  }, [status]);

  const [clips, setClips] = useState([]);
  const [selectedCamIds, setSelectedCamIds] = useState([]);

  useEffect(() => {
    if (status === 'done' && highlights) {
      setClips(highlights.map(h => ({ ...h, included: true })));
      setSelectedCamIds(cameras.filter(c => c.videoUrl).map(c => c.id));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, highlights]);

  const toggleClip = (id) => {
    setClips(prev => prev.map(c => c.id === id ? { ...c, included: !c.included } : c));
  };

  const toggleCam = (camId) => {
    setSelectedCamIds(prev =>
      prev.includes(camId) ? prev.filter(id => id !== camId) : [...prev, camId]
    );
  };

  const handleConfirm = () => {
    const selected = clips.filter(c => c.included);
    if (selected.length === 0) {
      alert('최소 1개 이상의 하이라이트를 선택해주세요.');
      return;
    }
    if (!useRoleMode && selectedCamIds.length === 0) {
      alert('최소 1개 이상의 카메라를 선택해주세요.');
      return;
    }
    onConfirm(selected, selectedCamIds);
  };

  const goalSideLabel = (side) => {
    if (side === 'left') return '왼쪽 골대';
    if (side === 'right') return '오른쪽 골대';
    return null;
  };

  return (
    <div className="highlight-panel-overlay">
      <div className="highlight-panel ai-highlight-panel">
        <div className="highlight-panel-header">
          <div>
            <h3 className="highlight-panel-title">
              <Sparkles size={16} style={{ verticalAlign: '-2px', marginRight: '6px' }} />
              AI 하이라이트 자동 생성
            </h3>
            <p className="highlight-panel-subtitle">
              {status === 'running' && '득점 장면을 분석하고 있습니다 (농구공 궤적 · 동작 패턴 인식)'}
              {status === 'done' && '감지된 득점 장면을 확인하고 타임라인에 추가하세요'}
              {status === 'error' && '분석 중 오류가 발생했습니다'}
            </p>
          </div>
          <button className="highlight-panel-close" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        {status === 'running' && (
          <div className="ai-highlight-progress-section">
            <div className="ai-highlight-device-badge">
              {device === 'cuda' ? <Zap size={13} /> : <Cpu size={13} />}
              {device === 'cuda' ? 'GPU (CUDA) 사용 중' : 'CPU 사용 중'}
            </div>
            <div className="export-progress-bar-wrapper">
              <div className="export-progress-bar-fill" style={{ width: `${progress}%` }} />
            </div>
            <div className="export-progress-text">{progress}%</div>
            <div className="ai-highlight-eta">
              경과 {formatShortTime(elapsed)}
              {estimatedSeconds ? ` / 예상 약 ${formatShortTime(estimatedSeconds)}` : ''}
            </div>
          </div>
        )}

        {status === 'error' && (
          <div className="ai-highlight-error-section">
            <p className="ai-highlight-error-msg">{error || '알 수 없는 오류가 발생했습니다.'}</p>
            {onRetry && (
              <button className="btn-base btn-primary" onClick={onRetry}>다시 시도</button>
            )}
          </div>
        )}

        {status === 'done' && (
          <>
            {clips.length === 0 ? (
              <div className="ai-highlight-empty">감지된 득점 장면이 없습니다. 민감도를 낮춰 다시 시도해보세요.</div>
            ) : (
              <div className="highlight-clip-list">
                {clips.map((clip) => {
                  const sideLabel = goalSideLabel(clip.goal_side);
                  return (
                    <div
                      key={clip.id}
                      className={`highlight-clip-row${!clip.included ? ' highlight-clip-row--excluded' : ''}`}
                    >
                      <span className="highlight-seq">{clip.index}</span>

                      <div className="highlight-cam-info">
                        <span className="highlight-cam-name">{formatShortTime(clip.timestamp)} 득점</span>
                        <span className="highlight-cam-duration">{clip.duration.toFixed(1)}s</span>
                        {sideLabel && <span className="ai-highlight-side-badge">{sideLabel}</span>}
                      </div>

                      <div className="ai-highlight-confidence">
                        <div className="ai-highlight-confidence-bar">
                          <div
                            className="ai-highlight-confidence-fill"
                            style={{ width: `${clip.confidence}%` }}
                          />
                        </div>
                        <span className="ai-highlight-confidence-text">{clip.confidence}%</span>
                      </div>

                      <label className="highlight-include-toggle">
                        <input
                          type="checkbox"
                          checked={clip.included}
                          onChange={() => toggleClip(clip.id)}
                        />
                        <span>포함</span>
                      </label>
                    </div>
                  );
                })}
              </div>
            )}

            {useRoleMode ? (
              <div className="ai-highlight-role-info">
                <Sparkles size={13} />
                카메라 역할이 설정되어 있어 득점마다 <strong>센터 카메라(빌드업)</strong> +{' '}
                <strong>득점 방향 골대 카메라(0.5x 리플레이)</strong> 클립이 자동으로 생성됩니다.
                (리소스 보관함에서 카메라 방향을 변경할 수 있습니다)
              </div>
            ) : (
              cameras.filter(c => c.videoUrl).length > 0 && (
                <div className="ai-highlight-cam-select">
                  <span className="ai-highlight-cam-select-label">적용할 카메라</span>
                  <div className="ai-highlight-cam-select-list">
                    {cameras.filter(c => c.videoUrl).map(cam => (
                      <label key={cam.id} className="ai-highlight-cam-chip">
                        <input
                          type="checkbox"
                          checked={selectedCamIds.includes(cam.id)}
                          onChange={() => toggleCam(cam.id)}
                        />
                        <span className="highlight-cam-color" style={{ background: cam.color }} />
                        {cam.name}
                      </label>
                    ))}
                  </div>
                </div>
              )
            )}
          </>
        )}

        <div className="highlight-panel-footer">
          <span className="highlight-panel-count">
            {status === 'done' ? `${clips.filter(c => c.included).length}개 하이라이트 선택됨` : ''}
          </span>
          <div className="highlight-panel-actions">
            <button className="btn-base btn-secondary" onClick={onClose}>
              {status === 'running' ? '백그라운드에서 계속' : '취소'}
            </button>
            {status === 'done' && (
              <button className="btn-base btn-primary" onClick={handleConfirm}>타임라인에 추가</button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default AIHighlightPanel;
