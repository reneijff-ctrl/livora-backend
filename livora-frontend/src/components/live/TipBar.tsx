import React, { useState } from 'react';
import { useWallet } from '@/wallet/WalletContext';
import apiClient from '@/api/apiClient';

interface TipBarProps {
  creatorId: number;
  roomId: number | string;
  onTip?: (amount: number) => void;
  onGiftClick?: () => void;
  minTip?: number;
}

const TipBar: React.FC<TipBarProps> = ({ roomId, onTip, onGiftClick, minTip = 1 }) => {
  const { balance } = useWallet();
  const [customAmount, setCustomAmount] = useState<string>('');
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const quickAmounts = [10, 25, 50, 100].filter(amt => amt >= minTip);

  const handleSendTip = async (amountToTip?: number) => {
    const tipAmount = amountToTip || parseInt(customAmount);

    if (!tipAmount || tipAmount < minTip) {
      if (tipAmount > 0 && tipAmount < minTip) {
        setError(`Minimum tip is ${minTip}`);
        setTimeout(() => setError(null), 3000);
      }
      return;
    }

    if (tipAmount > balance) {
      setError('Insufficient tokens');
      setTimeout(() => setError(null), 3000);
      return;
    }

    setIsSending(true);
    setError(null);

    try {
      // Body as required: roomId, amount, message, anonymous
      await apiClient.post('/tips/send', {
        roomId: roomId.toString(),
        amount: tipAmount,
        message: "",
        anonymous: false
      });
      
      // Success: Clear custom input
      setCustomAmount('');
      
      // UI update is driven by WebSocket monetization event — no optimistic update here
      // to avoid double-counting in leaderboard/top tipper.
    } catch (err: any) {
      console.error('TIPPING: Failed to send tip', err);
      setError(err.response?.data?.message || 'Tipping failed');
      setTimeout(() => setError(null), 3000);
    } finally {
      setIsSending(false);
    }
  };

  return (
    <div className="bg-[#0A0A0F]/95 backdrop-blur-sm border-t border-white/5 p-3 z-30">
      {/* Quick Tip Buttons + Gift */}
      <div className="flex items-center gap-2 mb-2">
        {quickAmounts.map((amt) => (
          <button
            key={amt}
            onClick={() => handleSendTip(amt)}
            disabled={isSending || amt > balance}
            className="flex-1 py-2 rounded-lg border border-white/5 bg-white/5 hover:bg-indigo-500/20 active:bg-indigo-600 text-white text-xs font-bold transition-all active:scale-95 disabled:opacity-30"
          >
            🪙 {amt}
          </button>
        ))}
        {onGiftClick && (
          <button
            onClick={onGiftClick}
            className="py-2 px-3 rounded-lg bg-indigo-500/20 border border-indigo-500/30 hover:bg-indigo-500/40 text-white text-xs font-bold transition-all active:scale-95"
          >
            🎁
          </button>
        )}
      </div>

      {/* Custom Amount & Send */}
      <div className="flex gap-2">
        <div className="relative flex-1">
          <input
            type="number"
            value={customAmount}
            onChange={(e) => setCustomAmount(e.target.value)}
            placeholder="Custom amount..."
            disabled={isSending}
            className="w-full bg-black/60 border border-white/5 rounded-lg px-3 py-2 text-sm text-white placeholder-zinc-600 focus:outline-none focus:ring-1 focus:ring-indigo-500/50 transition-all"
          />
          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-zinc-500 text-[10px] font-bold pointer-events-none">
            🪙
          </span>
        </div>
        
        <button
          onClick={() => handleSendTip()}
          disabled={isSending || !customAmount || parseInt(customAmount) > balance}
          className={`px-5 py-2 rounded-lg font-semibold text-sm transition-all duration-200 active:scale-95 flex items-center gap-1.5 ${
            isSending || !customAmount || parseInt(customAmount) > balance
              ? 'bg-zinc-800 text-zinc-500 cursor-not-allowed'
              : 'bg-gradient-to-r from-indigo-600 to-violet-600 text-white shadow-lg hover:shadow-[0_0_16px_rgba(99,102,241,0.3)]'
          }`}
        >
          {isSending ? (
            <span className="w-4 h-4 border-2 border-[#16161D] border-t-white rounded-full animate-spin" />
          ) : (
            <>💎 Send</>
          )}
        </button>
      </div>

      {error && (
        <p className="text-[10px] text-red-400 font-bold mt-1.5 text-center animate-pulse">{error}</p>
      )}
    </div>
  );
};

export default React.memo(TipBar);
