import React, { useState, useEffect } from 'react';
import tokenService, { TokenPackage } from '../api/tokenService';
import { useAuth } from '../auth/useAuth';
import { showToast } from './Toast';

const TokenStore: React.FC = () => {
  const [packages, setPackages] = useState<TokenPackage[]>([]);
  const [loading, setLoading] = useState(true);
  const [isBuying, setIsBuying] = useState<string | null>(null);
  const { tokenBalance } = useAuth();

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
    } catch (error) {
      console.error('Failed to start token checkout', error);
      setIsBuying(null);
    }
  };

  if (loading) return <div>Loading token store...</div>;

  return (
    <div style={{ padding: '1.5rem', border: '1px solid #ddd', borderRadius: '12px', backgroundColor: '#fff', boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem', flexWrap: 'wrap', gap: '1rem' }}>
        <h3 style={{ margin: 0 }}>Token Store</h3>
        <div style={{ backgroundColor: '#f0f4f8', padding: '6px 16px', borderRadius: '20px', fontWeight: 'bold', minHeight: '32px', display: 'flex', alignItems: 'center' }}>
          Your Balance: {tokenBalance} 🪙
        </div>
      </div>
      
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))', gap: '1rem' }}>
        {packages.map((pkg) => (
          <div key={pkg.id} style={{ 
            border: '1px solid #eee', 
            padding: '1.25rem', 
            borderRadius: '12px', 
            textAlign: 'center',
            display: 'flex',
            flexDirection: 'column',
            gap: '0.5rem',
            transition: 'transform 0.2s',
            cursor: 'default'
          }}>
            <div style={{ fontSize: '1.25rem', fontWeight: 'bold' }}>{pkg.tokenAmount} Tokens</div>
            <div style={{ color: '#666', fontSize: '0.9rem' }}>{pkg.name}</div>
            <div style={{ fontSize: '1.1rem', fontWeight: 'bold', color: '#6772e5', margin: '0.25rem 0' }}>
              {pkg.price.toFixed(2)} {pkg.currency.toUpperCase()}
            </div>
            <button
              onClick={() => handleBuy(pkg.id)}
              disabled={isBuying !== null}
              style={{
                backgroundColor: '#6772e5',
                color: 'white',
                border: 'none',
                padding: '12px 8px',
                borderRadius: '8px',
                cursor: isBuying !== null ? 'not-allowed' : 'pointer',
                fontWeight: 'bold',
                marginTop: '0.5rem',
                minHeight: '44px', // Tap target
                opacity: isBuying !== null ? 0.7 : 1
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
