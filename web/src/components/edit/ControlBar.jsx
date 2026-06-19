import { Play, Pause, SkipBack, SkipForward, Plus, Settings, Star } from 'lucide-react';

function ControlBar({
  selectedSourceCam, isPlaying, currentTime, totalSessionDuration,
  skipFrames, fps, playbackRate, compactTimeline,
  videoRefs, programVideoRef, settingsPanelRef, showSettings,
  onSetCurrentTime, onTogglePlay, onSetIn, onSetOut, onAddClip,
  onSetShowSettings, onSetPlaybackRate, onSetFps, onSetSkipFrames, onToggleCompact,
  inPoint, outPoint, onHighlightMark,
}) {
  return (
    <div className="timeline-header">
      <h2 className="section-title" style={{ margin: 0 }}>타임라인</h2>
      <div className="timeline-controls">
        {/* 뒤로 이동 */}
        <button
          className="btn-icon"
          onClick={() => {
            const newTime = Math.max(0, currentTime - skipFrames / fps);
            onSetCurrentTime(newTime);
            Object.values(videoRefs.current).forEach(v => { if (v) v.currentTime = newTime; });
            if (programVideoRef.current) programVideoRef.current.currentTime = newTime;
          }}
          disabled={selectedSourceCam === null}
        >
          <SkipBack size={20} />
        </button>

        {/* 재생/일시정지 */}
        <button
          className="btn-icon"
          onClick={onTogglePlay}
          disabled={selectedSourceCam === null}
        >
          {isPlaying ? <Pause size={20} /> : <Play size={20} />}
        </button>

        {/* 앞으로 이동 */}
        <button
          className="btn-icon"
          onClick={() => {
            const newTime = Math.min(totalSessionDuration, currentTime + skipFrames / fps);
            onSetCurrentTime(newTime);
            Object.values(videoRefs.current).forEach(v => { if (v) v.currentTime = newTime; });
            if (programVideoRef.current) programVideoRef.current.currentTime = newTime;
          }}
          disabled={selectedSourceCam === null}
        >
          <SkipForward size={20} />
        </button>

        <div className="divider" />

        {/* In / Out / 클립 추가 */}
        <button className="btn-base btn-in" onClick={onSetIn} disabled={selectedSourceCam === null}>
          In 설정
        </button>
        <button className="btn-base btn-out" onClick={onSetOut} disabled={selectedSourceCam === null}>
          Out 설정
        </button>
        <button
          className="btn-base btn-add"
          onClick={onAddClip}
          disabled={selectedSourceCam === null || inPoint === null || outPoint === null}
        >
          <Plus size={16} />
          클립 추가
        </button>

        <button
          className="btn-base btn-highlight"
          onClick={onHighlightMark}
          disabled={selectedSourceCam === null}
          title="현재 시점 ±3초 하이라이트 자동 생성"
        >
          <Star size={16} />
          하이라이트 마킹
        </button>

        {/* 설정 드롭다운 */}
        <div ref={settingsPanelRef} style={{ position: 'relative' }}>
          <button
            className="btn-icon"
            onClick={() => onSetShowSettings(v => !v)}
            style={{ background: showSettings ? '#e5e7eb' : undefined }}
            title="편집 설정"
          >
            <Settings size={18} />
          </button>

          {showSettings && (
            <div style={{
              position: 'absolute', right: 0, top: 'calc(100% + 6px)', zIndex: 200,
              background: '#fff', border: '1px solid #d1d5db', borderRadius: '10px',
              boxShadow: '0 8px 24px rgba(0,0,0,0.13)', padding: '16px 18px', minWidth: '220px',
            }}>
              {/* 재생 속도 */}
              <div style={{ marginBottom: '14px' }}>
                <div style={{ fontSize: '12px', fontWeight: '600', color: '#6b7280', marginBottom: '6px' }}>재생 속도</div>
                <div style={{ display: 'flex', gap: '4px' }}>
                  {[0.25, 0.5, 1, 1.5, 2].map(r => (
                    <button
                      key={r}
                      onClick={() => onSetPlaybackRate(r)}
                      style={{
                        flex: 1, padding: '4px 0', fontSize: '12px', borderRadius: '6px', cursor: 'pointer',
                        border: '1px solid #d1d5db',
                        background: playbackRate === r ? '#3b82f6' : '#f9fafb',
                        color: playbackRate === r ? '#fff' : '#374151',
                        fontWeight: playbackRate === r ? '700' : '400',
                      }}
                    >
                      {r}x
                    </button>
                  ))}
                </div>
              </div>

              {/* FPS 설정 */}
              <div style={{ marginBottom: '14px' }}>
                <div style={{ fontSize: '12px', fontWeight: '600', color: '#6b7280', marginBottom: '6px' }}>FPS 설정</div>
                <div style={{ display: 'flex', gap: '4px' }}>
                  {[24, 30, 60].map(f => (
                    <button
                      key={f}
                      onClick={() => onSetFps(f)}
                      style={{
                        flex: 1, padding: '4px 0', fontSize: '12px', borderRadius: '6px', cursor: 'pointer',
                        border: '1px solid #d1d5db',
                        background: fps === f ? '#6366f1' : '#f9fafb',
                        color: fps === f ? '#fff' : '#374151',
                        fontWeight: fps === f ? '700' : '400',
                      }}
                    >
                      {f}fps
                    </button>
                  ))}
                </div>
              </div>

              {/* 프레임 이동 단위 */}
              <div style={{ marginBottom: '14px' }}>
                <div style={{ fontSize: '12px', fontWeight: '600', color: '#6b7280', marginBottom: '6px' }}>
                  이동 단위 <span style={{ fontWeight: '400', color: '#9ca3af' }}>
                    ({skipFrames}프레임 = {(skipFrames / fps).toFixed(3)}초)
                  </span>
                </div>
                <div style={{ display: 'flex', gap: '4px' }}>
                  {[1, 5, 10, 30].map(n => (
                    <button
                      key={n}
                      onClick={() => onSetSkipFrames(n)}
                      style={{
                        flex: 1, padding: '4px 0', fontSize: '12px', borderRadius: '6px', cursor: 'pointer',
                        border: '1px solid #d1d5db',
                        background: skipFrames === n ? '#10b981' : '#f9fafb',
                        color: skipFrames === n ? '#fff' : '#374151',
                        fontWeight: skipFrames === n ? '700' : '400',
                      }}
                    >
                      {n}f
                    </button>
                  ))}
                </div>
              </div>

              {/* 클립 이어붙이기 토글 */}
              <div>
                <div style={{ fontSize: '12px', fontWeight: '600', color: '#6b7280', marginBottom: '6px' }}>클립 편집</div>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span style={{ fontSize: '13px', color: '#374151' }}>이어붙이기</span>
                  <button
                    onClick={onToggleCompact}
                    style={{
                      width: '44px', height: '24px', borderRadius: '12px', border: 'none', cursor: 'pointer',
                      background: compactTimeline ? '#10b981' : '#d1d5db',
                      position: 'relative', transition: 'background 0.2s', flexShrink: 0,
                    }}
                  >
                    <div style={{
                      position: 'absolute', top: '2px',
                      left: compactTimeline ? '22px' : '2px',
                      width: '20px', height: '20px', borderRadius: '50%',
                      background: '#fff', transition: 'left 0.2s',
                      boxShadow: '0 1px 3px rgba(0,0,0,0.2)'
                    }} />
                  </button>
                </div>
                <div style={{ fontSize: '11px', color: '#9ca3af', marginTop: '4px' }}>
                  {compactTimeline ? 'ON: 클립을 갭 없이 이어붙여 표시 중' : 'OFF: 원본 위치로 표시 중'}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default ControlBar;
