import React from 'react';
import { User } from '../types';

interface ViewerHeroSectionProps {
  user: User;
  tokenBalance: number;
  activeSubscriptions: number;
  totalSpent: number;
  role: string;
  onSecondaryActionClick: () => void;
  onExploreClick: () => void;
}

/**
 * ViewerHeroSection component.
 * Unified hero section for the Viewer Hub.
 * Merges the welcome message, current balance, and calls-to-action into one section.
 */
const ViewerHeroSection: React.FC<ViewerHeroSectionProps> = ({
  user,
  tokenBalance,
  activeSubscriptions,
  totalSpent,
  role,
  onSecondaryActionClick,
  onExploreClick,
}) => {
  const isCreator = role === 'CREATOR';

  // Conditional logic for the primary CTA text
  const getPrimaryCTAText = () => {
    if (tokenBalance > 1000) {
      return "Support a Creator Now 💎";
    }
    if (tokenBalance === 0) {
      return "Buy Tokens 🪙";
    }
    if (activeSubscriptions === 0 && tokenBalance > 0) {
      return "Discover Creators 🔍";
    }
    return "Explore Creators 🔍";
  };

  return (
    <div 
      className="backdrop-blur-sm relative overflow-hidden shadow-[0_0_60px_rgba(168,85,247,0.08)] before:absolute before:inset-0 before:bg-[radial-gradient(circle_at_top_right,rgba(168,85,247,0.05),transparent_60%)] before:content-[''] before:pointer-events-none" 
      style={styles.heroContainer}
    >
      <div style={styles.leftSide}>
        <span 
          className="text-xs uppercase tracking-widest opacity-50"
          style={styles.hubLabel}
        >
          Viewer Hub
        </span>
        
        <div className="mt-2" style={styles.welcomeInfo}>
          <h2 
            className="bg-clip-text text-transparent bg-gradient-to-r from-white to-purple-300"
            style={styles.welcomeTitle} 
            title={user.username || user.email || ""}
          >
            Welcome back, {user.username || user.displayName || 'Viewer'}! <span>👋</span>
          </h2>

          <div className="flex flex-col">
            <div className="flex">
              <span 
                className="text-xs px-3 py-1 rounded-full bg-purple-500/20 text-purple-300 font-medium"
              >
                {totalSpent > 0 ? "Supporter" : "New Member"}
              </span>
            </div>

            {totalSpent > 0 && (
              <div className="mt-2 w-32" title={`€${totalSpent} / €100 toward milestone`}>
                <div className="h-1.5 w-full bg-white/10 rounded-full overflow-hidden">
                  <div 
                    className="h-full bg-gradient-to-r from-purple-500 to-pink-500 transition-all duration-1000" 
                    style={{ width: `${Math.min((totalSpent / 100) * 100, 100)}%` }}
                  />
                </div>
              </div>
            )}
          </div>

          <p style={styles.heroSubtitle}>
            {role === 'VIEWER' 
              ? "Start your journey as a creator today or explore our talented community."
              : "Manage your subscriptions, tokens, and favorite creators all in one place."}
          </p>

          {totalSpent === 0 && activeSubscriptions === 0 && (
            <p className="text-sm opacity-70">
              Start by discovering creators that match your interests.
            </p>
          )}

          {totalSpent > 0 && (
            <p className="text-sm opacity-70 mt-2">
              Total supported: €{totalSpent}
            </p>
          )}
        </div>

        <div style={styles.actionRow}>
          <button 
            onClick={onExploreClick} 
            className="transition-all hover:scale-[1.03]"
            style={styles.primaryButton}
          >
            {getPrimaryCTAText()}
          </button>
          
          {(role === 'VIEWER' || isCreator) && (
            <div className="flex flex-col">
              <button 
                onClick={onSecondaryActionClick} 
                className="transition-all hover:scale-[1.03]"
                style={styles.secondaryButton}
              >
                {isCreator ? "Go to Creator Hub 🎨" : ((totalSpent >= 50 || activeSubscriptions >= 3) ? "Start Earning as Creator" : "Become a Creator 🚀")}
              </button>
              {totalSpent >= 20 && (
                <p className="text-xs opacity-60 mt-2">
                  Top creators earn from tips, subscriptions and private streams.
                </p>
              )}
            </div>
          )}
        </div>
      </div>

      <div style={styles.rightSide}>
        <div 
          className="glass-panel border border-white/10 bg-gradient-to-b from-white/5 to-transparent p-5 transition-all hover:shadow-lg" 
          style={styles.balanceCard}
        >
          <span style={styles.balanceLabel}>Token Balance</span>
          <span style={styles.balanceValue}>
            {tokenBalance} <span style={{ fontSize: '1.5rem', marginLeft: '0.25rem' }}>🪙</span>
          </span>
        </div>
      </div>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  heroContainer: {
    backgroundColor: 'rgba(15, 15, 20, 0.85)',
    padding: '3rem',
    borderRadius: '24px',
    border: '1px solid rgba(255, 255, 255, 0.05)',
    display: 'flex',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: '2rem',
    flexWrap: 'wrap',
  },
  leftSide: {
    flex: '1',
    display: 'flex',
    flexDirection: 'column',
    gap: '1.5rem',
    minWidth: '300px',
  },
  rightSide: {
    flexShrink: 0,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: '220px',
  },
  hubLabel: {
    display: 'block',
    color: '#818cf8',
  },
  welcomeInfo: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.5rem',
  },
  welcomeTitle: {
    margin: 0,
    fontSize: '3rem',
    fontWeight: '800',
    letterSpacing: '-0.02em',
  },
  heroSubtitle: {
    color: '#A1A1AA',
    fontSize: '1.125rem',
    lineHeight: '1.6',
    margin: 0,
    maxWidth: '550px',
  },
  balanceCard: {
    borderRadius: '20px',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: '0.5rem',
    minWidth: '220px',
  },
  balanceLabel: {
    fontSize: '0.875rem',
    fontWeight: '600',
    color: '#A1A1AA',
    textTransform: 'uppercase',
    letterSpacing: '0.1em',
  },
  balanceValue: {
    fontSize: '2.75rem',
    fontWeight: '800',
    color: '#F4F4F5',
    display: 'flex',
    alignItems: 'center',
  },
  actionRow: {
    display: 'flex',
    gap: '1rem',
    flexWrap: 'wrap',
  },
  primaryButton: {
    padding: '0.875rem 1.75rem',
    background: 'linear-gradient(135deg, #6366f1 0%, #a855f7 100%)',
    color: 'white',
    border: 'none',
    borderRadius: '12px',
    fontWeight: '700',
    fontSize: '1rem',
    cursor: 'pointer',
    transition: 'all 0.2s ease',
    boxShadow: '0 4px 12px rgba(99, 102, 241, 0.3)',
  },
  secondaryButton: {
    padding: '0.875rem 1.75rem',
    backgroundColor: 'rgba(168, 85, 247, 0.1)',
    color: '#c084fc',
    border: '1px solid rgba(168, 85, 247, 0.2)',
    borderRadius: '12px',
    fontWeight: '700',
    fontSize: '1rem',
    cursor: 'pointer',
    transition: 'all 0.2s ease',
  },
};

export default ViewerHeroSection;
