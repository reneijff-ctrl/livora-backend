import React, { ReactNode } from 'react';

interface LiveLayoutProps {
  video: ReactNode;
  chat: ReactNode;
}

const LiveLayout: React.FC<LiveLayoutProps> = ({ video, chat }) => {
  return (
    <div className="live-layout text-white">
      <div className="live-video">
        {video}
      </div>
      <div className="live-chat">
        {chat}
      </div>
    </div>
  );
};

export default LiveLayout;
