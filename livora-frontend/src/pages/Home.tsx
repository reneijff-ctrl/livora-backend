import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import SEO from '../components/SEO';
import creatorService from '../api/creatorService';
import { ICreator } from '../domain/creator/ICreator';
import CreatorCard from '../components/creator/CreatorCard';

const Home = () => {
  const { isAuthenticated } = useAuth();
  const [creators, setCreators] = useState<ICreator[]>([]);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const creatorsData = await creatorService.getPublicCreatorsForHomepage();
        setCreators(creatorsData.content.slice(0, 8));
      } catch (err) {
        console.error('Failed to fetch homepage data:', err);
        setCreators([]);
      }
    };

    fetchData();
  }, []);

  return (
    <div className="bg-[#08080A] text-zinc-100 font-sans">
      <SEO 
        title="Premium Creator Platform" 
        description="Livora is the premium platform for discovery and supporting your favorite creators."
        canonical="/"
      />
      
      <section className="relative bg-[#08080A] py-20 text-center">

        {/* Ambient glow layer */}
        <div className="absolute inset-0 flex justify-center pointer-events-none">
          <div className="w-[1100px] h-[1100px] bg-indigo-600/15 blur-[220px] rounded-full" />
        </div>

        <div className="relative z-10 max-w-[1200px] mx-auto px-6 md:px-8">
          <img 
            src="/icoon_joinlivora.png"
            alt="JoinLivora Icon"
            className="mx-auto mb-10 w-[120px] drop-shadow-[0_20px_60px_rgba(0,0,0,0.6)]"
          />
      
          <h1 className="text-5xl md:text-7xl font-black tracking-tight text-white drop-shadow-[0_0_40px_rgba(255,255,255,0.08)]">
            Live. Direct. Connected.
          </h1>

          <p className="mt-8 text-lg text-white/60 max-w-2xl mx-auto leading-relaxed">
            A private live experience. Premium creators. Direct access.
          </p>

          <div className="mt-8 flex justify-center gap-4">

            <a
              href="/explore"
              className="px-6 py-3 rounded-xl bg-white text-black font-medium hover:bg-white/90 transition duration-200"
            >
              Explore Creators
            </a>

            <a
              href="/become-creator"
              className="px-6 py-3 rounded-xl border border-white/30 text-white hover:border-white hover:text-white transition duration-200"
            >
              Become a Creator
            </a>

          </div>
        </div>
      </section>

      <main className="relative z-10">
        {/* FEATURED CREATORS SECTION */}
        <section className="py-20">
          <div className="max-w-[1200px] mx-auto px-6 md:px-8">
            
            <h2 className="text-3xl md:text-5xl font-extrabold text-white tracking-tighter">
              Featured Creators
            </h2>

            <p className="mt-2 text-lg text-white/60 leading-relaxed">
              Discover the most active and trending talent on Livora.
            </p>

            <div className="marketplace-grid">
              {creators.length === 0 ? (
                <div className="col-span-full text-white/60">
                  No featured creators at the moment.
                </div>
              ) : (
                creators.map((creator) => (
                  <CreatorCard key={creator.id} creator={creator} />
                ))
              )}
            </div>
          </div>
        </section>
        
        {/* VALUE PROPS SECTION */}
        <section className="py-20">
          <div className="max-w-[1200px] mx-auto px-6 md:px-8">
            <div className="text-center mb-16">
              <h2 className="text-3xl md:text-5xl font-extrabold text-white mb-4 tracking-tighter">Why Choose Livora?</h2>
              <p className="text-lg text-white/60 max-w-2xl mx-auto leading-relaxed">
                Designed for those who value privacy, quality, and direct connection.
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
              <div className="bg-[#0F0F14] p-10 rounded-2xl border border-[#16161D] flex flex-col items-center text-center shadow-[0_20px_60px_rgba(0,0,0,0.6)] transition-all hover:scale-[1.02]">
                <div className="w-20 h-20 rounded-3xl bg-white/5 flex items-center justify-center mb-8 border border-[#16161D]">
                  <span className="text-4xl">💎</span>
                </div>
                <h3 className="text-2xl font-bold text-white mb-4">Support Directly</h3>
                <p className="text-lg text-white/60 leading-relaxed">
                  Your support goes directly to the creators you love. No middleman, no hidden fees, just pure appreciation for their work.
                </p>
              </div>
              
              <div className="bg-[#0F0F14] p-10 rounded-2xl border border-[#16161D] flex flex-col items-center text-center shadow-[0_20px_60px_rgba(0,0,0,0.6)] transition-all hover:scale-[1.02]">
                <div className="w-20 h-20 rounded-3xl bg-white/5 flex items-center justify-center mb-8 border border-[#16161D]">
                  <span className="text-4xl">🛡️</span>
                </div>
                <h3 className="text-2xl font-bold text-white mb-4">Secure Payments</h3>
                <p className="text-lg text-white/60 leading-relaxed">
                  Industry-leading security ensures your transactions are safe and private. Support with confidence every time.
                </p>
              </div>
              
              <div className="bg-[#0F0F14] p-10 rounded-2xl border border-[#16161D] flex flex-col items-center text-center shadow-[0_20px_60px_rgba(0,0,0,0.6)] transition-all hover:scale-[1.02]">
                <div className="w-20 h-20 rounded-3xl bg-white/5 flex items-center justify-center mb-8 border border-[#16161D]">
                  <span className="text-4xl">✨</span>
                </div>
                <h3 className="text-2xl font-bold text-white mb-4">Real Interaction</h3>
                <p className="text-lg text-white/60 leading-relaxed">
                  Go beyond simple following. Engage in real conversations, request custom content, and join a community that cares.
                </p>
              </div>
            </div>
          </div>
        </section>

        {!isAuthenticated && (
          <section className="py-20">
            <div className="max-w-[1200px] mx-auto px-6 md:px-8">
              <div className="bg-zinc-900 rounded-[40px] p-12 md:p-24 text-center relative overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-br from-purple-600/20 to-pink-600/20 opacity-40" />
                <div className="relative z-10 max-w-2xl mx-auto">
                  <h2 className="text-3xl md:text-5xl font-extrabold text-white mb-6 tracking-tighter">Ready to start?</h2>
                  <p className="text-lg text-white/60 mb-10 leading-relaxed">Join thousands of fans and creators already on the platform.</p>
                  <Link 
                    to="/register" 
                    className="inline-block px-10 py-5 bg-white text-black rounded-2xl text-lg font-bold hover:scale-[1.02] transition-transform active:scale-95 shadow-2xl"
                  >
                    Create Your Account Today
                  </Link>
                </div>
              </div>
            </div>
          </section>
        )}

        <div className="border-t border-white/10 mt-16 mb-24" />
      </main>
    </div>
  );
};

export default Home;
