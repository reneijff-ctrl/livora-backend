import React from 'react';

const SkeletonRow: React.FC = () => (
  <div style={{
    height: '20px',
    backgroundColor: '#e0e0e0',
    borderRadius: '4px',
    margin: '10px 0',
    width: '100%',
    animation: 'pulse 1.5s ease-in-out infinite'
  }} />
);

const Loader: React.FC<{ type?: 'full' | 'skeleton' }> = ({ type = 'full' }) => {
  if (type === 'skeleton') {
    return (
      <div style={{ padding: '2rem', maxWidth: '800px', margin: '0 auto' }}>
        <div style={{ height: '40px', width: '200px', backgroundColor: '#e0e0e0', borderRadius: '4px', marginBottom: '2rem', animation: 'pulse 1.5s ease-in-out infinite' }} />
        <SkeletonRow />
        <SkeletonRow />
        <SkeletonRow />
        <div style={{ height: '200px', width: '100%', backgroundColor: '#f0f0f0', borderRadius: '8px', marginTop: '2rem', animation: 'pulse 1.5s ease-in-out infinite' }} />
        <style>{`
          @keyframes pulse {
            0% { opacity: 0.6; }
            50% { opacity: 1; }
            100% { opacity: 0.6; }
          }
        `}</style>
      </div>
    );
  }

  return (
    <div style={{
      position: 'fixed',
      top: 0,
      left: 0,
      width: '100vw',
      height: '100vh',
      display: 'flex',
      flexDirection: 'column',
      justifyContent: 'center',
      alignItems: 'center',
      backgroundColor: '#f8f9fa',
      zIndex: 9999
    }}>
      <div style={{
        width: '50px',
        height: '50px',
        border: '5px solid #f3f3f3',
        borderTop: '5px solid #6772e5',
        borderRadius: '50%',
        animation: 'spin 1s linear infinite'
      }} />
      <p style={{ marginTop: '1rem', fontFamily: 'sans-serif', color: '#666' }}>Loading...</p>
      <style>{`
        @keyframes spin {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  );
};

export default Loader;
