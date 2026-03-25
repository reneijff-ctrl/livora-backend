import React, { useState, useEffect } from 'react';
import { safeRender } from '@/utils/safeRender';

export interface PinnedMessage {
  username?: string;
  senderUsername?: string;
  viewer?: string;
  amount: number;
  message?: string;
  content?: string;
  timestamp: string;
}

interface PinnedMessageBannerProps {
  pinnedMessage: PinnedMessage | null;
}

/**
 * PinnedMessageBanner - Displays a high-value tip pinned at the top of the stream.
 * Features a gold neon border, auto-removal after 60 seconds, and no layout shift.
 */
const PinnedMessageBanner: React.FC<PinnedMessageBannerProps> = ({ pinnedMessage }) => {
  const [show, setShow] = useState(false);
  const [currentMessage, setCurrentMessage] = useState<PinnedMessage | null>(null);

  useEffect(() => {
    if (pinnedMessage) {
      console.debug("PINNED MESSAGE RECEIVED", pinnedMessage);
      setCurrentMessage(pinnedMessage);
      setShow(true);
      
      const timer = setTimeout(() => {
        setShow(false);
      }, 60000); // 60 seconds

      return () => clearTimeout(timer);
    }
  }, [pinnedMessage]);

  if (!show || !currentMessage) return null;
  
  const displayName = 
    currentMessage.username || 
    currentMessage.senderUsername || 
    (typeof currentMessage.viewer === 'string' ? currentMessage.viewer : currentMessage.viewer?.username) || 
    'Anonymous';
  const displayMessage = 
    (typeof currentMessage.message === 'string' ? currentMessage.message : null) || 
    (typeof currentMessage.content === 'string' ? currentMessage.content : null) || 
    '';

  return (
    <div className="pinned-banner-overlay">
      <div className="pinned-banner-content glass-panel border-gold-neon animate-banner-slide-down">
        <div className="flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="pinned-icon-container">
              <span className="text-xl">🏆</span>
            </div>
            <div className="flex flex-col">
              <span className="pinned-label">BIG TIP PINNED</span>
              <div className="flex items-center gap-2">
                <span className="pinned-username">{safeRender(displayName)}</span>
                <span className="pinned-amount">tipped 🪙 {safeRender(currentMessage.amount)}</span>
              </div>
            </div>
          </div>
          {displayMessage && (
            <div className="pinned-message-container hidden md:block">
              <p className="pinned-message-text italic opacity-90 truncate max-w-[200px]">
                "{safeRender(displayMessage)}"
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default PinnedMessageBanner;
