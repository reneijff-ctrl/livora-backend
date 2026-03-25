import React, { useState, useEffect } from 'react';
import tokenService from '../api/tokenService';
import { SafeTokenPackage } from '../adapters/WalletAdapter';
import TokenBalance from './TokenBalance';
import { showToast } from './Toast';
import { useWallet } from '../hooks/useWallet';

const TokenStore: React.FC = () => {
  const [packages, setPackages] = useState<SafeTokenPackage[]>([]);
  const [loading, setLoading] = useState(true);
  const [isBuying, setIsBuying] = useState<string | null>(null);
  const { balance } = useWallet();

  useEffect(() => {
    const fetchPackages = async () => {
      try {
        const data = await tokenService.getPackages();
        setPackages(data);
      } catch (error) {
        console.error('Failed to fetch packages', error);
      } finally {
        setLoading(false);
      }
    };
    fetchPackages();
  }, []);

  const handleBuy = async (packageId: string) => {
    setIsBuying(packageId);
    try {
      const { redirectUrl } = await tokenService.createCheckoutSession(packageId);
      // Store current balance before redirecting so success page can detect the increase
      sessionStorage.setItem('livora_pre_purchase_balance', String(balance));
      window.location.href = redirectUrl;
    } catch (error: any) {
      console.error('Failed to start token checkout', error);
      const message = error?.response?.data?.message || 'Failed to start checkout. Please try again.';
      showToast(message, 'error');
      setIsBuying(null);
    }
  };

  if (loading) return (
    <div style={{ padding: '2rem', textAlign: 'center' }}>
      <div className="spinner" style={{ margin: '0 auto 1rem', width: '40px', height: '40px', border: '4px solid #f3f3f3', borderTop: '4px solid #6772e5', borderRadius: '50%', animation: 'spin 1s linear infinite' }} />
      <div>Loading token store...</div>
    </div>
  );

  if (packages.length === 0) return (
    <div className="bg-black/40 backdrop-blur-xl border border-white/5 rounded-3xl p-8 text-center">
      <p className="text-zinc-500">No token packages available at the moment. Please check back later.</p>
    </div>
  );

  return (
    <div className="bg-black/40 backdrop-blur-xl border border-white/5 rounded-3xl p-8 shadow-2xl shadow-black/40">
      <div className="flex justify-between items-center mb-8 flex-wrap gap-4">
        <h3 className="m-0 text-xl font-bold text-zinc-100">Token Store</h3>
        <TokenBalance />
      </div>
      
      <div className="grid grid-cols-[repeat(auto-fill,minmax(160px,1fr))] gap-5">
        {packages.map((pkg) => (
          <div key={pkg.id} className="bg-white/5 border border-white/5 p-6 rounded-2xl text-center flex flex-col gap-3 transition-all duration-200 hover:scale-[1.02]">
            <div className="text-xl font-extrabold text-zinc-100">{pkg.tokenAmount.toLocaleString()} Tokens</div>
            <div className="text-zinc-500 text-sm font-medium">{pkg.name}</div>
            <div className="text-lg font-extrabold text-purple-400 my-1">
              {pkg.price.toFixed(2)} {pkg.currency.toUpperCase()}
            </div>
            <button
              onClick={() => handleBuy(pkg.id)}
              disabled={isBuying !== null}
              className={`w-full py-3 rounded-xl font-extrabold transition-all shadow-lg ${
                isBuying === pkg.id 
                  ? 'bg-zinc-800 text-zinc-500 cursor-not-allowed' 
                  : 'bg-indigo-600 hover:bg-indigo-700 text-white shadow-indigo-600/20'
              }`}
            >
              {isBuying === pkg.id ? 'Processing...' : 'Buy Now'}
            </button>
          </div>
        ))}
      </div>
    </div>
  );
};

export default TokenStore;
