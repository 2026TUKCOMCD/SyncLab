import { useState } from 'react';

export default function MultiVideoPlayer() {
  const [videoList] = useState([ // 재생 가능한 동영상 목록으로 추후에는 사용자가 로그인 시 사용자가 속한 session ID를 통해 S3에서 url을 가져올 것
    { id: 1, title: '동영상 1', url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4' },
    { id: 2, title: '동영상 2', url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4' },
    { id: 3, title: '동영상 3', url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4' },
    { id: 4, title: '동영상 4', url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4' },
    { id: 5, title: '동영상 5', url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4' },
    { id: 6, title: '동영상 6', url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4' },
  ]);

  /**
   * 사용자가 선택한 동영상 목록(배열)으로 null로 초기화
   * selectedVideos는 변수, setSelectedVideos는 selectedVideos 변수를 변경할 때 사용하는 함수
   * useState는 Hook이라는 함수로 컴포넌트가 재생성될 때 데이터가 유지
   */ 
  const [selectedVideos, setSelectedVideos] 
  = useState([null, null, null, null]); 

  /**
   * 
   * 
   */
  const handleVideoSelect = (video, slotIndex) => { 
    const newSelected = [...selectedVideos];
    newSelected[slotIndex] = video;
    setSelectedVideos(newSelected);
  };

  const handleListItemClick = (video) => { // 좌측 목록에서 비디오 선택 시 비어있는 슬롯에 동영상 불러오기, 가득 차 있다면 0번 교체
    const emptySlot = selectedVideos.findIndex(slot => slot === null);
    if (emptySlot !== -1) {
      handleVideoSelect(video, emptySlot);
    } else {
      handleVideoSelect(video, 0);
    }
  };

  return (
    <div style={{ display: 'flex', height: '100vh', padding: '20px', gap: '20px' }}>
      {/* 좌측 동영상 목록 */}
      <div style={{ width: '250px', borderRight: '1px solid #ccc', paddingRight: '20px' }}>
        <h2>동영상 목록</h2>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {videoList.map((video) => (
            <div
              key={video.id}
              onClick={() => handleListItemClick(video)}
              style={{
                padding: '10px',
                border: '1px solid #ddd',
                borderRadius: '4px',
                cursor: 'pointer',
                backgroundColor: '#f9f9f9'
              }}
            >
              {video.title}
            </div>
          ))}
        </div>
      </div>

      {/* 중앙 및 우측 플레이어 영역 */}
      <div style={{ flex: 1, display: 'grid', gridTemplateColumns: '1fr 1fr', gridTemplateRows: '1fr 1fr', gap: '20px' }}>
        {[0, 1, 2, 3].map((index) => (
          <div key={index} style={{ border: '1px solid #ccc', borderRadius: '4px', padding: '10px' }}>
            <div style={{ marginBottom: '10px' }}>
              <select
                value={selectedVideos[index]?.id || ''}
                onChange={(e) => {
                  const video = videoList.find(v => v.id === parseInt(e.target.value));
                  handleVideoSelect(video || null, index);
                }}
                style={{ width: '100%', padding: '5px' }}
              >
                <option value="">동영상을 선택하세요</option>
                {videoList.map((video) => (
                  <option key={video.id} value={video.id}>
                    {video.title}
                  </option>
                ))}
              </select>
            </div>
            {selectedVideos[index] ? (
              <video
                controls
                style={{ width: '100%', height: 'calc(100% - 50px)', backgroundColor: '#000' }}
                src={selectedVideos[index].url}
              >
                동영상을 지원하지 않는 브라우저입니다.
              </video>
            ) : (
              <div style={{
                width: '100%',
                height: 'calc(100% - 50px)',
                backgroundColor: '#f0f0f0',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#999'
              }}>
                플레이어 {index + 1}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}