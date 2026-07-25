import { useState } from 'react';
import ClipEditModal from './ClipEditModal';

const getTextColor = (hexColor) => {
  if (!hexColor) return '#ffffff';
  const r = parseInt(hexColor.slice(1, 3), 16);
  const g = parseInt(hexColor.slice(3, 5), 16);
  const b = parseInt(hexColor.slice(5, 7), 16);
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return luminance > 0.5 ? '#1a1a1a' : '#ffffff';
};

// 겹치는 클립(다른 카메라, 같은 시간대)끼리 겹치지 않는 lane 번호를 그리디하게 배정
// (compact 모드는 클립들이 순서대로 이어붙어 겹치지 않으므로 non-compact 모드에서만 사용)
function assignLanes(savedClips) {
  const laneEnds = [];
  const laneOf = {};
  [...savedClips]
    .sort((a, b) => a.global_in - b.global_in)
    .forEach(clip => {
      let laneIndex = laneEnds.findIndex(end => end <= clip.global_in);
      if (laneIndex === -1) {
        laneIndex = laneEnds.length;
        laneEnds.push(clip.global_out);
      } else {
        laneEnds[laneIndex] = clip.global_out;
      }
      laneOf[clip.id] = laneIndex;
    });
  return { laneOf, laneCount: Math.max(1, laneEnds.length) };
}

// 클립 하나(재생/더보기/삭제 오버레이 포함)를 렌더링하는 공용 조각 - 전체 보기(lane)와 카메라별 보기(로컬 축) 둘 다 사용
function ClipBlock({ clip, leftPct, widthPct, topPct, heightPct, color, label, onRemoveClip, onClipReplay, onMoreClick }) {
  return (
    <div
      className="clip-wrapper"
      style={{ position: 'absolute', left: `${leftPct}%`, width: `${widthPct}%`, top: `${topPct}%`, height: `${heightPct}%` }}
    >
      <div
        className="clip-item-content"
        style={{
          backgroundColor: color,
          border: '2px solid rgba(255,255,255,0.3)',
          height: '100%', borderRadius: '4px', position: 'relative',
          cursor: 'pointer',
          display: 'flex', alignItems: 'center', justifyContent: 'center'
        }}
      >
        <span className="clip-seq-label" style={{ color: getTextColor(color) }}>
          {label}
          {clip.slow_rate && clip.slow_rate < 1 && (
            <span className="clip-slowmo-badge">
              {clip.slow_rate === 0.5 ? ' ½x' : ' ¼x'}
            </span>
          )}
        </span>
      </div>
      <div className="clip-hover-overlay">
        <button className="clip-icon-btn clip-icon-play" onClick={() => onClipReplay && onClipReplay(clip)}>▶</button>
        <button className="clip-icon-btn clip-icon-more" onClick={(e) => { e.stopPropagation(); onMoreClick(clip); }}>⋯</button>
        <button className="clip-icon-btn clip-icon-del" onClick={(e) => { e.stopPropagation(); onRemoveClip(clip.id); }}>×</button>
      </div>
    </div>
  );
}

function EditTimeline({ savedClips, cameras, compactTimeline, compactPositions, totalSessionDuration, totalClipDuration, minAbsStart, formatTime, onRemoveClip, onClipReplay, onUpdateClip }) {
  const [viewByCamera, setViewByCamera] = useState(false);
  const [editingClip, setEditingClip] = useState(null);
  const { laneOf, laneCount } = compactTimeline
    ? { laneOf: {}, laneCount: 1 }
    : assignLanes(savedClips);

  const camIds = viewByCamera ? [...new Set(savedClips.map(clip => clip.cam))] : [];

  return (
    <div style={{ flex: 1 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div className="edit-timeline-label">편집 타임라인 (저장된 클립)</div>
        {savedClips.length > 0 && (
          <button
            className="btn-base btn-secondary"
            style={{ fontSize: '12px', padding: '4px 10px' }}
            onClick={() => setViewByCamera(v => !v)}
          >
            {viewByCamera ? '전체 보기' : '카메라별로 보기'}
          </button>
        )}
      </div>

      {savedClips.length === 0 ? (
        <div className="clips-container">
          <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'black', fontSize: '16px' }}>
            저장된 클립이 없습니다. 소스에서 구간을 선택하고 "클립 추가"를 눌러주세요.
          </div>
        </div>
      ) : viewByCamera ? (
        <div className="saved-clips-panel">
          {camIds.map(camId => {
            const cam = cameras.find(c => c.id === camId);
            if (!cam) return null;
            const camOffset = (Number(cam.start_time) - Number(minAbsStart)) / 1000;
            const camDuration = (Number(cam.end_time) - Number(cam.start_time)) / 1000;
            const camClips = savedClips.filter(clip => clip.cam === camId);

            return (
              <div key={camId} className="saved-clips-row">
                <div className="saved-clips-row-label" style={{ backgroundColor: cam.color }}>
                  {cam.name}
                </div>
                <div className="timeline-track saved-clips-track">
                  <div className="track-bg" style={{ backgroundColor: '#1f2937' }} />
                  {camClips.map(clip => {
                    const localIn = clip.global_in - camOffset;
                    return (
                      <ClipBlock
                        key={clip.id}
                        clip={clip}
                        leftPct={(localIn / camDuration) * 100}
                        widthPct={((clip.global_out - clip.global_in) / camDuration) * 100}
                        topPct={0}
                        heightPct={100}
                        color={cam.color}
                        label={clip.sequence}
                        onRemoveClip={onRemoveClip}
                        onClipReplay={onClipReplay}
                        onMoreClick={setEditingClip}
                      />
                    );
                  })}
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div className="clips-container">
          <div className="clips-track">
            {savedClips.map((clip) => {
              const cp = compactTimeline ? compactPositions[clip.id] : null;
              const leftPos = cp
                ? (cp.left / totalClipDuration) * 100
                : (clip.global_in / totalSessionDuration) * 100;
              const widthSize = cp
                ? (cp.width / totalClipDuration) * 100
                : ((clip.global_out - clip.global_in) / totalSessionDuration) * 100;
              const clipColor = cameras.find(c => c.id === clip.cam)?.color;
              const laneHeight = 100 / laneCount;
              const topPos = compactTimeline ? 0 : laneOf[clip.id] * laneHeight;
              const heightSize = compactTimeline ? 100 : laneHeight;

              return (
                <ClipBlock
                  key={clip.id}
                  clip={clip}
                  leftPct={leftPos}
                  widthPct={widthSize}
                  topPct={topPos}
                  heightPct={heightSize}
                  color={clipColor}
                  label={`${clip.sequence} ${cameras.find(c => c.id === clip.cam)?.name || ''}`}
                  onRemoveClip={onRemoveClip}
                  onClipReplay={onClipReplay}
                  onMoreClick={setEditingClip}
                />
              );
            })}
          </div>
        </div>
      )}

      {editingClip && (
        <ClipEditModal
          clip={editingClip}
          cam={cameras.find(c => c.id === editingClip.cam)}
          formatTime={formatTime}
          onClose={() => setEditingClip(null)}
          onSave={onUpdateClip}
        />
      )}
    </div>
  );
}

export default EditTimeline;
