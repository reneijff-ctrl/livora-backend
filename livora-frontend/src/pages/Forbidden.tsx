import React from 'react';
import { useNavigate } from 'react-router-dom';

const Forbidden: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div style={{ 
      display: 'flex', 
      flexDirection: 'column', 
      alignItems: 'center', 
      justifyContent: 'center', 
      height: '100vh', 
      fontFamily: 'sans-serif',
      textAlign: 'center',
      padding: '0 20px',
      backgroundColor: '#08080A',
      color: '#F4F4F5'
    }}>
      <h1 style={{ fontSize: '3rem', marginBottom: '1rem', color: '#f87171' }}>Access denied</h1>
      <p style={{ fontSize: '1.2rem', marginBottom: '2rem', color: '#71717A' }}>
        You do not have the required permissions to view this page.
      </p>
      <button 
        onClick={() => navigate('/')}
        style={{ 
          padding: '12px 24px', 
          fontSize: '1rem', 
          backgroundColor: '#6366f1', 
          color: 'white', 
          border: 'none', 
          borderRadius: '12px', 
          cursor: 'pointer',
          fontWeight: 'bold',
          boxShadow: '0 4px 12px rgba(99, 102, 241, 0.3)'
        }}
      >
        Go back home
      </button>
    </div>
  );
};

export default Forbidden;
