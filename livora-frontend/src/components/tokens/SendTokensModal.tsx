import React from 'react';

interface SendTokensModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelect: (amount: number) => void;
}

const SendTokensModal: React.FC<SendTokensModalProps> = ({ isOpen, onClose, onSelect }) => {
  if (!isOpen) return null;

  const amounts = [50, 100, 250];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="bg-[#0F0F14] rounded-2xl shadow-[0_20px_60px_rgba(0,0,0,0.6)] border border-[#16161D] w-full max-w-[320px] p-6 text-center">
        <h2 className="text-xl font-bold mb-6 text-white">Send Tokens</h2>
        
        <div className="flex flex-col gap-3">
          {amounts.map((amount) => (
            <button
              key={amount}
              className="w-full py-3 rounded-xl bg-[#08080A] border border-[#16161D] hover:bg-white/5 text-zinc-100 font-semibold transition active:scale-95"
              onClick={() => onSelect(amount)}
            >
              {amount} Tokens
            </button>
          ))}
        </div>

        <button
          className="mt-6 w-full text-zinc-500 hover:text-white transition font-medium"
          onClick={onClose}
        >
          Close
        </button>
      </div>
    </div>
  );
};

export default SendTokensModal;
