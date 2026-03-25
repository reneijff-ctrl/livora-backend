import React, { useState } from 'react';
import privateShowService, { PrivateSession } from '../api/privateShowService';
import { useAuth } from '../auth/useAuth';
import { showToast } from './Toast';

interface PrivateShowRequestButtonProps {
  creatorId: number;
  pricePerMinute: number;
  onSessionCreated?: (session: PrivateSession) => void;
}

const PrivateShowRequestButton: React.FC<PrivateShowRequestButtonProps> = ({ creatorId, pricePerMinute, onSessionCreated }) => {
  const { tokenBalance } = useAuth();
  const [loading, setLoading] = useState(false);
  const [isSent, setIsSent] = useState(false);
  const [showTooltip, setShowTooltip] = useState(false);

  const handleRequest = async () => {
    if (loading || isSent) return;

    if (tokenBalance < pricePerMinute) {
      showToast('Insufficient tokens for this request', 'error');
      return;
    }

    setLoading(true);
    try {
      const session = await privateShowService.requestPrivateShow(creatorId, pricePerMinute);
      setIsSent(true);
      showToast('Private show requested!', 'success');
      if (onSessionCreated) {
        onSessionCreated(session);
      }
      setTimeout(() => setIsSent(false), 3000);
    } catch (error: any) {
      console.error('Failed to request private show', error);
      showToast(error.response?.data?.message || 'Failed to request private show', 'error');
    } finally {
      setLoading(false);
    }
  };

  const buttonLabel = loading
    ? 'Sending...'
    : isSent
      ? '✔ Requested'
      : '🔒 Private';

  const buttonClass = isSent
    ? 'px-3 py-1.5 bg-green-600 text-white rounded-full text-xs font-bold shadow-lg transition cursor-default'
    : 'px-3 py-1.5 bg-gradient-to-r from-purple-600 to-pink-500 text-white rounded-full text-xs font-bold shadow-lg hover:from-purple-500 hover:to-pink-400 transition disabled:opacity-50';

  return (
    <div
      className="relative inline-block"
      onMouseEnter={() => setShowTooltip(true)}
      onMouseLeave={() => setShowTooltip(false)}
    >
      {showTooltip && !loading && !isSent && (
        <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-3 py-1.5 bg-gray-900 text-white text-[11px] rounded-lg whitespace-nowrap shadow-xl pointer-events-none z-50">
          Request a private show — {pricePerMinute} tokens/min
          <div className="absolute top-full left-1/2 -translate-x-1/2 border-4 border-transparent border-t-gray-900" />
        </div>
      )}
      <button
        onClick={handleRequest}
        disabled={loading || isSent}
        className={buttonClass}
      >
        {buttonLabel}
      </button>
    </div>
  );
};

export default PrivateShowRequestButton;
