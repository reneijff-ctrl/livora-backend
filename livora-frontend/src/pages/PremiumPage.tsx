import React from 'react';
import { useAuth } from '../auth/useAuth';
import { Link } from 'react-router-dom';
import SEO from '../components/SEO';
import GlobalChat from '../components/GlobalChat';

const PremiumPage: React.FC = () => {
  const { user } = useAuth();

  return (
    <div style={{ padding: '2rem' }}>
      <SEO title="Premium Content" canonical="/premium" />
      <h1>💎 Premium Page</h1>
      <p>Welcome, {user?.email}! This is exclusive content for Premium members.</p>
      
      <div style={{ marginTop: '2rem', maxWidth: '800px' }}>
        <h2>Premium Live Chat</h2>
        <GlobalChat />
      </div>

      <div style={{ marginTop: '2rem' }}>
        <Link to="/dashboard">Back to Viewer Hub</Link>
      </div>
    </div>
  );
};

export default PremiumPage;
