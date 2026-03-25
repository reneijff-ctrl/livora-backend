import React, { useState, useEffect, useRef, useCallback } from 'react';
import apiClient from '@/api/apiClient';
import ChatComponent from '@/components/ChatComponent';
import TipBar from '@/components/live/TipBar';
import GiftSoundControls from '@/components/GiftSoundControls';
import { PmSession, PmMessage } from '@/api/pmService';

interface LiveChatPanelProps {
  creatorId: number | undefined;
  roomId: string | undefined;
  onTipClick: () => void;
  onGiftClick?: () => void;
  onProfileOpen: (username: string) => void;
  chatWidth?: number;
  availability: "ONLINE" | "LIVE" | "OFFLINE" | null;
  minTip: number;
  onTip: (payloadOrAmount: any) => void;
  onGoalUpdate?: (payload: any) => void;
  onSuperTipEnd?: () => void;
  // PM simple single-session props (viewer)
  pmSession?: PmSession | null;
  pmMessages?: PmMessage[];
  unreadPm?: number;
  onSendPm?: (content: string) => void;
  onPmTabOpen?: () => void;
  activeTab?: 'CHAT' | 'PM' | 'USERS';
  onTabChange?: (tab: 'CHAT' | 'PM' | 'USERS') => void;
  userId?: number;
  pmEnded?: boolean;
  viewers?: { id: number; username: string; displayName: string | null; isFollower?: boolean; isModerator?: boolean }[];
  viewersLoading?: boolean;
  hiddenUserIds?: Set<number>;
  onToggleHideUser?: (userId: number) => void;
  onUnhideAll?: () => void;
  followerUserIds?: Set<number>;
  moderatorUserIds?: Set<number>;
  isCurrentUserMod?: boolean;
  chatFontSize?: number;
  onChatFontSizeChange?: (size: number) => void;
  showTipOverlays?: boolean;
  onShowTipOverlaysChange?: (show: boolean) => void;
  showTopTipperBanner?: boolean;
  onShowTopTipperBannerChange?: (show: boolean) => void;
}

