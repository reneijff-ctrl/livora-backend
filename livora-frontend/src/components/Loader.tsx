import React from 'react';
import Logo from '@/components/Logo';

const SkeletonRow: React.FC = () => (
  <div style={{
    height: '20px',
    backgroundColor: '#27272A',
    borderRadius: '4px',
    margin: '10px 0',
    width: '100%',
    animation: 'pulse 1.5s ease-in-out infinite'
  }} />
);

const CardSkeleton: React.FC = () => (
  <div style={{
    backgroundColor: 'var(--card-bg)',
    borderRadius: '16px',
    border: '1px solid var(--border-color)',
    overflow: 'hidden',
    height: '360px',
    display: 'flex',
    flexDirection: 'column',
    boxShadow: '0 6px 24px rgba(0, 0, 0, 0.25)',
  }}>
    <div style={{ 
      height: '120px', 
      background: 'linear-gradient(135deg, rgba(168,85,247,0.35) 0%, rgba(236,72,153,0.35) 100%)', 
      animation: 'pulse 1.5s ease-in-out infinite',
      opacity: 0.5,
    }} />
    <div style={{ padding: '0 1.25rem 1.5rem 1.25rem', display: 'flex', flexDirection: 'column', alignItems: 'center', marginTop: '-40px', flex: 1 }}>
      <div style={{ 
        width: '80px', 
        height: '80px', 
        borderRadius: '50%', 
        backgroundColor: '#27272A', 
        border: '4px solid var(--card-bg)', 
        marginBottom: '0.75rem', 
        animation: 'pulse 1.5s ease-in-out infinite',
        boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)'
      }} />
      <div style={{ height: '20px', width: '60%', backgroundColor: '#3F3F46', borderRadius: '4px', marginBottom: '0.5rem', animation: 'pulse 1.5s ease-in-out infinite' }} />
      <div style={{ height: '14px', width: '40%', backgroundColor: '#27272A', borderRadius: '4px', marginBottom: '1rem', animation: 'pulse 1.5s ease-in-out infinite' }} />
      <div style={{ height: '14px', width: '90%', backgroundColor: '#27272A', borderRadius: '4px', marginBottom: '0.25rem', animation: 'pulse 1.5s ease-in-out infinite' }} />
      <div style={{ height: '14px', width: '80%', backgroundColor: '#27272A', borderRadius: '4px', marginBottom: '1.5rem', animation: 'pulse 1.5s ease-in-out infinite' }} />
      
      {/* Skeleton for View Profile button */}
      <div style={{ 
        marginTop: 'auto',
        height: '38px', 
        width: '100%', 
        backgroundColor: '#3F3F46', 
        borderRadius: '8px', 
        animation: 'pulse 1.5s ease-in-out infinite' 
      }} />
    </div>
  </div>
);

const FeedSkeleton: React.FC = () => (
  <div style={{
    backgroundColor: 'var(--card-bg)',
    padding: '1.5rem',
    borderRadius: '12px',
    border: '1px solid var(--border-color)',
    boxShadow: '0 6px 24px rgba(0, 0, 0, 0.25)',
    marginBottom: '1.5rem',
  }}>
    <div style={{ display: 'flex', gap: '1rem', marginBottom: '1rem' }}>
      <div style={{ width: '40px', height: '40px', borderRadius: '50%', backgroundColor: '#27272A', animation: 'pulse 1.5s ease-in-out infinite' }} />
      <div style={{ flex: 1 }}>
        <div style={{ height: '16px', width: '30%', backgroundColor: '#3F3F46', borderRadius: '4px', marginBottom: '0.5rem', animation: 'pulse 1.5s ease-in-out infinite' }} />
        <div style={{ height: '12px', width: '20%', backgroundColor: '#27272A', borderRadius: '4px', animation: 'pulse 1.5s ease-in-out infinite' }} />
      </div>
    </div>
    <div style={{ height: '14px', width: '100%', backgroundColor: '#27272A', borderRadius: '4px', marginBottom: '0.5rem', animation: 'pulse 1.5s ease-in-out infinite' }} />
    <div style={{ height: '14px', width: '100%', backgroundColor: '#27272A', borderRadius: '4px', marginBottom: '0.5rem', animation: 'pulse 1.5s ease-in-out infinite' }} />
    <div style={{ height: '14px', width: '60%', backgroundColor: '#27272A', borderRadius: '4px', animation: 'pulse 1.5s ease-in-out infinite' }} />
  </div>
);

