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
                      border: `2px solid ${clipColor}`,
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      height: '100%', borderRadius: '4px', position: 'relative',
                      cursor: 'pointer'
                    }}
                    onClick={() => onClipReplay && onClipReplay(clip)}
                  >
                    <div className="clip-info">
                      <div className="clip-info-text-main" style={{ fontSize: '12px', fontWeight: 'bold' }}>
                        {cameras.find(c => c.id === clip.cam)?.name}
                      </div>
                    </div>
                    <button className="btn-clip-delete" onClick={(e) => { e.stopPropagation(); onRemoveClip(clip.id); }}>×</button>
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
