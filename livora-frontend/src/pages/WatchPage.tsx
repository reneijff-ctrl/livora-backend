import React, { useEffect, useState, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useCreatorPublicProfile } from '@/hooks/useCreatorPublicProfile';
import { webSocketService } from '@/websocket/webSocketService';
import apiClient from '@/api/apiClient';
import { safeRender } from '@/utils/safeRender';
import { useAuth } from '@/auth/useAuth';
import { useWs } from '@/ws/WsContext';
import Navbar from '@/components/Navbar';
import AbuseReportModal from '@/components/AbuseReportModal';
import TipModal from '@/components/TipModal';
import GiftSelectorModal, { GiftSelectorModalHandle } from '@/components/GiftSelectorModal';
import { ReportTargetType } from '@/types/report';
import { showToast } from '@/components/Toast';
import LiveLayout from '@/layouts/LiveLayout';

// Modular Components
import LiveStreamPlayer from '@/components/live/LiveStreamPlayer';
import GoalOverlay from '@/components/live/GoalOverlay';
import GoalLadderOverlay from '@/components/live/GoalLadderOverlay';
import LiveChatPanel from '@/components/live/LiveChatPanel';
import LiveTipOverlays from '@/components/live/LiveTipOverlays';
import TopTipperBanner from '@/components/TopTipperBanner';
import LeaderboardPanel, { LeaderboardPanelHandle } from '@/components/LeaderboardPanel';
import PrivateShowRequestButton from '@/components/PrivateShowRequestButton';
import PrivateShowSessionOverlay from '@/components/PrivateShowSessionOverlay';
import { PrivateSessionStatus } from '@/api/privateShowService';

// Extracted hooks
import { useViewerSettings } from '@/hooks/useViewerSettings';
import { useRoomModeration } from '@/hooks/useRoomModeration';
import { usePrivateShow } from '@/hooks/usePrivateShow';
import { usePmSession } from '@/hooks/usePmSession';
import { useStreamTips } from '@/hooks/useStreamTips';
import { useFollowState } from '@/hooks/useFollowState';

