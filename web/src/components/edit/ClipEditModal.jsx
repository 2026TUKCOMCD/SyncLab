import { useState } from 'react';
import { X } from 'lucide-react';

const SLOW_OPTIONS = [
  { label: '1x', value: 1.0 },
  { label: '½x', value: 0.5 },
  { label: '¼x', value: 0.25 },
];

// 편집 타임라인에서 클립의 "더보기"를 눌렀을 때 뜨는 클립 상세/수정 모달
// 참고: Premiere/Final Cut 등 영상 편집기의 클립 속성 패널처럼 소스 구간(원본 영상 내 In/Out)과
// 타임라인 구간(세션 전역 In/Out)을 구분해서 보여줌
function ClipEditModal({ clip, cam, formatTime, onClose, onSave }) {
  const [slowRate, setSlowRate] = useState(clip.slow_rate ?? 1.0);

  const handleSave = () => {
    onSave(clip.id, { slow_rate: slowRate });
    onClose();
  };

  return (
    <div className="highlight-panel-overlay">
      <div className="highlight-panel" style={{ width: 420 }}>
        <div className="highlight-panel-header">
          <div>
            <h3 className="highlight-panel-title">클립 #{clip.sequence} 정보</h3>
            <p className="highlight-panel-subtitle">{cam?.name || '알 수 없는 카메라'}</p>
          </div>
          <button className="highlight-panel-close" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <div className="clip-edit-info">
          <div className="clip-edit-info-row">
            <span className="clip-edit-info-label">카메라</span>
            <span className="clip-edit-info-value">
              {cam && <span className="highlight-cam-color" style={{ background: cam.color }} />}
              {cam?.name || '-'}
            </span>
          </div>
          <div className="clip-edit-info-row">
            <span className="clip-edit-info-label">타임라인 구간</span>
            <span className="clip-edit-info-value">{formatTime(clip.global_in)} ~ {formatTime(clip.global_out)}</span>
          </div>
          <div className="clip-edit-info-row">
            <span className="clip-edit-info-label">원본 영상 구간</span>
            <span className="clip-edit-info-value">{formatTime(clip.start_seek)} ~ {formatTime(clip.end_seek)}</span>
          </div>
          <div className="clip-edit-info-row">
            <span className="clip-edit-info-label">길이</span>
            <span className="clip-edit-info-value">{clip.duration.toFixed(2)}초</span>
          </div>
        </div>

        <div className="clip-edit-speed">
          <span className="clip-edit-info-label">배속</span>
          <div className="highlight-slowmo-btns">
            {SLOW_OPTIONS.map(({ label, value }) => (
              <button
                key={value}
                className={`highlight-slowmo-btn${slowRate === value ? ' highlight-slowmo-btn--active' : ''}`}
                onClick={() => setSlowRate(value)}
              >
                {label}
              </button>
            ))}
          </div>
        </div>

        <div className="highlight-panel-footer">
          <span className="highlight-panel-count" />
          <div className="highlight-panel-actions">
            <button className="btn-base btn-secondary" onClick={onClose}>취소</button>
            <button className="btn-base btn-primary" onClick={handleSave}>저장</button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default ClipEditModal;
