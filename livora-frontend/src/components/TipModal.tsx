import React, { useState } from 'react';
import tokenService from '../api/tokenService';
import { showToast } from './Toast';
import { useAuth } from '../auth/useAuth';

interface TipModalProps {
  isOpen: boolean;
  onClose: () => void;
  creatorId: number;
  onSuccess?: (amount: number) => void;
}

const TipModal: React.FC<TipModalProps> = ({ isOpen, onClose, creatorId, onSuccess }) => {
  const [amount, setAmount] = useState<number | ''>(10);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { refreshTokenBalance } = useAuth();

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!amount || amount < 1) {
      showToast('Please enter an amount of at least 1 token', 'error');
      return;
    }

    setIsSubmitting(true);
    try {
      await tokenService.sendTipByCreatorId(creatorId, Number(amount));
      showToast('Tokens sent!', 'success');
      
      // Update local balance
      refreshTokenBalance().catch(err => console.error('Failed to refresh balance after tip:', err));

      if (onSuccess) onSuccess(Number(amount));
      setAmount(10);
      onClose();
    } catch (error: any) {
      showToast(error.response?.data?.error || 'Failed to send tokens', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
      <div className="w-full max-w-md bg-[#0f0f14] border border-white/10 rounded-2xl shadow-2xl overflow-hidden animate-in fade-in zoom-in duration-200">
        <div className="p-6 border-b border-white/5 flex justify-between items-center">
          <h2 className="text-xl font-bold text-white">Send Tokens</h2>
          <button 
            onClick={onClose}
            className="text-white/40 hover:text-white transition-colors"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        
        <form onSubmit={handleSubmit} className="p-6 space-y-6">
          <div className="space-y-2">
            <label className="text-sm font-medium text-white/60">Amount of tokens</label>
            <div className="relative">
              <input
                type="number"
                min="1"
                value={amount}
                onChange={(e) => setAmount(e.target.value === '' ? '' : Number(e.target.value))}
                className="premium-input pr-12"
                placeholder="Enter amount..."
                required
                autoFocus
              />
              <div className="absolute right-4 top-1/2 -translate-y-1/2 text-white/20 font-bold">
                TOKENS
              </div>
            </div>
          </div>

          <div className="flex gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-3 rounded-xl bg-white/5 text-white font-medium hover:bg-white/10 transition-colors"
              disabled={isSubmitting}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="flex-1 px-4 py-3 rounded-xl bg-gradient-to-r from-purple-600 to-pink-600 text-white font-bold shadow-lg shadow-purple-500/20 hover:scale-[1.02] active:scale-95 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
              disabled={isSubmitting || !amount || amount < 1}
            >
              {isSubmitting ? 'Sending...' : 'Confirm Tip'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default TipModal;
