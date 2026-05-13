const getTextColor = (hexColor) => {
  if (!hexColor) return '#ffffff';
  const r = parseInt(hexColor.slice(1, 3), 16);
  const g = parseInt(hexColor.slice(3, 5), 16);
  const b = parseInt(hexColor.slice(5, 7), 16);
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return luminance > 0.5 ? '#1a1a1a' : '#ffffff';
};

function EditTimeline({ savedClips, cameras, compactTimeline, compactPositions, totalSessionDuration, onRemoveClip, onClipReplay }) {
  return (
    <div style={{ flex: 1 }}>
      <div className="edit-timeline-label">편집 타임라인 (저장된 클립)</div>
      <div className="clips-container">
        {savedClips.length === 0 ? (
          <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'black', fontSize: '16px' }}>
            저장된 클립이 없습니다. 소스에서 구간을 선택하고 "클립 추가"를 눌러주세요.
          </div>
        ) : (
          <div className="clips-track">
            {savedClips.map((clip) => {
              const cp = compactTimeline ? compactPositions[clip.id] : null;
              const leftPos = cp
                ? (cp.left / totalSessionDuration) * 100
                : (clip.global_in / totalSessionDuration) * 100;
              const widthSize = cp
                ? (cp.width / totalSessionDuration) * 100
                : ((clip.global_out - clip.global_in) / totalSessionDuration) * 100;
              const clipColor = cameras.find(c => c.id === clip.cam)?.color;

              return (
                <div
                  key={clip.id}
                  className="clip-wrapper"
                  style={{ position: 'absolute', left: `${leftPos}%`, width: `${widthSize}%`, height: '100%' }}
                >
                  <div
                    className="clip-item-content"
                    style={{
                      backgroundColor: clipColor,
                      border: '2px solid rgba(255,255,255,0.3)',
                      height: '100%', borderRadius: '4px', position: 'relative',
                      cursor: 'pointer',
                      display: 'flex', alignItems: 'center', justifyContent: 'center'
                    }}
                  >
                    <span className="clip-seq-label" style={{ color: getTextColor(clipColor) }}>
                      {clip.sequence} {cameras.find(c => c.id === clip.cam)?.name}
                    </span>
                    <div className="clip-hover-overlay">
                      <button className="clip-icon-btn clip-icon-play" onClick={() => onClipReplay && onClipReplay(clip)}>▶</button>
                      <button className="clip-icon-btn clip-icon-del" onClick={(e) => { e.stopPropagation(); onRemoveClip(clip.id); }}>×</button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

export default EditTimeline;
