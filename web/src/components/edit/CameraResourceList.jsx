const ROLE_LABELS = { left: '왼쪽 골대', center: '센터', right: '오른쪽 골대' };

function CameraResourceList({
  sessionList, activeSessionId, onSessionChange, cameras, multiviewCameras, onCameraClick, getProxyUrl,
  cameraRoles = {}, onSetCameraRole,
}) {
  return (
    <div className="sidebar">
      {/* 세션 선택 영역 */}
      <div className="session-select-wrapper">
        <h2 className="section-title">📅 세션 관리</h2>
        <div className="session-info-box">
          <span className="session-label">현재 선택된 세션 ID</span>
          <div className="session-value">{activeSessionId || "세션을 선택하세요"}</div>
        </div>
        <select
          value={activeSessionId || ""}
          onChange={(e) => onSessionChange(e.target.value)}
          className="session-select-dropdown"
        >
          <option value="" disabled>목록에서 세션 변경</option>
          {sessionList?.map((session) => (
            <option key={session.session_session_id} value={session.session_session_id}>
              ID: {session.session_session_id}
            </option>
          ))}
        </select>
      </div>

      <h2 className="section-title">
        📁 리소스 보관함
        <span style={{ fontSize: '0.8em', marginLeft: '8px', color: '#666' }}>
          ({cameras.length})
        </span>
      </h2>

      <div className="resource-list">
        {cameras.length === 0 ? (
          <div style={{ padding: '20px', textAlign: 'center', color: '#999' }}>
            저장된 영상이 없습니다.
          </div>
        ) : (
          cameras.map((cam) => {
            const isSelected = multiviewCameras.some((c) => c?.id === cam.id);
            return (
              <div
                key={cam.id}
                className="resource-item"
                style={{
                  opacity: isSelected ? 0.5 : 1,
                  border: isSelected ? '2px solid #10b981' : '1px solid #eee',
                  cursor: 'pointer',
                  marginBottom: '10px',
                  borderRadius: '8px',
                  overflow: 'hidden',
                  backgroundColor: '#fff'
                }}
                onClick={() => onCameraClick(cam)}
              >
                <div className="video-thumb-wrapper" style={{ position: 'relative', width: '100%', height: '100px', backgroundColor: '#000' }}>
                  {cam.videoUrl ? (
                    <video
                      src={`${getProxyUrl(cam.videoUrl)}#t=0.1`}
                      className="video-thumb"
                      preload="metadata"
                      style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                      onMouseOver={e => e.target.play()}
                      onMouseOut={e => e.target.pause()}
                      muted
                    />
                  ) : (
                    <div className="video-placeholder" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: '#fff' }}>
                      🎥
                    </div>
                  )}
                  {isSelected && (
                    <div className="check-badge" style={{
                      position: 'absolute', top: '5px', right: '5px',
                      backgroundColor: '#10b981', color: 'white',
                      borderRadius: '50%', width: '20px', height: '20px',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: '12px'
                    }}>
                      ✓
                    </div>
                  )}
                </div>
                <div style={{ padding: '8px' }}>
                  <div style={{ fontWeight: 'bold', fontSize: '14px', marginBottom: '2px' }}>
                    {cam.name}
                  </div>
                  <div style={{ fontSize: '11px', color: '#666', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', marginBottom: '6px' }}>
                    {cam.file_name}
                  </div>
                  {onSetCameraRole && (
                    <select
                      className="camera-role-select"
                      value={cameraRoles[cam.id] || ''}
                      onClick={(e) => e.stopPropagation()}
                      onChange={(e) => onSetCameraRole(cam.id, e.target.value || null)}
                    >
                      <option value="">방향 미지정</option>
                      <option value="left">{ROLE_LABELS.left}</option>
                      <option value="center">{ROLE_LABELS.center}</option>
                      <option value="right">{ROLE_LABELS.right}</option>
                    </select>
                  )}
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}

export default CameraResourceList;
