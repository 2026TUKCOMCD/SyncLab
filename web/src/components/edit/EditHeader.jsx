import { Save, Play, Square } from 'lucide-react';

function EditHeader({ savedClips, totalClipDuration, formatTime, onSave, onLogoClick, onPreviewPlay, isPreviewMode }) {
  return (
    <div className="header-container">
      <div className="header-left">
        <img src="/synclab_logo.png" alt="synclab_logo" className="logo-img" onClick={onLogoClick} />
      </div>
      <div className="header-right">
        <div className="header-info-text">
          총 클립: {savedClips.length}개 | 총 길이: {formatTime(totalClipDuration)}
        </div>
        <button
          className={`btn-base btn-preview${isPreviewMode ? ' btn-preview--active' : ''}`}
          onClick={onPreviewPlay}
          disabled={!isPreviewMode && savedClips.length === 0}
        >
          {isPreviewMode ? <Square size={18} /> : <Play size={18} />}
          {isPreviewMode ? '미리보기 중 (끄려면 클릭)' : '전체 미리보기'}
        </button>
        <button className="btn-base btn-primary" onClick={onSave}>
          <Save size={18} />
          프로젝트 저장
        </button>
      </div>
    </div>
  );
}

export default EditHeader;
