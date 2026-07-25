function SourceTimeline({ selectedSourceCam, cameras, duration, savedClips, totalSessionDuration, inPoint, outPoint, currentTime, timelineRef, onTimelineClick, onMarkerMouseDown, formatTime }) {
  if (selectedSourceCam === null) return null;

  const selectedCamName = cameras.find(c => c.id === selectedSourceCam)?.name;

  return (
    <div className="source-timeline-wrapper">
      <div style={{ fontSize: '12px', color: '#9ca3af', marginBottom: '4px' }}>
        소스 타임라인 - {selectedCamName}
      </div>
      <div className="time-ruler">
        {[...Array(13)].map((_, i) => (
          <span key={i}>{formatTime((totalSessionDuration / 12) * i).slice(0, 8)}</span>
        ))}
      </div>
      <div className="timeline-track" ref={timelineRef} onClick={onTimelineClick}>
        <div className="track-bg" style={{ backgroundColor: '#ffffff', border: '2px solid rgb(0,0,0)' }} />

        {savedClips.filter(clip => clip.cam === selectedSourceCam).map((clip) => (
          <div
            key={clip.id}
            className="clip-region"
            style={{
              left: `${(clip.global_in / totalSessionDuration) * 100}%`,
              width: `${((clip.global_out - clip.global_in) / totalSessionDuration) * 100}%`,
              backgroundColor: cameras.find(c => c.id === clip.cam)?.color,
              opacity: 0.6
            }}
          >
            {cameras.find(c => c.id === clip.cam)?.name}
          </div>
        ))}

        {inPoint !== null && outPoint !== null && (
          <div
            className="selection-region"
            style={{
              left: `${(inPoint / totalSessionDuration) * 100}%`,
              width: `${((outPoint - inPoint) / totalSessionDuration) * 100}%`,
            }}
          />
        )}

        {inPoint !== null && (
          <div
            className="marker"
            onMouseDown={(e) => onMarkerMouseDown('in', e)}
            style={{ left: `${(inPoint / totalSessionDuration) * 100}%`, backgroundColor: '#10b981' }}
          >
            <div className="marker-label" style={{ backgroundColor: '#10b981' }}>IN</div>
          </div>
        )}

        {outPoint !== null && (
          <div
            className="marker"
            onMouseDown={(e) => onMarkerMouseDown('out', e)}
            style={{ left: `${(outPoint / totalSessionDuration) * 100}%`, backgroundColor: '#ef4444' }}
          >
            <div className="marker-label" style={{ backgroundColor: '#ef4444' }}>OUT</div>
          </div>
        )}

        <div className="playhead" style={{ left: `${(currentTime / totalSessionDuration) * 100}%` }}>
          <div className="playhead-head" />
        </div>
      </div>
    </div>
  );
}

export default SourceTimeline;
