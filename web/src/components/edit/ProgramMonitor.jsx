function ProgramMonitor({ selectedSourceCam, cameras, programVideoRef, pendingProgramSeek, isPlaying, currentTime, inPoint, outPoint, formatTime, getProxyUrl }) {
  const selectedCam = cameras.find(c => c.id === selectedSourceCam);

  return (
    <div className="program-wrapper">
      <h2 className="section-title" style={{ fontSize: '20px', marginBottom: '8px' }}>프로그램 모니터</h2>
      <div className="program-screen">
        {selectedSourceCam === null ? (
          <div className="empty-slot" style={{ color: '#6b7280' }}>
            <div style={{ fontSize: '18px' }}>왼쪽에서 카메라를 선택하세요</div>
          </div>
        ) : (
          <>
            <video
              ref={programVideoRef}
              src={getProxyUrl(selectedCam?.videoUrl)}
              className="program-video"
              playsInline
              onCanPlay={() => {
                if (pendingProgramSeek.current !== null && programVideoRef.current) {
                  programVideoRef.current.currentTime = pendingProgramSeek.current;
                  pendingProgramSeek.current = null;
                }
                if (isPlaying && programVideoRef.current) {
                  programVideoRef.current.play().catch(() => {});
                }
              }}
            />
            <div className="cam-label" style={{ backgroundColor: selectedCam?.color, top: '16px', left: '16px', fontSize: '14px' }}>
              {selectedCam?.name}
            </div>
            <div className="timecode-overlay">
              {formatTime(currentTime)}
            </div>
            {(inPoint !== null || outPoint !== null) && (
              <div className="info-overlay">
                {inPoint !== null && <div style={{ color: '#34d399' }}>IN: {formatTime(inPoint)}</div>}
                {outPoint !== null && <div style={{ color: '#f87171' }}>OUT: {formatTime(outPoint)}</div>}
                {inPoint !== null && outPoint !== null && <div style={{ color: '#fbbf24' }}>길이: {formatTime(outPoint - inPoint)}</div>}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

export default ProgramMonitor;
