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
      padding: '0 20px'
    }}>
      <h1 style={{ fontSize: '3rem', marginBottom: '1rem', color: '#e53e3e' }}>Access denied</h1>
      <p style={{ fontSize: '1.2rem', marginBottom: '2rem', color: '#4a5568' }}>
        You do not have the required permissions to view this page.
      </p>
      <button 
        onClick={() => navigate('/')}
        style={{ 
          padding: '10px 20px', 
          fontSize: '1rem', 
          backgroundColor: '#3182ce', 
          color: 'white', 
          border: 'none', 
          borderRadius: '4px', 
          cursor: 'pointer' 
        }}
      >
        Go back home
      </button>
    </div>
  );
};

export default Forbidden;