const ProfileSkeleton: React.FC = () => (
  <div style={{ width: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
    <LogoLoader />
    <div style={{ 
      width: '100%', 
      height: '300px', 
      background: 'linear-gradient(135deg, rgba(168,85,247,0.15) 0%, rgba(236,72,153,0.15) 100%)',
      animation: 'pulse 1.5s ease-in-out infinite'
    }} />
    <div style={{ 
      maxWidth: '1100px', 
      margin: '-60px auto 0 auto', 
      padding: '0 1.5rem',
      position: 'relative',
      zIndex: 2
    }}>
      <div style={{
        backgroundColor: 'var(--card-bg)',
        borderRadius: '24px',
        padding: '2rem',
        boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.4)',
        border: '1px solid var(--border-color)',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center'
      }}>
        <div style={{
          width: '120px',
          height: '120px',
          borderRadius: '50%',
          backgroundColor: '#27272A',
          border: '4px solid var(--card-bg)',
          marginTop: '-80px',
          marginBottom: '1rem',
          animation: 'pulse 1.5s ease-in-out infinite'
        }} />
        <div style={{ height: '32px', width: '250px', backgroundColor: '#3F3F46', borderRadius: '8px', marginBottom: '1rem', animation: 'pulse 1.5s ease-in-out infinite' }} />
        <div style={{ height: '18px', width: '120px', backgroundColor: '#27272A', borderRadius: '4px', marginBottom: '1.5rem', animation: 'pulse 1.5s ease-in-out infinite' }} />
        <div style={{ display: 'flex', gap: '1rem', marginBottom: '2rem' }}>
          <div style={{ height: '40px', width: '100px', backgroundColor: '#27272A', borderRadius: '20px', animation: 'pulse 1.5s ease-in-out infinite' }} />
          <div style={{ height: '40px', width: '100px', backgroundColor: '#27272A', borderRadius: '20px', animation: 'pulse 1.5s ease-in-out infinite' }} />
        </div>
        <div style={{ height: '14px', width: '90%', backgroundColor: '#27272A', borderRadius: '4px', marginBottom: '0.5rem', animation: 'pulse 1.5s ease-in-out infinite' }} />
        <div style={{ height: '14px', width: '80%', backgroundColor: '#27272A', borderRadius: '4px', marginBottom: '2rem', animation: 'pulse 1.5s ease-in-out infinite' }} />
        
        <div style={{ width: '100%', borderTop: '1px solid var(--border-color)', paddingTop: '2rem', marginTop: '1rem' }}>
          <div style={{ height: '24px', width: '150px', backgroundColor: '#3F3F46', borderRadius: '4px', marginBottom: '1.5rem', animation: 'pulse 1.5s ease-in-out infinite' }} />
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '1.5rem' }}>
            {[...Array(3)].map((_, i) => (
              <div key={i} style={{ height: '200px', backgroundColor: 'var(--card-bg)', borderRadius: '16px', border: '1px solid var(--border-color)', animation: 'pulse 1.5s ease-in-out infinite' }} />
            ))}
          </div>
        </div>
      </div>
    </div>
  </div>
);

const LogoLoader: React.FC = () => (
  <div style={{
    position: 'absolute',
    top: '50%',
    left: '50%',
    transform: 'translate(-50%, -50%)',
    zIndex: 10,
    pointerEvents: 'none',
  }}>
    <Logo 
      size={80} 
      glow={false}
      style={{
        animation: 'calm-fade-pulse 2s ease-in-out infinite',
        filter: 'grayscale(1) brightness(1.5)',
      }}
    />
  </div>
);

const Loader: React.FC<{ type?: 'full' | 'skeleton' | 'grid' | 'feed' | 'profile' | 'logo' }> = ({ type = 'full' }) => {
  const pulseStyles = `
    @keyframes pulse {
      0% { opacity: 0.6; }
      50% { opacity: 1; }
      100% { opacity: 0.6; }
    }
    @keyframes calm-fade-pulse {
      0% { opacity: 0.1; transform: scale(0.98); }
      50% { opacity: 0.25; transform: scale(1); }
      100% { opacity: 0.1; transform: scale(0.98); }
    }
  `;

  if (type === 'logo') {
    return (
      <div style={{ padding: '4rem', display: 'flex', justifyContent: 'center', alignItems: 'center', width: '100%', minHeight: '200px', position: 'relative' }}>
        <style>{pulseStyles}</style>
        <LogoLoader />
      </div>
    );
  }

  if (type === 'skeleton') {
    return (
      <div style={{ padding: '2rem', maxWidth: '800px', margin: '0 auto', width: '100%' }}>
        <style>{pulseStyles}</style>
        <div style={{ height: '40px', width: '200px', backgroundColor: '#e0e0e0', borderRadius: '4px', marginBottom: '2rem', animation: 'pulse 1.5s ease-in-out infinite' }} />
        <SkeletonRow />
        <SkeletonRow />
        <SkeletonRow />
        <div style={{ height: '200px', width: '100%', backgroundColor: '#f0f0f0', borderRadius: '8px', marginTop: '2rem', animation: 'pulse 1.5s ease-in-out infinite' }} />
      </div>
    );
  }

  if (type === 'grid') {
    return (
      <div style={{ 
        display: 'grid', 
        gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', 
        gap: '2rem',
        width: '100%'
      }}>
        <style>{pulseStyles}</style>
        {[...Array(8)].map((_, i) => <CardSkeleton key={i} />)}
      </div>
    );
  }

  if (type === 'feed') {
    return (
      <div style={{ width: '100%' }}>
        <style>{pulseStyles}</style>
        {[...Array(5)].map((_, i) => <FeedSkeleton key={i} />)}
      </div>
    );
  }

  if (type === 'profile') {
    return (
      <div style={{ width: '100%', minHeight: '100vh', backgroundColor: 'var(--background)' }}>
        <style>{pulseStyles}</style>
        <ProfileSkeleton />
      </div>
    );
  }

  return (
    <div style={{ padding: '2rem', width: '100%' }}>
      <style>{pulseStyles}</style>
      <div style={{ 
        display: 'grid', 
        gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', 
        gap: '2rem',
        width: '100%'
      }}>
        {[...Array(8)].map((_, i) => <CardSkeleton key={i} />)}
      </div>
    </div>
  );
};

export default Loader;
