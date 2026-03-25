import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

const SupportCreatorPage: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { displayName } = (location.state as { displayName?: string }) || {};

  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-[#08080A] text-zinc-100 p-6">
      <div className="w-full max-w-[640px] text-center">
        <h1 className="text-3xl font-bold tracking-tight mb-2 text-white">
          Support this creator
        </h1>
        <p className="text-xl text-zinc-400 mb-8">
          {displayName || 'Creator'}
        </p>
        
        <div className="p-8 border border-[#16161D] rounded-2xl bg-[#0F0F14] mb-10 shadow-[0_20px_60px_rgba(0,0,0,0.6)]">
          <p className="text-zinc-500 font-medium">
            Token support will be available soon.
          </p>
        </div>

        <button
          onClick={() => navigate(-1)}
          className="text-sm font-semibold text-zinc-500 hover:text-white transition"
        >
          ← Back
        </button>
      </div>
    </div>
  );
};

export default SupportCreatorPage;
