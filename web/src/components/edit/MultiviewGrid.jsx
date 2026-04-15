function MultiviewGrid({ multiviewCameras, selectedSourceCam, currentTime, minAbsStart, videoRefs, onSlotClick, onRemoveCamera, onVideoLoaded, getProxyUrl }) {
  return (
    <div className="multiview-wrapper">
      <h2 className="section-title" style={{ fontSize: '20px', marginBottom: '8px' }}>멀티뷰 소스</h2>
      <div className="multiview-grid">
        {multiviewCameras.map((cam, slotIndex) => (
          <div
            key={slotIndex}
            className="grid-slot"
            onClick={() => onSlotClick(slotIndex)}
            style={{
              position: 'relative',
              cursor: cam ? 'pointer' : 'default',
              border: cam && selectedSourceCam === cam.id ? `4px solid ${cam.color}` : '2px solid #4b5563',
              boxShadow: cam && selectedSourceCam === cam.id ? `0 0 16px 2px ${cam.color}60` : 'none',
              backgroundColor: '#000',
              overflow: 'hidden',
              minHeight: '150px'
            }}
          >
            {cam ? (
              <>
                <video
                  ref={el => { if (el) videoRefs.current[cam.id] = el; }}
                  src={getProxyUrl(cam.videoUrl)}
                  style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                  muted
                  playsInline
                  onLoadedMetadata={(e) => onVideoLoaded(cam.id, e)}
                />
                {currentTime < (Number(cam.start_time) - Number(minAbsStart)) / 1000 && (
                  <div className="waiting-signal" style={{
                    position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column',
                    alignItems: 'center', justifyContent: 'center', background: 'rgba(0,0,0,1)',
                    color: 'white', zIndex: 10
                  }}>
                    <div className="spinner"></div>
                    <span style={{ fontWeight: 'bold' }}>신호 대기 중...</span>
                    <span style={{ fontSize: '11px', marginTop: '4px', opacity: 0.8 }}>
                      {Math.max(0, Math.ceil((cam.start_time - Number(minAbsStart || 0)) / 1000 - currentTime))}초 후 시작
                    </span>
                  </div>
                )}
                <div className="cam-label" style={{ backgroundColor: cam.color || '#333' }}>
                  {cam.name}
                </div>
                <button
                  className="btn-remove-cam"
                  onClick={(e) => {
                    e.stopPropagation();
                    onRemoveCamera(slotIndex);
                  }}
                >
                  ×
                </button>
              </>
            ) : (
              <div className="empty-slot">
                <div style={{ fontSize: '40px' }}>➕</div>
                <div style={{ fontSize: '14px' }}>왼쪽에서 카메라 선택</div>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

export default MultiviewGrid;
