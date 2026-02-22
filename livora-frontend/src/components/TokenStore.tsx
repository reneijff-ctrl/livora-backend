import React, { useState, useEffect } from 'react';
import tokenService, { TokenPackage } from '../api/tokenService';
import TokenBalance from './TokenBalance';
import { showToast } from './Toast';

const TokenStore: React.FC = () => {
  const [packages, setPackages] = useState<TokenPackage[]>([]);
  const [loading, setLoading] = useState(true);
  const [isBuying, setIsBuying] = useState<string | null>(null);

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
    <div style={{ padding: '2rem', textAlign: 'center', backgroundColor: '#0F0F14', borderRadius: '16px', border: '1px solid rgba(255, 255, 255, 0.05)' }}>
      <p style={{ color: '#71717A' }}>No token packages available at the moment. Please check back later.</p>
    </div>
  );

  return (
    <div style={{ padding: '2rem', border: '1px solid rgba(255, 255, 255, 0.05)', borderRadius: '24px', backgroundColor: '#0F0F14', boxShadow: '0 20px 60px rgba(0,0,0,0.6)' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem', flexWrap: 'wrap', gap: '1rem' }}>
        <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 700 }}>Token Store</h3>
        <TokenBalance />
      </div>
      
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))', gap: '1.25rem' }}>
        {packages.map((pkg) => (
          <div key={pkg.id} style={{ 
            border: '1px solid rgba(255, 255, 255, 0.05)', 
            padding: '1.5rem', 
            borderRadius: '16px', 
            textAlign: 'center',
            display: 'flex',
            flexDirection: 'column',
            gap: '0.75rem',
            backgroundColor: '#08080A',
            transition: 'all 0.2s ease',
            cursor: 'default'
          }}>
            <div style={{ fontSize: '1.25rem', fontWeight: 800, color: '#F4F4F5' }}>{pkg.tokenAmount} Tokens</div>
            <div style={{ color: '#71717A', fontSize: '0.875rem', fontWeight: 500 }}>{pkg.name}</div>
            <div style={{ fontSize: '1.125rem', fontWeight: 800, color: '#A855F7', margin: '0.25rem 0' }}>
              {pkg.price.toFixed(2)} {pkg.currency.toUpperCase()}
            </div>
            <button
              onClick={() => handleBuy(pkg.id)}
              disabled={isBuying !== null}
              style={{
                backgroundColor: '#6366F1',
                color: 'white',
                border: 'none',
                padding: '12px 8px',
                borderRadius: '12px',
                cursor: isBuying !== null ? 'not-allowed' : 'pointer',
                fontWeight: 800,
                marginTop: '0.5rem',
                minHeight: '44px',
                opacity: isBuying !== null ? 0.6 : 1,
                boxShadow: '0 4px 12px rgba(99, 102, 241, 0.2)'
              }}
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
