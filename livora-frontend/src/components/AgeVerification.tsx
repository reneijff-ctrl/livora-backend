import React, { useEffect, useState } from 'react';

const AgeVerification: React.FC = () => {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const isVerified = localStorage.getItem("age_verified");
    if (!isVerified) {
      setVisible(true);
      document.body.style.overflow = 'hidden';
    }
  }, []);

  const handleEnter = () => {
    localStorage.setItem("age_verified", "true");
    setVisible(false);
    document.body.style.overflow = 'auto';
  };

  const handleLeave = () => {
    window.location.href = "https://www.google.com";
  };

  if (!visible) return null;

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/95 backdrop-blur-md">
      <div className="max-w-md w-full bg-[#0F0F14] border border-[#16161D] rounded-2xl p-8 text-center shadow-[0_0_100px_rgba(99,102,241,0.2)] animate-in fade-in zoom-in duration-300">
        
        {/* Icon / Brand */}
        <div className="w-20 h-20 bg-indigo-600/10 rounded-full flex items-center justify-center mx-auto mb-6 ring-1 ring-indigo-500/20">
          <span className="text-3xl">🔞</span>
        </div>

        <h2 className="text-2xl font-bold text-white mb-4 uppercase tracking-tight">
          18+ Age Verification
        </h2>
        
        <p className="text-zinc-400 text-sm leading-relaxed mb-8">
          This platform contains adult-oriented content, including sexually explicit material. 
          By entering, you confirm you are at least 18 years of age and agree to our terms.
        </p>

        <div className="flex flex-col gap-4">
          <button
            onClick={handleEnter}
            className="w-full bg-gradient-to-r from-indigo-600 to-violet-600 hover:scale-[1.02] active:scale-[0.98] transition rounded-full py-4 text-base font-semibold text-white shadow-lg shadow-indigo-600/20"
          >
            Enter (I am 18+)
          </button>
          
          <button
            onClick={handleLeave}
            className="w-full border border-[#16161D] hover:bg-white/5 transition rounded-full py-4 text-base text-zinc-400"
          >
            Leave
          </button>
        </div>

        <div className="mt-8 flex justify-center gap-4 text-[9px] text-zinc-600 font-bold uppercase tracking-widest">
          <span>Secure</span>
          <span>•</span>
          <span>Discreet</span>
          <span>•</span>
          <span>Verified</span>
        </div>
      </div>
    </div>
  );
};

export default AgeVerification;
