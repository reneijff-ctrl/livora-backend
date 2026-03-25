import React from 'react';
import { Link } from 'react-router-dom';
import Logo from '@/components/Logo';

const HomepageHero: React.FC = () => {
  return (
    <section className="relative w-full overflow-hidden bg-[#0B0B0F]">
      {/* Background Gradient Layer: Purple to Blue */}
      <div className="absolute inset-0 bg-gradient-to-br from-purple-900/30 via-[#0B0B0F] to-blue-900/20" aria-hidden="true" />
      
      {/* Decorative Blur Orbs for depth */}
      <div className="absolute -left-[10%] top-0 h-[500px] w-[500px] rounded-full bg-purple-600/10 blur-[120px]" aria-hidden="true" />
      <div className="absolute -right-[10%] bottom-0 h-[500px] w-[500px] rounded-full bg-blue-600/10 blur-[120px]" aria-hidden="true" />

      <div className="relative mx-auto max-w-7xl px-4 py-24 sm:py-32 lg:py-48">
        <div className="flex flex-col items-center text-center">
          {/* Brand */}
          <div className="mb-8">
            <Logo size={72} maxWidth="280px" />
          </div>

          <h1 className="mb-6 text-4xl font-extrabold leading-tight text-white sm:text-5xl md:text-7xl tracking-tight">
            Live. Direct.<br className="sm:hidden" /> Connected.
          </h1>

          <p className="mx-auto mb-10 max-w-2xl text-lg text-zinc-400 sm:text-xl font-medium leading-relaxed">
            Elevate your experience. Connect with elite talent directly. Secure, premium, and built for you.
          </p>

          <div className="flex flex-col sm:flex-row items-center justify-center gap-5">
            <Link
              to="/register"
              className="w-full sm:w-auto inline-flex items-center justify-center rounded-xl bg-gradient-to-r from-purple-600 to-blue-600 px-10 py-4 text-base font-bold text-white shadow-[0_8px_32px_rgba(147,51,234,0.3)] transition-all hover:scale-[1.02] hover:shadow-[0_12px_40px_rgba(147,51,234,0.45)] focus:outline-none focus:ring-2 focus:ring-purple-400/60"
            >
              Join as Creator
            </Link>
            <Link
              to="/explore"
              className="w-full sm:w-auto inline-flex items-center justify-center rounded-xl border border-zinc-800 bg-white/5 px-10 py-4 text-base font-bold text-white backdrop-blur-md transition-all hover:border-zinc-600 hover:bg-white/10 focus:outline-none focus:ring-2 focus:ring-zinc-400/60"
            >
              Explore Creators
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
};

export default HomepageHero;