const WatchPage: React.FC = () => {
  const { identifier } = useParams<{ identifier: string }>();
  const { creator, loading: profileLoading } = useCreatorPublicProfile();
  const { user, refreshTokenBalance } = useAuth();
  const navigate = useNavigate();
  const { presenceMap, subscribe, connected } = useWs();

  // Stream state
  const [availability, setAvailability] = useState<"ONLINE" | "LIVE" | "OFFLINE" | null>(null);
  const [room, setRoom] = useState<any>(null);
  const [hasAccess, setHasAccess] = useState<boolean | null>(null);
  const [, setViewerCount] = useState<number>(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [needsInteraction, setNeedsInteraction] = useState(false);
  const [showReportModal, setShowReportModal] = useState(false);
  const [showTipModal, setShowTipModal] = useState(false);
  const [videoWidth] = useState(1400);
  const [isResizing] = useState(false);

  const sessionIdRef = useRef(Math.random().toString(36).substring(2, 9));
  const sessionId = sessionIdRef.current;
  const containerRef = useRef<HTMLDivElement>(null);
  const leaderboardRef = useRef<LeaderboardPanelHandle>(null);
  const giftModalRef = useRef<GiftSelectorModalHandle>(null);

  const creatorUserId = creator?.profile?.userId;
  const isOwnPage = user && creatorUserId ? Number(user.id) === creatorUserId : false;
  const userId = user?.id;

  // --- Extracted hooks ---
  const viewerSettings = useViewerSettings();

  const pm = usePmSession(
    userId,
    connected,
    subscribe,
  );

  const moderation = useRoomModeration(
    userId,
    creatorUserId,
    isOwnPage,
    pm.activeTab,
  );

  const privateShow = usePrivateShow(
    userId,
    creatorUserId,
    isOwnPage,
    sessionId,
    connected,
    subscribe,
  );

  const tips = useStreamTips(
    creatorUserId,
    availability,
    user?.displayName,
    room?.streamRoomId,
  );

  const follow = useFollowState(
    creatorUserId,
    creator?.profile,
    !!user,
  );

  // --- Stream state effects (remaining in WatchPage) ---

  // Fetch availability
  useEffect(() => {
    const fetchData = async () => {
      if (!creator?.profile?.id) return;
      try {
        const res = await apiClient.get(`/v2/public/creators/${creatorUserId}/availability`);
        console.debug("CREATOR AVAILABILITY RESPONSE", res.data);
        setAvailability(res.data.availability || res.data.status || "OFFLINE");
        if (res.data.viewerCount !== undefined) setViewerCount(res.data.viewerCount);
      } catch (err) { 
        console.warn("Stream availability endpoint failed", err);
        setAvailability("OFFLINE");
        setViewerCount(0);
      }
      finally { setLoading(false); }
    };
    fetchData();
  }, [creatorUserId, creator?.profile?.id]);

  // Fetch room access
  useEffect(() => {
    if (!creatorUserId || !user || availability !== "LIVE") { setRoom(null); return; }
    apiClient.get(`/livestream/${creatorUserId}/access`).then(res => { setRoom(res.data); setHasAccess(res.data.hasAccess); });
  }, [creatorUserId, user, availability]);

  // Presence-driven availability updates
  useEffect(() => {
    if (!creatorUserId) return;
    const presence = presenceMap[Number(creatorUserId)];
    if (presence?.availability) {
      setAvailability(presence.availability as "ONLINE" | "LIVE" | "OFFLINE");
    }
  }, [creatorUserId, presenceMap]);

  // Viewer count WS subscription
  useEffect(() => {
    if (!creatorUserId) return;
    const unsubViewers = webSocketService.subscribe(`/exchange/amq.topic/viewers.${creatorUserId}`, (msg) => {
      const payload = JSON.parse(msg.body).payload || JSON.parse(msg.body);
      if (payload.viewerCount !== undefined) setViewerCount(payload.viewerCount);
    });
    return () => { if (typeof unsubViewers === "function") unsubViewers(); };
  }, [creatorUserId]);

  const handleUnlock = useCallback(async () => {
    if (!creatorUserId || loading) return;
    setLoading(true);
    try {
      const res = await apiClient.post(`/livestream/${creatorUserId}/unlock`);
      if (res.data.success) { setHasAccess(true); setRoom(res.data.room); refreshTokenBalance(); }
    } catch (err: any) { console.error("Unlock failed", err); setError(err.response?.data?.message || "Failed to unlock stream"); }
    finally { setLoading(false); }
  }, [creatorUserId, loading, refreshTokenBalance]);

  // --- Render ---

  console.debug("Creator loaded", creator);
  console.debug("Availability state", availability);

  if (profileLoading || (loading && !creator?.profile)) {
    return (
      <div className="h-screen bg-black flex items-center justify-center">
        <div className="flex flex-col items-center gap-4">
          <div className="w-12 h-12 border-4 border-white/20 border-t-white rounded-full animate-spin" />
          <p className="text-white font-medium animate-pulse">Loading experience...</p>
        </div>
      </div>
    );
  }

  if (creator === null || creator === undefined || !creator?.profile?.id) {
    console.debug("Creator not found guard triggered", { 
      hasCreator: !!creator, 
      hasProfile: !!creator?.profile,
      profileLoading, 
      loading 
    });
    return (
      <div className="h-screen bg-black flex flex-col items-center justify-center p-8 text-center">
        <h2 className="text-white text-2xl font-bold mb-4">Creator not found</h2>
        <p className="text-zinc-400 mb-8 max-w-md">
          We couldn't find the creator you're looking for. They may have changed their username or deleted their account.
        </p>
        <button 
          onClick={() => navigate('/explore')} 
          className="px-8 py-3 bg-white text-black rounded-full font-bold hover:bg-zinc-200 transition"
        >
          Back to Explore
        </button>
      </div>
    );
  }

  return (
    <div className="h-screen flex flex-col bg-[#050505] overflow-hidden">
      <Navbar />
      <div className="flex flex-1 overflow-hidden">
        <LiveLayout
          video={
            <div className={`premium-video-container flex flex-col ${availability === 'LIVE' ? 'video-live' : ''} flex-1 overflow-hidden relative`}>
              <div className="absolute top-0 left-0 right-0 flex items-center justify-between p-6 bg-gradient-to-b from-black/80 z-20">
                <div className="flex items-center gap-3">
                  <button onClick={() => navigate(`/creators/${identifier}`)} className="p-2 hover:bg-white/10 rounded-full transition text-white">←</button>
                  <div>
                    <h2 className="font-semibold text-lg text-white leading-tight">{safeRender(creator.profile.displayName)}</h2>
                    <div className="flex items-center gap-2">
                      <span className={`w-2 h-2 rounded-full ${availability === 'LIVE' ? 'bg-red-500 animate-pulse' : (availability === 'ONLINE' ? 'bg-green-500' : 'bg-zinc-500')}`} />
                      <p className="text-[11px] text-zinc-100/70 font-medium">
                        {availability === 'LIVE' ? 'Live' : (availability === 'ONLINE' ? 'Online' : 'Offline')}
                      </p>
                      <span className="text-[11px] text-zinc-100/50">·</span>
                      <p className="text-[11px] text-zinc-100/70 font-medium">{follow.followerCount.toLocaleString()} {follow.followerCount === 1 ? 'follower' : 'followers'}</p>
                    </div>
                  </div>
                  {user && !isOwnPage && (
                    <button
                      onClick={follow.toggleFollow}
                      disabled={follow.followLoading}
                      className={`ml-2 px-3 py-1 rounded-full text-xs font-bold transition-all active:scale-95 border ${
                        follow.isFollowing
                          ? 'bg-white/10 text-white border-white/20 hover:bg-white/20'
                          : 'bg-white text-black border-white hover:bg-zinc-200'
                      } ${follow.followLoading ? 'opacity-50 cursor-not-allowed' : ''}`}
                    >
                      {follow.followLoading ? '...' : follow.isFollowing ? 'Following' : 'Follow'}
                    </button>
                  )}
                </div>
                {availability === "LIVE" && (
                  <div className="status-cluster flex items-center gap-2">
                    <button onClick={() => giftModalRef.current?.open()} className="p-1.5 px-3 bg-indigo-500 rounded-full text-xs font-bold text-white shadow-lg">🎁 Gifts</button>
                    <button onClick={() => setShowTipModal(true)} className="send-tokens-btn px-3 py-1.5 bg-white text-black rounded-full text-xs font-bold">Send Tokens</button>
                    <button onClick={() => leaderboardRef.current?.toggle()} className="p-1.5 px-3 bg-white/5 rounded-full text-xs font-bold text-white border border-white/5">🏆 Leaderboard</button>
                    {user && Number(user.id) !== creatorUserId && privateShow.privateSettings?.enabled && !privateShow.privateSession && !privateShow.spySession && (
                      <PrivateShowRequestButton creatorId={creatorUserId!} pricePerMinute={privateShow.privateSettings.pricePerMinute} onSessionCreated={(session) => privateShow.setPrivateSession(session)} />
                    )}
                    {user && !privateShow.privateSession && !privateShow.spySession && privateShow.creatorActivePrivateSession && privateShow.privateSettings?.allowSpyOnPrivate && privateShow.privateAvailability?.allowSpyOnPrivate && (
                      <button
                        onClick={privateShow.handleJoinSpy}
                        disabled={privateShow.isJoiningSpy}
                        className="px-3 py-1.5 bg-gradient-to-r from-amber-600 to-orange-500 text-white rounded-full text-xs font-bold shadow-lg hover:from-amber-500 hover:to-orange-400 transition disabled:opacity-50"
                        title={`Spy on private — ${privateShow.privateSettings.spyPricePerMinute} tokens/min`}
                      >
                        {privateShow.isJoiningSpy ? 'Joining...' : `👁 Spy · ${privateShow.privateSettings.spyPricePerMinute}/min`}
                      </button>
                    )}
                    {privateShow.spySession && (
                      <>
                        <span className="px-2.5 py-1 bg-amber-500/15 border border-amber-500/40 rounded-full text-amber-300 text-xs font-bold">👁 Spying · {privateShow.spySession.spyPricePerMinute}/min</span>
                        <button
                          onClick={privateShow.handleLeaveSpy}
                          disabled={privateShow.isLeavingSpy}
                          className="px-3 py-1.5 bg-amber-600 hover:bg-amber-700 disabled:opacity-60 disabled:cursor-not-allowed text-white rounded-full text-xs font-bold transition"
                        >
                          {privateShow.isLeavingSpy ? 'Leaving...' : 'Leave Spy'}
                        </button>
                      </>
                    )}
                    {privateShow.privateSession?.status === PrivateSessionStatus.REQUESTED && (
                      <span className="px-2.5 py-1 bg-purple-500/15 border border-purple-500/40 rounded-full text-purple-300 text-xs font-bold animate-pulse">⏳ Waiting for creator...</span>
                    )}
                    {privateShow.privateSession?.status === PrivateSessionStatus.ACCEPTED && (
                      <span className="px-2.5 py-1 bg-purple-500/15 border border-purple-500/40 rounded-full text-purple-300 text-xs font-bold">✅ Creator accepted! Starting soon...</span>
                    )}
                    {privateShow.privateSession?.status === PrivateSessionStatus.ACTIVE && (
                      <>
                        <span className="px-2.5 py-1 bg-red-500/15 border border-red-500/40 rounded-full text-red-400 text-xs font-bold">🔴 Private Active</span>
                        <button
                          onClick={privateShow.handleEndSession}
                          disabled={privateShow.isEndingSession}
                          className="px-3 py-1.5 bg-red-600 hover:bg-red-700 disabled:opacity-60 disabled:cursor-not-allowed text-white rounded-full text-xs font-bold transition"
                        >
                          {privateShow.isEndingSession ? 'Ending...' : 'End Private Session'}
                        </button>
                      </>
                    )}
                    {user && Number(user.id) !== creatorUserId && (
                      <button onClick={() => {
                        const lastReport = localStorage.getItem('report_cooldown');
                        if (lastReport && Date.now() - parseInt(lastReport, 10) < 60000) {
                          showToast('You can submit another report in a few seconds.', 'info');
                          return;
                        }
                        setShowReportModal(true);
                      }} className="p-1.5 px-3 bg-white/5 rounded-full text-xs font-bold text-white border border-white/5 hover:bg-red-500/20 hover:border-red-500/30 transition" title="Report Stream">🚩 Report</button>
                    )}
                  </div>
                )}
              </div>

              {moderation.isRoomBanned ? (
                <div className="flex-1 flex flex-col items-center justify-center bg-black/80 rounded-2xl border border-red-500/20 p-12 text-center gap-6">
                  <div className="w-20 h-20 rounded-full bg-red-500/10 border border-red-500/30 flex items-center justify-center text-4xl">🚫</div>
                  <div>
                    <h3 className="text-white text-xl font-bold mb-2">You are banned from this room</h3>
                    <p className="text-zinc-400 text-sm max-w-sm">
                      {moderation.roomBanInfo?.banType === 'permanent'
                        ? 'You have been permanently banned from this room.'
                        : moderation.roomBanInfo?.expiresAt && moderation.roomBanInfo.expiresAt !== 'never'
                          ? `Your ban expires at ${new Date(moderation.roomBanInfo.expiresAt).toLocaleString()}.`
                          : 'You have been banned from participating in this room.'}
                    </p>
                  </div>
                </div>
              ) : privateShow.isBlockedByPrivate ? (
                <div className="flex-1 flex flex-col items-center justify-center bg-black/80 rounded-2xl border border-white/5 p-12 text-center gap-6">
                  <div className="w-20 h-20 rounded-full bg-purple-500/10 border border-purple-500/30 flex items-center justify-center text-4xl">🔒</div>
                  <div>
                    <h3 className="text-white text-xl font-bold mb-2">Private Session in Progress</h3>
                    <p className="text-zinc-400 text-sm max-w-sm">This creator is currently in a private session. The stream will be available again when the session ends.</p>
                  </div>
                  {privateShow.privateAvailability?.allowSpyOnPrivate && privateShow.privateAvailability.canCurrentUserSpy && privateShow.privateAvailability.activeSessionId && (
                    <button
                      onClick={privateShow.handleJoinSpy}
                      disabled={privateShow.isJoiningSpy}
                      className="mt-2 px-6 py-2.5 bg-gradient-to-r from-amber-600 to-orange-500 text-white rounded-full text-sm font-bold shadow-lg hover:from-amber-500 hover:to-orange-400 transition disabled:opacity-50"
                    >
                      {privateShow.isJoiningSpy ? 'Joining...' : `👁 Spy on Private · ${privateShow.privateAvailability.spyPricePerMinute} tokens/min`}
                    </button>
                  )}
                </div>
              ) : (
                <LiveStreamPlayer
                  creatorId={creatorUserId} roomId={room?.streamRoomId} streamId={sessionId} user={user} availability={availability}
                  hasAccess={hasAccess} room={room} error={error} loading={loading} needsInteraction={needsInteraction}
                  setHasAccess={setHasAccess} setAvailability={setAvailability} setError={setError} setLoading={setLoading}
                  setNeedsInteraction={setNeedsInteraction} handleUnlock={handleUnlock} identifier={identifier || ""} navigate={navigate}
                  onProfileOpen={(u) => navigate(`/creators/${u}`)}
                  videoWidth={videoWidth} isResizing={isResizing} containerRef={containerRef}
                >
                  {viewerSettings.showTopTipperBanner && <TopTipperBanner creatorId={creatorUserId} topTipperName={tips.topTipper.name} topTipAmount={tips.topTipper.amount} onProfileOpen={(u) => navigate(`/creators/${u}`)} />}
                  <LeaderboardPanel ref={leaderboardRef} creatorId={creatorUserId} streamId={privateShow.effectiveStreamId} leaderboard={tips.leaderboard} />
                  {viewerSettings.showTipOverlays && <LiveTipOverlays creatorId={creatorUserId} roomId={room?.streamRoomId} overlayRef={tips.overlayRef} tokenExplosion={tips.tokenExplosion} legendaryEffect={tips.legendaryEffect} />}
                  {privateShow.privateSession?.status === PrivateSessionStatus.ACTIVE && (
                    <PrivateShowSessionOverlay
                      sessionId={privateShow.privateSession.id}
                      pricePerMinute={privateShow.privateSession.pricePerMinute}
                      isCreator={false}
                      onSessionEnded={() => privateShow.setPrivateSession(null)}
                    />
                  )}
                </LiveStreamPlayer>
              )}

              {tips.goal && tips.goal.active && tips.goalOverlayVisible && availability === 'LIVE' && (
                tips.goal.milestones && tips.goal.milestones.length > 0 ? (
                  <GoalLadderOverlay title={tips.goal.title} currentAmount={tips.goal.currentAmount} targetAmount={tips.goal.targetAmount} milestones={tips.goal.milestones} onClose={() => tips.setGoalOverlayVisible(false)} onMilestoneReached={tips.handleMilestoneReached} />
                ) : (
                  <GoalOverlay goal={tips.goal.targetAmount} progress={tips.goal.currentAmount} title={tips.goal.title} onClose={() => tips.setGoalOverlayVisible(false)} />
                )
              )}

            </div>
          }
          chat={
            <LiveChatPanel
              creatorId={creatorUserId} roomId={room?.streamRoomId} onTipClick={() => setShowTipModal(true)} onGiftClick={() => giftModalRef.current?.open()} onProfileOpen={(u) => navigate(`/creators/${u}`)}
              availability={availability} minTip={creator?.profile?.minTip || 1} onTip={tips.handleTip} 
              onGoalUpdate={tips.handleGoalUpdate}
              onSuperTipEnd={tips.handleSuperTipEnd}
              pmSession={pm.pmSession}
              pmMessages={pm.pmMessages}
              unreadPm={pm.pmSession?.unreadCount || 0}
              onSendPm={pm.handleSendPm}
              onPmTabOpen={pm.handlePmTabOpen}
              activeTab={pm.activeTab}
              onTabChange={pm.handleTabChange}
              userId={user ? Number(user.id) : undefined}
              pmEnded={pm.pmEnded}
              viewers={moderation.viewers}
              viewersLoading={moderation.viewersLoading}
              hiddenUserIds={viewerSettings.hiddenUserIds}
              onToggleHideUser={viewerSettings.toggleHideUser}
              onUnhideAll={viewerSettings.unhideAll}
              followerUserIds={moderation.followerUserIds}
              moderatorUserIds={moderation.moderatorUserIds}
              isCurrentUserMod={moderation.isCurrentUserMod}
              chatFontSize={viewerSettings.chatFontSize}
              onChatFontSizeChange={viewerSettings.setChatFontSize}
              showTipOverlays={viewerSettings.showTipOverlays}
              onShowTipOverlaysChange={viewerSettings.setShowTipOverlays}
              showTopTipperBanner={viewerSettings.showTopTipperBanner}
              onShowTopTipperBannerChange={viewerSettings.setShowTopTipperBanner}
            />
          }
        />
      </div>
      <GiftSelectorModal ref={giftModalRef} onSelectGift={tips.handleSelectGift} />
      {showReportModal && <AbuseReportModal isOpen={showReportModal} onClose={() => setShowReportModal(false)} targetType={ReportTargetType.STREAM} targetId={room?.streamRoomId || creator.profile.userId.toString()} targetLabel={creator.profile.displayName || creator.profile.username || 'this creator'} reportedUserId={creator.profile.userId} />}
      {showTipModal && <TipModal isOpen={showTipModal} onClose={() => setShowTipModal(false)} creatorId={creator.profile.userId} onSuccess={(amount) => tips.handleTip(amount)} />}
      {tips.showGoalBanner && tips.goal && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center pointer-events-none overflow-hidden">
          <div className="absolute inset-0 bg-indigo-900/20 backdrop-blur-sm" />
          <div className="relative flex flex-col items-center gap-6">
            <h2 className="text-6xl font-black text-white uppercase tracking-tighter">Goal Reached!</h2>
            <p className="text-2xl font-bold text-indigo-300 italic">"{safeRender(tips.goal.title)}"</p>
            <div className="bg-white/10 px-8 py-3 rounded-full border border-white/20 text-white font-black">🪙{safeRender(tips.goal.targetAmount.toLocaleString())}</div>
          </div>
        </div>
      )}

    </div>
  );
};

export default WatchPage;