const LiveChatPanel: React.FC<LiveChatPanelProps> = ({
  creatorId,
  roomId,
  onTipClick,
  onGiftClick,
  onProfileOpen,
  availability,
  minTip,
  onTip,
  onGoalUpdate,
  onSuperTipEnd,
  pmSession = null,
  pmMessages = [],
  unreadPm = 0,
  onSendPm,
  onPmTabOpen,
  activeTab: controlledTab,
  onTabChange,
  userId,
  pmEnded = false,
  viewers = [],
  viewersLoading = false,
  hiddenUserIds = new Set(),
  onToggleHideUser,
  onUnhideAll,
  followerUserIds,
  moderatorUserIds = new Set(),
  isCurrentUserMod = false,
  chatFontSize = 14,
  onChatFontSizeChange,
  showTipOverlays = true,
  onShowTipOverlaysChange,
  showTopTipperBanner = true,
  onShowTopTipperBannerChange,
}) => {
  const [internalTab, setInternalTab] = useState<'CHAT' | 'PM' | 'USERS'>('CHAT');
  const [settingsOpen, setSettingsOpen] = useState(false);
  const activeTab = settingsOpen ? 'SETTINGS' as const : (controlledTab ?? internalTab);
  const setActiveTab = onTabChange ?? setInternalTab;

  const [pmInput, setPmInput] = useState('');
  const [showHidden, setShowHidden] = useState(false);
  const [expandedModActions, setExpandedModActions] = useState<number | null>(null);
  const pmEndRef = useRef<HTMLDivElement>(null);

  // Moderator action handlers
  const handleModMute = useCallback(async (viewerId: number, minutes: number) => {
    try {
      await apiClient.post('/stream/moderation/mute', { creatorId, userId: viewerId, durationMinutes: minutes });
    } catch {}
  }, [creatorId]);

  const handleModShadowMute = useCallback(async (viewerId: number) => {
    try {
      await apiClient.post('/stream/moderation/shadow-mute', { creatorId, userId: viewerId });
    } catch {}
  }, [creatorId]);

  const handleModKick = useCallback(async (viewerId: number) => {
    try {
      await apiClient.post('/stream/moderation/kick', { creatorId, userId: viewerId });
    } catch {}
  }, [creatorId]);

  const handleModBan = useCallback(async (viewerId: number, banType: string) => {
    try {
      await apiClient.post('/room-bans/ban', { creatorId, targetUserId: viewerId, banType });
    } catch {}
  }, [creatorId]);

  useEffect(() => {
    pmEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [pmMessages]);

  const handleSendPm = () => {
    if (!pmInput.trim() || !pmSession || !onSendPm) return;
    onSendPm(pmInput.trim());
    setPmInput('');
  };

  const handleTabClick = (tab: 'CHAT' | 'PM' | 'USERS') => {
    setSettingsOpen(false);
    setActiveTab(tab);
    if (tab === 'PM' && onPmTabOpen) {
      onPmTabOpen();
    }
  };

  return (
    <div 
      className="relative flex flex-col h-full min-h-0 bg-[#0A0A0F] border-l border-white/5 shadow-2xl overflow-hidden"
    >
      {/* Tabs */}
      <div className="flex items-center justify-between px-6 py-4 border-b border-white/5 bg-black/20 backdrop-blur-sm">
        <div className="flex gap-6">
          <button 
            onClick={() => handleTabClick('CHAT')}
            className={`text-xs font-black uppercase tracking-[0.2em] transition-all relative py-1 ${
              activeTab === 'CHAT' ? 'text-white' : 'text-zinc-500 hover:text-zinc-300'
            }`}
          >
            Chat
            {activeTab === 'CHAT' && <div className="absolute -bottom-4 left-0 right-0 h-0.5 bg-indigo-500 shadow-[0_0_10px_rgba(99,102,241,0.5)]" />}
          </button>
          <button 
            onClick={() => handleTabClick('PM')}
            className={`text-xs font-black uppercase tracking-[0.2em] transition-all relative py-1 ${
              activeTab === 'PM' ? 'text-white' : 'text-zinc-500 hover:text-zinc-300'
            }`}
          >
            PM
            {unreadPm > 0 && activeTab !== 'PM' && (
              <span className="ml-1.5 inline-flex items-center justify-center w-4 h-4 bg-red-500 rounded-full text-[9px] font-bold text-white leading-none">
                {unreadPm}
              </span>
            )}
            {activeTab === 'PM' && <div className="absolute -bottom-4 left-0 right-0 h-0.5 bg-indigo-500 shadow-[0_0_10px_rgba(99,102,241,0.5)]" />}
          </button>
          <button 
            onClick={() => handleTabClick('USERS')}
            className={`text-xs font-black uppercase tracking-[0.2em] transition-all relative py-1 ${
              activeTab === 'USERS' ? 'text-white' : 'text-zinc-500 hover:text-zinc-300'
            }`}
          >
            Viewers
            {viewers.length > 0 && (
              <span className="ml-1.5 inline-flex items-center justify-center min-w-[16px] h-4 px-1 bg-indigo-500/30 border border-indigo-500/40 rounded-full text-[9px] font-bold text-indigo-300 leading-none">
                {viewers.length}
              </span>
            )}
            {activeTab === 'USERS' && <div className="absolute -bottom-4 left-0 right-0 h-0.5 bg-indigo-500 shadow-[0_0_10px_rgba(99,102,241,0.5)]" />}
          </button>
        </div>
        <button
          onClick={() => setSettingsOpen(s => !s)}
          className={`p-2 transition-all duration-200 rounded-lg hover:bg-white/5 ${settingsOpen ? 'text-white' : 'text-zinc-500 hover:text-white'}`}
          title="Audio Settings"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
          </svg>
        </button>
      </div>

      <div className="flex-1 overflow-hidden relative flex flex-col">
        {/* SETTINGS panel */}
        <div style={{ display: activeTab === 'SETTINGS' ? 'block' : 'none' }} className="flex-1 overflow-y-auto">
          <div className="p-6 space-y-6">
            <div>
              <h4 className="text-[10px] font-black text-zinc-500 uppercase tracking-[0.2em] mb-4">Audio Settings</h4>
              <div className="space-y-4">
                <div className="flex justify-between items-center">
                  <span className="text-[10px] uppercase tracking-widest text-gray-500">Alert & Notification Volume</span>
                </div>
                <GiftSoundControls isStatic={true} />
              </div>
            </div>
            <div>
              <h4 className="text-[10px] font-black text-zinc-500 uppercase tracking-[0.2em] mb-4">Chat</h4>
              <div className="space-y-4">
                <div>
                  <div className="flex justify-between items-center mb-2">
                    <span className="text-[10px] uppercase tracking-widest text-gray-500">Chat Font Size</span>
                    <span className="text-xs text-purple-400 tabular-nums">{chatFontSize}px</span>
                  </div>
                  <input
                    type="range" min="10" max="22" value={chatFontSize}
                    onChange={e => onChatFontSizeChange?.(Number(e.target.value))}
                    className="w-full h-1 bg-zinc-800 rounded-full appearance-none cursor-pointer [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-4 [&::-webkit-slider-thumb]:h-4 [&::-webkit-slider-thumb]:bg-purple-500 [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:shadow-lg [&::-webkit-slider-thumb]:shadow-purple-500/40"
                  />
                </div>
              </div>
            </div>
            <div>
              <h4 className="text-[10px] font-black text-zinc-500 uppercase tracking-[0.2em] mb-4">Overlays</h4>
              <div className="space-y-3">
                <label className="flex items-center justify-between cursor-pointer group">
                  <span className="text-[10px] uppercase tracking-widest text-gray-500 group-hover:text-gray-400 transition">Show Tip Animations</span>
                  <button
                    onClick={() => onShowTipOverlaysChange?.(!showTipOverlays)}
                    className={`relative w-9 h-5 rounded-full transition-colors duration-200 ${showTipOverlays ? 'bg-indigo-500' : 'bg-zinc-700'}`}
                  >
                    <span className={`absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full shadow transition-transform duration-200 ${showTipOverlays ? 'translate-x-4' : 'translate-x-0'}`} />
                  </button>
                </label>
                <label className="flex items-center justify-between cursor-pointer group">
                  <span className="text-[10px] uppercase tracking-widest text-gray-500 group-hover:text-gray-400 transition">Show Top Tipper Banner</span>
                  <button
                    onClick={() => onShowTopTipperBannerChange?.(!showTopTipperBanner)}
                    className={`relative w-9 h-5 rounded-full transition-colors duration-200 ${showTopTipperBanner ? 'bg-indigo-500' : 'bg-zinc-700'}`}
                  >
                    <span className={`absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full shadow transition-transform duration-200 ${showTopTipperBanner ? 'translate-x-4' : 'translate-x-0'}`} />
                  </button>
                </label>
              </div>
            </div>
          </div>
        </div>

        {/* CHAT panel — always mounted to preserve WebSocket subscription and message state */}
        <div style={{ display: activeTab === 'CHAT' ? 'flex' : 'none' }} className="flex-1 overflow-hidden relative group/chat flex-col">
          <ChatComponent 
            creatorId={creatorId} 
            streamRoomId={roomId}
            onTip={onTip}
            onGoalUpdate={onGoalUpdate}
            onSuperTipEnd={onSuperTipEnd}
            hiddenUserIds={hiddenUserIds}
            followerUserIds={followerUserIds}
            chatFontSize={chatFontSize}
          />
        </div>

        {/* PM panel */}
        <div style={{ display: activeTab === 'PM' ? 'flex' : 'none' }} className="flex-1 flex-col overflow-hidden">
          {!pmSession ? (
            <div className="flex-1 flex flex-col items-center justify-center p-8 text-center bg-black/20">
              <div className="w-16 h-16 bg-white/5 rounded-full flex items-center justify-center mb-4">
                <span className="text-2xl opacity-50">{pmEnded ? '🔒' : '💬'}</span>
              </div>
              <h4 className="text-white font-bold mb-2">{pmEnded ? 'Conversation ended' : 'No private messages'}</h4>
              <p className="text-zinc-600 text-[11px] max-w-[200px]">{pmEnded ? 'This private conversation has been closed by the creator.' : 'When a creator starts a private chat with you, it will appear here.'}</p>
            </div>
          ) : (
            <>
              <div className="px-4 py-2 border-b border-white/10 bg-black/40 shrink-0">
                <p className="text-xs font-bold text-white">💬 {pmSession.creatorUsername || 'Creator'}</p>
              </div>
              <div className="flex-1 overflow-y-auto px-3 py-2 space-y-2">
                {pmMessages.map((m, i) => (
                  <div key={i} className={`flex ${m.senderId === userId ? 'justify-end' : 'justify-start'}`}>
                    <div className={`max-w-[80%] px-3 py-1.5 rounded-xl text-xs ${m.senderId === userId ? 'bg-indigo-600 text-white' : 'bg-white/10 text-zinc-300'}`}>
                      {m.content}
                    </div>
                  </div>
                ))}
                <div ref={pmEndRef} />
              </div>
              <div className="px-3 py-2 border-t border-white/10 shrink-0 flex gap-2">
                <input
                  value={pmInput}
                  onChange={e => setPmInput(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && handleSendPm()}
                  placeholder="Type a message..."
                  className="flex-1 bg-white/5 border border-white/10 rounded-lg px-3 py-1.5 text-xs text-white placeholder-zinc-500 outline-none focus:border-indigo-500"
                />
                <button
                  onClick={handleSendPm}
                  disabled={!pmInput.trim() || !pmSession}
                  className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40 rounded-lg text-xs font-bold text-white transition"
                >
                  Send
                </button>
              </div>
            </>
          )}
        </div>

        {/* VIEWERS panel */}
        <div style={{ display: activeTab === 'USERS' ? 'flex' : 'none' }} className="flex-1 flex-col overflow-hidden bg-black/20">
          {viewersLoading ? (
            <div className="flex-1 flex items-center justify-center">
              <span className="text-zinc-500 text-xs">Loading viewers...</span>
            </div>
          ) : viewers.length === 0 ? (
            <div className="flex-1 flex flex-col items-center justify-center p-8 text-center">
              <div className="w-16 h-16 bg-white/5 rounded-full flex items-center justify-center mb-4">
                <span className="text-2xl opacity-50">👥</span>
              </div>
              <h4 className="text-white font-bold mb-2">No viewers yet</h4>
              <p className="text-zinc-600 text-[11px] max-w-[200px]">Viewers will appear here when they join the stream.</p>
            </div>
          ) : (
            <div className="flex-1 overflow-y-auto">
              <div className="px-4 py-2 border-b border-white/5 sticky top-0 bg-black/40 backdrop-blur-sm">
                <div className="flex items-center justify-between">
                  <span className="text-zinc-400 text-[10px] font-bold uppercase tracking-widest">{viewers.length} viewer{viewers.length !== 1 ? 's' : ''}</span>
                  {hiddenUserIds.size > 0 && (
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => setShowHidden(h => !h)}
                        className={`text-[10px] font-bold transition ${
                          showHidden ? 'text-indigo-300' : 'text-zinc-500 hover:text-zinc-400'
                        }`}
                      >
                        {showHidden ? 'Hide hidden' : `Show hidden (${hiddenUserIds.size})`}
                      </button>
                      {onUnhideAll && (
                        <button
                          onClick={onUnhideAll}
                          className="text-indigo-400 hover:text-indigo-300 text-[10px] font-bold transition"
                        >
                          Unhide all
                        </button>
                      )}
                    </div>
                  )}
                </div>
              </div>
              {viewers.filter((v) => showHidden || !hiddenUserIds.has(v.id)).map((v) => {
                const isHidden = hiddenUserIds.has(v.id);
                const isSelf = userId != null && v.id === userId;
                const isViewerMod = v.isModerator || moderatorUserIds.has(v.id);
                const isCreator = creatorId != null && v.id === creatorId;
                // Show mod actions if current user is mod and target is not self/creator/another mod
                const canModerate = isCurrentUserMod && !isSelf && !isCreator && !isViewerMod;
                console.debug('[MOD_DEBUG] viewer row:', v.username, '{ isSelf:', isSelf, 'isCreator:', isCreator, 'isViewerMod:', isViewerMod, 'isCurrentUserMod:', isCurrentUserMod, 'canModerate:', canModerate, '}');
                const isExpanded = expandedModActions === v.id;
                return (
                  <div key={v.id} className={`border-b border-white/5 hover:bg-white/5 transition ${isHidden ? 'opacity-50' : ''}`}>
                    <div className="flex items-center gap-3 px-4 py-2.5">
                      <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold ${isViewerMod ? 'bg-green-500/20 text-green-300' : 'bg-indigo-500/20 text-indigo-300'}`}>
                        {(v.displayName || v.username).charAt(0).toUpperCase()}
                      </div>
                      <span className="text-white text-xs font-medium truncate">{v.displayName || v.username}</span>
                      {isViewerMod && (
                        <span className="shrink-0 px-1.5 py-0.5 rounded text-[9px] font-bold bg-green-500/15 text-green-400 border border-green-500/20">🛡 Mod</span>
                      )}
                      {v.isFollower && (
                        <span className="shrink-0 px-1.5 py-0.5 rounded text-[9px] font-bold bg-amber-500/15 text-amber-400 border border-amber-500/20">★ Follower</span>
                      )}
                      <div className="ml-auto flex items-center gap-1">
                        {canModerate && (
                          <button
                            onClick={() => setExpandedModActions(isExpanded ? null : v.id)}
                            className={`px-2 py-0.5 rounded text-[10px] font-bold transition ${
                              isExpanded ? 'bg-indigo-500/20 text-indigo-300' : 'bg-white/5 text-zinc-500 hover:bg-white/10 hover:text-zinc-300'
                            }`}
                            title="Moderation actions"
                          >
                            🛡 Mod
                          </button>
                        )}
                        {!isSelf && onToggleHideUser && (
                          <button
                            onClick={() => onToggleHideUser(v.id)}
                            className={`px-2 py-0.5 rounded text-[10px] font-bold transition ${
                              isHidden
                                ? 'bg-zinc-700 text-zinc-300 hover:bg-zinc-600'
                                : 'bg-white/5 text-zinc-500 hover:bg-white/10 hover:text-zinc-300'
                            }`}
                            title={isHidden ? 'Unhide messages from this user' : 'Hide messages from this user'}
                          >
                            {isHidden ? '🔇 Hidden' : 'Hide'}
                          </button>
                        )}
                      </div>
                    </div>
                    {canModerate && isExpanded && (
                      <div className="px-4 pb-2.5 space-y-1">
                        <div className="flex items-center gap-1 flex-wrap">
                          <button onClick={() => handleModMute(v.id, 5)} className="px-2 py-1 rounded-lg bg-zinc-800 text-zinc-300 hover:bg-zinc-700 text-[10px] font-bold border border-white/5 transition">Mute 5m</button>
                          <button onClick={() => handleModMute(v.id, 30)} className="px-2 py-1 rounded-lg bg-zinc-800 text-zinc-300 hover:bg-zinc-700 text-[10px] font-bold border border-white/5 transition">30m</button>
                          <button onClick={() => handleModMute(v.id, 1440)} className="px-2 py-1 rounded-lg bg-zinc-800 text-zinc-300 hover:bg-zinc-700 text-[10px] font-bold border border-white/5 transition">24h</button>
                          <button onClick={() => handleModShadowMute(v.id)} className="px-2 py-1 rounded-lg bg-indigo-500/20 text-indigo-300 hover:bg-indigo-500/30 text-[10px] font-bold border border-indigo-500/20 transition">Shadow Mute</button>
                          <button onClick={() => handleModKick(v.id)} className="px-2 py-1 rounded-lg bg-red-500/20 text-red-400 hover:bg-red-500/30 text-[10px] font-bold border border-red-500/20 transition">Kick</button>
                        </div>
                        <div className="flex items-center gap-1 flex-wrap">
                          <span className="text-[9px] text-zinc-600 font-bold uppercase mr-1">Ban:</span>
                          <button onClick={() => handleModBan(v.id, '5m')} className="px-2 py-1 rounded-lg bg-orange-500/15 text-orange-400 hover:bg-orange-500/25 text-[10px] font-bold border border-orange-500/20 transition">5m</button>
                          <button onClick={() => handleModBan(v.id, '30m')} className="px-2 py-1 rounded-lg bg-orange-500/15 text-orange-400 hover:bg-orange-500/25 text-[10px] font-bold border border-orange-500/20 transition">30m</button>
                          <button onClick={() => handleModBan(v.id, '24h')} className="px-2 py-1 rounded-lg bg-orange-500/15 text-orange-400 hover:bg-orange-500/25 text-[10px] font-bold border border-orange-500/20 transition">24h</button>
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {availability === 'LIVE' && roomId && creatorId && (
        <div className="flex-shrink-0">
          <TipBar 
            creatorId={creatorId} 
            roomId={roomId} 
            minTip={minTip}
            onTip={onTip}
            onGiftClick={onGiftClick}
          />
        </div>
      )}
    </div>
  );
};

export default React.memo(LiveChatPanel);
