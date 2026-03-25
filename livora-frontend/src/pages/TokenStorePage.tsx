import React from 'react';
import TokenStore from '../components/TokenStore';
import SEO from '../components/SEO';

const TokenStorePage: React.FC = () => {
  return (
    <div style={{ padding: '2rem', maxWidth: '1200px', margin: '0 auto', color: '#F4F4F5' }}>
      <SEO title="Token Store" description="Purchase tokens to tip your favorite creators and unlock exclusive features." />
      <h1 style={{ marginBottom: '2rem', fontWeight: 800 }}>Get Tokens</h1>
      <TokenStore />
      
      <div style={{ marginTop: '3rem', backgroundColor: '#0F0F14', padding: '2.5rem', borderRadius: '24px', border: '1px solid rgba(255, 255, 255, 0.05)', boxShadow: '0 20px 60px rgba(0,0,0,0.6)' }}>
        <h3 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '1.5rem' }}>Why get tokens?</h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: '2rem' }}>
          <div>
            <h4 style={{ color: '#A855F7', marginBottom: '0.5rem' }}>Support Creators</h4>
            <p style={{ color: '#71717A', lineHeight: '1.6' }}>Send tips to your favorite streamers to show your appreciation and help them grow.</p>
          </div>
          <div>
            <h4 style={{ color: '#A855F7', marginBottom: '0.5rem' }}>Unlock Paid Chat</h4>
            <p style={{ color: '#71717A', lineHeight: '1.6' }}>Participate in exclusive PPV chat rooms that require a small token fee per message.</p>
          </div>
          <div>
            <h4 style={{ color: '#A855F7', marginBottom: '0.5rem' }}>Private Shows</h4>
            <p style={{ color: '#71717A', lineHeight: '1.6' }}>Request private one-on-one sessions with creators for a more personal experience.</p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default TokenStorePage;
