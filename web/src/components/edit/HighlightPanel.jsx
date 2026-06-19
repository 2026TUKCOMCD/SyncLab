import React, { useState } from 'react';
import { X, ChevronUp, ChevronDown } from 'lucide-react';

function HighlightPanel({ highlightClips, onConfirm, onClose }) {
  const [clips, setClips] = useState(() =>
    highlightClips.map((c, i) => ({ ...c, sequence: i + 1 }))
  );

  const toggleInclude = (id) => {
    setClips(prev => prev.map(c => c.id === id ? { ...c, included: !c.included } : c));
  };

  const setSlowRate = (id, rate) => {
    setClips(prev => prev.map(c => c.id === id ? { ...c, slow_rate: rate } : c));
  };

  const move = (index, dir) => {
    const next = [...clips];
    const swapIdx = index + dir;
    if (swapIdx < 0 || swapIdx >= next.length) return;
    [next[index], next[swapIdx]] = [next[swapIdx], next[index]];
    setClips(next.map((c, i) => ({ ...c, sequence: i + 1 })));
  };

  const handleConfirm = () => {
    const selected = clips.filter(c => c.included);
    if (selected.length === 0) {
      alert('최소 1개 이상의 카메라를 선택해주세요.');
      return;
    }
    onConfirm(selected);
  };

  const includedCount = clips.filter(c => c.included).length;

  const SLOW_OPTIONS = [
    { label: '1x', value: 1.0 },
    { label: '½x', value: 0.5 },
    { label: '¼x', value: 0.25 },
  ];

  return (
    <div className="highlight-panel-overlay">
      <div className="highlight-panel">
        <div className="highlight-panel-header">
          <div>
            <h3 className="highlight-panel-title">하이라이트 편집</h3>
            <p className="highlight-panel-subtitle">카메라 순서와 슬로우 모션을 설정하세요 (±3초 구간)</p>
          </div>
          <button className="highlight-panel-close" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <div className="highlight-clip-list">
          {clips.map((clip, index) => (
            <div
              key={clip.id}
              className={`highlight-clip-row${!clip.included ? ' highlight-clip-row--excluded' : ''}`}
            >
              <span className="highlight-seq">{clip.sequence}</span>

              <div className="highlight-cam-info">
                <div className="highlight-cam-color" style={{ background: clip.camColor }} />
                <span className="highlight-cam-name">{clip.camName}</span>
                <span className="highlight-cam-duration">{clip.duration.toFixed(1)}s</span>
              </div>

              <div className="highlight-slowmo-btns">
                {SLOW_OPTIONS.map(({ label, value }) => (
                  <button
                    key={value}
                    className={`highlight-slowmo-btn${clip.slow_rate === value ? ' highlight-slowmo-btn--active' : ''}`}
                    onClick={() => setSlowRate(clip.id, value)}
                    disabled={!clip.included}
                  >
                    {label}
                  </button>
                ))}
              </div>

              <div className="highlight-order-btns">
                <button
                  className="highlight-order-btn"
                  onClick={() => move(index, -1)}
                  disabled={index === 0}
                >
                  <ChevronUp size={14} />
                </button>
                <button
                  className="highlight-order-btn"
                  onClick={() => move(index, 1)}
                  disabled={index === clips.length - 1}
                >
                  <ChevronDown size={14} />
                </button>
              </div>

              <label className="highlight-include-toggle">
                <input
                  type="checkbox"
                  checked={clip.included}
                  onChange={() => toggleInclude(clip.id)}
                />
                <span>포함</span>
              </label>
            </div>
          ))}
        </div>

        <div className="highlight-panel-footer">
          <span className="highlight-panel-count">{includedCount}개 카메라 선택됨</span>
          <div className="highlight-panel-actions">
            <button className="btn-base btn-secondary" onClick={onClose}>취소</button>
            <button className="btn-base btn-primary" onClick={handleConfirm}>타임라인에 추가</button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default HighlightPanel;
