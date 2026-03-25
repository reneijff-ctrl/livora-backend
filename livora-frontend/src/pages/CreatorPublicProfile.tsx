/**
 * LAYOUT CONTRACT — PHASE E (Polished)
 * - Minimalist profile layout
 * - SafeAvatar usage (96x96)
 * - Max width 640px
 * - Centered typography focus
 * - Consistent vertical spacing
 * - Subtle typography hierarchy
 */
import React, { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '@/auth/useAuth';
import { useWallet } from '@/wallet/WalletContext';
import { useCreatorPublicProfile } from '@/hooks/useCreatorPublicProfile';
import CreatorMediaTab from '@/components/creator/CreatorMediaTab';
import SafeAvatar from '@/components/ui/SafeAvatar';
import EmptyState from '@/components/EmptyState';
import apiClient from '@/api/apiClient';
import creatorService from '@/api/creatorService';
import { showToast } from '@/components/Toast';
import { safeRender } from '@/utils/safeRender';

const calculateAge = (birthDate: string | undefined): number | null => {
  if (!birthDate) return null;
  const today = new Date();
  const birth = new Date(birthDate);
  let age = today.getFullYear() - birth.getFullYear();
  const m = today.getMonth() - birth.getMonth();
  if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) {
    age--;
  }
  return age;
};


const CreatorPublicProfile: React.FC = () => {
  const { identifier } = useParams<{ identifier: string }>();
  const creatorId = identifier ? Number(identifier) : undefined;
  console.log("Creator ID:", creatorId);

  const { creator, loading } = useCreatorPublicProfile();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { setBalance: updateWallet } = useWallet();
  const [selectedItem, setSelectedItem] = useState<any | null>(null);
  const [authMessage, setAuthMessage] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'about' | 'media' | 'clips' | 'wishlist'>('about');
  const [mediaTab, setMediaTab] = useState<'Photos' | 'Videos' | 'Premium'>('Photos');
  const [mediaByTab, setMediaByTab] = useState<Record<string, any[]>>({
    Photos: [],
    Videos: [],
    Clips: [],
    Premium: []
  });
  const [isFollowing, setIsFollowing] = useState(false);
  const [followLoading, setFollowLoading] = useState(false);

  React.useEffect(() => {
    if (!creatorId || !user) return;
    creatorService.getFollowStatus(creatorId)
      .then(status => setIsFollowing(status.following))
      .catch(() => {});
  }, [creatorId, user]);

  React.useEffect(() => {
    if (!creatorId) return;

    apiClient.get(`/creators/${creatorId}/media`)
      .then(res => {
        const media = res.data;

        const grouped = {
          Photos: media.filter((m: any) => m.type === "PHOTO" && m.accessLevel !== "PREMIUM"),
          Videos: media.filter((m: any) => m.type === "VIDEO" && m.accessLevel !== "PREMIUM"),
          Clips: media.filter((m: any) => m.type === "CLIP" && m.accessLevel !== "PREMIUM"),
          Premium: media.filter((m: any) => m.accessLevel === "PREMIUM")
        };

        setMediaByTab(grouped);
      })
      .catch(err => {
        console.error('Failed to fetch media:', err);
      });
  }, [creatorId]);

  const unlockContent = async (id: string) => {
    const res = await apiClient.post(`/media/${id}/unlock`);
    return res.data;
  };

  const handleUnlock = async (mediaId: string) => {
    handleAction(async () => {
      try {
        console.log("Unlocking contentId:", mediaId);
        const response = await unlockContent(mediaId);
        if (response.unlocked) {
          setMediaByTab(prev => {
            const updated = { ...prev };
            Object.keys(updated).forEach(tab => {
              updated[tab] = updated[tab].map(m => 
                m.id === mediaId ? { ...m, unlocked: true } : m
              );
            });
            return updated;
          });
          updateWallet(response.remainingTokens);
          showToast('Content unlocked successfully!', 'success');
        }
      } catch (err: any) {
        console.error('Failed to unlock content:', err);
        const message = err.response?.data?.message || 'Failed to unlock content';
        showToast(message, 'error');
      }
    });
  };

  // Fallback values for consistent rendering during loading/null state
  const profile = creator?.profile;
  const displayName = profile?.displayName || (loading ? 'Loading...' : 'Creator');
  const username = profile?.username ? `@${profile.username}` : '';
  const bio = profile?.bio || '';
  const avatarUrl = profile?.avatarUrl;
  const bannerUrl = profile?.bannerUrl;
  const isOnline = Boolean(profile?.isOnline);
  const isAuthenticated = Boolean(user);
  const isNotFound = !loading && !creator;

  const handleAction = (callback: () => void) => {
    if (!isAuthenticated) {
      setAuthMessage('Please log in to continue');
      setTimeout(() => setAuthMessage(null), 3000);
      return;
    }
    callback();
  };

  if (loading) {
    return (
      <div className="min-h-screen flex flex-col items-center bg-[#08080A] pb-20">
        <div className="w-full h-[220px] bg-white/5 animate-pulse" />
        <div className="w-full max-w-[640px] flex flex-col items-center md:items-start text-center md:text-left -mt-12 relative z-10 animate-pulse px-6">
          <div className="w-32 h-32 rounded-full border-4 border-[#08080A] bg-white/5 mb-6" />
          <div className="h-3 w-20 bg-white/5 mb-4 rounded" />
          <div className="h-8 w-48 bg-white/5 mb-2 rounded" />
          <div className="h-4 w-32 bg-white/5 mb-8 rounded" />
          <div className="h-16 w-full max-w-md bg-white/5 mb-10 rounded-xl" />
          <div className="h-12 w-48 bg-white/5 rounded-full mb-16" />
          <div className="w-full border-t border-[#16161D] pt-12">
            <div className="h-4 w-24 bg-white/5 mb-8 rounded mx-auto" />
            <div className="space-y-4 max-w-md mx-auto">
              <div className="h-24 w-full bg-white/5 rounded-xl" />
              <div className="h-24 w-full bg-white/5 rounded-xl" />
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col items-center bg-[#08080A] text-zinc-100 pb-20">
      {/* Profile Banner */}
      {!isNotFound && (
        <div className="creator-banner w-full relative">
          {bannerUrl ? (
            <img 
              src={bannerUrl} 
              alt="Banner" 
              className="banner-img w-full h-[220px] object-cover"
            />
          ) : (
            <div className="banner-fallback w-full h-[220px] bg-gradient-to-r from-[#0f172a] to-[#1e293b]" />
          )}
        </div>
      )}

      <div className={`w-full max-w-[640px] flex flex-col items-center md:items-start text-center md:text-left px-6 ${isNotFound ? 'pt-20' : '-mt-12 relative z-10'}`}>
        
        {isNotFound ? (
          <div className="py-20 w-full text-center">
            <EmptyState 
              message="Profile not found or private."
              icon="👤"
              actionLabel="Explore Creators"
              onAction={() => navigate('/explore')}
            />
          </div>
        ) : (
          <>
            {/* Creator Header */}
            <div className="creator-header w-full flex flex-col items-center md:items-start mb-10">
              {/* Avatar Section */}
              <div className={`relative p-1 rounded-full transition-all duration-500 ${isOnline ? 'bg-gradient-to-tr from-emerald-500/20 to-emerald-400/20 ring-2 ring-emerald-500/50 shadow-[0_0_30px_rgba(16,185,129,0.2)] animate-pulse' : 'bg-zinc-800/50'}`}>
                <SafeAvatar 
                  src={avatarUrl} 
                  name={displayName} 
                  size={120} 
                  className="creator-avatar rounded-full border-4 border-[#08080A]"
                />
              </div>

              {/* Identity Section */}
              <div className="flex flex-col items-center md:items-start pt-4">
                <h1 className="text-3xl font-bold tracking-tight text-white mb-1">
                  {safeRender(displayName)}
                </h1>
                <p className="text-zinc-400 font-medium mb-4">
                  {safeRender(username)}
                </p>

                {/* Attribute Tags */}
                <div className="flex flex-wrap items-center justify-center md:justify-start gap-2 mb-6">
                  {profile?.gender && (
                    <span className="px-2.5 py-1 bg-zinc-900 border border-zinc-800 text-zinc-400 text-[10px] font-black uppercase tracking-widest rounded-full">
                      {safeRender(profile.gender)}
                    </span>
                  )}
                  {profile?.showLanguages && profile?.languages && (
                    <span className="px-2.5 py-1 bg-zinc-900 border border-zinc-800 text-zinc-400 text-[10px] font-black uppercase tracking-widest rounded-full">
                      {safeRender(profile.languages)}
                    </span>
                  )}
                  {profile?.showLocation && profile?.location && (
                    <span className="px-2.5 py-1 bg-zinc-900 border border-zinc-800 text-zinc-400 text-[10px] font-black uppercase tracking-widest rounded-full">
                      {safeRender(profile.location)}
                    </span>
                  )}
                  {profile?.showBodyType && profile?.bodyType && (
                    <span className="px-2.5 py-1 bg-zinc-900 border border-zinc-800 text-zinc-400 text-[10px] font-black uppercase tracking-widest rounded-full">
                      {safeRender(profile.bodyType)}
                    </span>
                  )}
                  {profile?.interestedIn && (
                    <span className="px-2.5 py-1 bg-zinc-900 border border-zinc-800 text-zinc-400 text-[10px] font-black uppercase tracking-widest rounded-full">
                      {safeRender(profile.interestedIn)}
                    </span>
                  )}
                </div>
                
                <div className="flex flex-col items-center md:items-start gap-2.5">
                  {/* Live Indicator */}
                  <div className="flex items-center gap-2">
                    <span className={`w-2 h-2 rounded-full ${isOnline ? 'bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,1)] animate-pulse' : 'bg-zinc-700'}`} />
                    <span className={`text-[10px] font-black uppercase tracking-[0.2em] ${isOnline ? 'text-emerald-500' : 'text-zinc-500'}`}>
                      {isOnline ? 'LIVE NOW' : 'OFFLINE'}
                    </span>
                  </div>
                  
                  {/* Creator Stats */}
                  <div className="creator-stats flex flex-wrap items-center justify-center md:justify-start gap-3 mt-1">
                    <div className="flex items-center gap-1.5 bg-zinc-900/50 border border-zinc-800/50 px-2.5 py-1 rounded-full text-zinc-400 font-bold text-[10px] uppercase tracking-widest">
                      <span>⭐ {safeRender(profile?.rating)} rating</span>
                    </div>
                    <div className="flex items-center gap-1.5 bg-zinc-900/50 border border-zinc-800/50 px-2.5 py-1 rounded-full text-zinc-400 font-bold text-[10px] uppercase tracking-widest">
                      <span>👥 {safeRender(profile?.followersCount)} followers</span>
                    </div>
                    <div className="flex items-center gap-1.5 bg-zinc-900/50 border border-zinc-800/50 px-2.5 py-1 rounded-full text-zinc-400 font-bold text-[10px] uppercase tracking-widest">
                      <span>🎥 {safeRender(profile?.streamCount)} streams</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Bio Section */}
            <div className="mb-10 max-w-md">
              {bio ? (
                <p className="text-zinc-300 leading-relaxed">
                  {safeRender(bio)}
                </p>
              ) : (
                <p className="text-emerald-400 font-medium flex items-center gap-2 bg-emerald-500/5 border border-emerald-500/10 px-4 py-2 rounded-lg w-fit text-sm">
                  ✨ New creator — be one of the first supporters!
                </p>
              )}
            </div>

            {/* CTA Section */}
            <div className="mb-16 w-full flex flex-col items-center md:items-start gap-4">
              <div className="flex flex-wrap items-center justify-center md:justify-start gap-3 mt-2">
                <button
                  onClick={() => navigate(`/creators/${creatorId}/live`)}
                  className="px-8 py-2.5 rounded-full font-bold bg-white text-black hover:bg-zinc-200 transition-all active:scale-95 text-sm"
                >
                  Watch Live
                </button>
                <button
                  onClick={() => handleAction(() => console.log('Send tip clicked'))}
                  disabled={!isOnline}
                  className={`px-8 py-2.5 rounded-full font-bold transition-all active:scale-95 text-sm border ${
                    isOnline 
                      ? "bg-zinc-900 text-white border-zinc-800 hover:bg-zinc-800" 
                      : "bg-zinc-900/50 text-zinc-700 border-zinc-900 cursor-not-allowed"
                  }`}
                >
                  Send Tip
                </button>
                <button
                  onClick={() => handleAction(async () => {
                    if (!creatorId || followLoading) return;
                    setFollowLoading(true);
                    try {
                      if (isFollowing) {
                        const status = await creatorService.unfollowCreator(creatorId);
                        setIsFollowing(status.following);
                      } else {
                        const status = await creatorService.followCreator(creatorId);
                        setIsFollowing(status.following);
                      }
                    } catch (err: any) {
                      console.error('Follow action failed:', err);
                      showToast(err.response?.data?.message || 'Follow action failed', 'error');
                    } finally {
                      setFollowLoading(false);
                    }
                  })}
                  disabled={followLoading}
                  className={`px-8 py-2.5 rounded-full font-bold transition-all active:scale-95 text-sm border ${
                    isFollowing
                      ? 'bg-white text-black border-white hover:bg-zinc-200'
                      : 'bg-zinc-900 text-white border-zinc-800 hover:bg-zinc-800'
                  } ${followLoading ? 'opacity-50 cursor-not-allowed' : ''}`}
                >
                  {followLoading ? '...' : isFollowing ? 'Following' : 'Follow'}
                </button>
              </div>

              {authMessage && (
                <p className="text-[10px] font-bold uppercase tracking-widest text-zinc-500 animate-pulse">
                  {authMessage}
                </p>
              )}
            </div>

            {/* Profile Tabs */}
            <div className="w-full flex justify-center mb-12 border-b border-zinc-800/50">
               <div className="flex gap-4 md:gap-8">
                  {[
                    { id: 'about', label: 'About' },
                    { id: 'media', label: 'Media' },
                    { id: 'clips', label: 'Clips' },
                    { id: 'wishlist', label: 'Wishlist' }
                  ].map((tab) => (
                    <button
                      key={tab.id}
                      onClick={() => setActiveTab(tab.id as any)}
                      className={`pb-4 px-2 text-[10px] font-black uppercase tracking-[0.2em] transition-all relative ${
                        activeTab === tab.id ? 'text-white' : 'text-zinc-500 hover:text-zinc-300'
                      }`}
                    >
                      {tab.label}
                      {activeTab === tab.id && (
                        <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-white rounded-full" />
                      )}
                    </button>
                  ))}
               </div>
            </div>

            {/* About Tab Content */}
            {activeTab === 'about' && (
              <>
                {/* About Me Section */}
                <div className="w-full bg-zinc-900/50 border border-zinc-800 rounded-xl p-8 mb-16">
                  <h2 className="text-xs font-bold uppercase tracking-[0.2em] text-zinc-500 mb-8 flex items-center gap-2">
                    <span className="w-4 h-px bg-zinc-800" />
                    About Me
                  </h2>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-y-6 gap-x-12">
                    {/* Real Name */}
                    <div className="flex items-start gap-4">
                      <span className="text-xl mt-0.5">👤</span>
                      <div className="flex flex-col gap-1">
                        <span className="text-[10px] font-black uppercase tracking-widest text-zinc-500">Real Name</span>
                        <span className="text-zinc-200 font-bold text-sm">{safeRender(profile?.realName || displayName)}</span>
                      </div>
                    </div>

                    {/* Followers */}
                    <div className="flex items-start gap-4">
                      <span className="text-xl mt-0.5">👥</span>
                      <div className="flex flex-col gap-1">
                        <span className="text-[10px] font-black uppercase tracking-widest text-zinc-500">Followers</span>
                        <span className="text-zinc-200 font-bold text-sm">{safeRender(profile?.followersCount)}</span>
                      </div>
                    </div>

                    {/* Age */}
                    {profile?.showAge && calculateAge(profile?.birthDate) !== null && (
                      <div className="flex items-start gap-4">
                        <span className="text-xl mt-0.5">🎂</span>
                        <div className="flex flex-col gap-1">
                          <span className="text-[10px] font-black uppercase tracking-widest text-zinc-500">Age</span>
                          <span className="text-zinc-200 font-bold text-sm">{safeRender(calculateAge(profile?.birthDate))}</span>
                        </div>
                      </div>
                    )}

                    {/* Gender / I Am */}
                    {profile?.gender && (
                      <div className="flex items-start gap-4">
                        <span className="text-xl mt-0.5">
                          {profile.gender.toLowerCase() === 'female' ? '♀️' : 
                           profile.gender.toLowerCase() === 'male' ? '♂️' : 
                           profile.gender.toLowerCase() === 'couple' ? '👫' : '👤'}
                        </span>
                        <div className="flex flex-col gap-1">
                          <span className="text-[10px] font-black uppercase tracking-widest text-zinc-500">Gender</span>
                          <span className="text-zinc-200 font-bold text-sm">{safeRender(profile.gender)}</span>
                        </div>
                      </div>
                    )}

                    {/* Interested In */}
                    {profile?.interestedIn && (
                      <div className="flex items-start gap-4">
                        <span className="text-xl mt-0.5">🌈</span>
                        <div className="flex flex-col gap-1">
                          <span className="text-[10px] font-black uppercase tracking-widest text-zinc-500">Interested In</span>
                          <span className="text-zinc-200 font-bold text-sm">{safeRender(profile.interestedIn)}</span>
                        </div>
                      </div>
                    )}

                    {/* Location */}
                    {profile?.showLocation && profile?.location && (
                      <div className="flex items-start gap-4">
                        <span className="text-xl mt-0.5">📍</span>
                        <div className="flex flex-col gap-1">
                          <span className="text-[10px] font-black uppercase tracking-widest text-zinc-500">Location</span>
                          <span className="text-zinc-200 font-bold text-sm">{safeRender(profile.location)}</span>
                        </div>
                      </div>
                    )}

                    {/* Languages */}
                    {profile?.showLanguages && profile?.languages && (
                      <div className="flex items-start gap-4">
                        <span className="text-xl mt-0.5">🗣️</span>
                        <div className="flex flex-col gap-1">
                          <span className="text-[10px] font-black uppercase tracking-widest text-zinc-500">Languages</span>
                          <span className="text-zinc-200 font-bold text-sm">{safeRender(profile.languages)}</span>
                        </div>
                      </div>
                    )}

                    {/* Body Type */}
                    {profile?.showBodyType && profile?.bodyType && (
                      <div className="flex items-start gap-4">
                        <span className="text-xl mt-0.5">📏</span>
                        <div className="flex flex-col gap-1">
                          <span className="text-[10px] font-black uppercase tracking-widest text-zinc-500">Body Type</span>
                          <span className="text-zinc-200 font-bold text-sm">{safeRender(profile.bodyType)}</span>
                        </div>
                      </div>
                    )}
                  </div>
                </div>

                {/* Physical Attributes Section */}
                <div className="w-full bg-zinc-900/50 border border-zinc-800 rounded-xl p-8 mb-16">
                  <h2 className="text-xs font-bold uppercase tracking-[0.2em] text-zinc-500 mb-8 flex items-center gap-2">
                    <span className="w-4 h-px bg-zinc-800" />
                    Physical Attributes
                  </h2>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-y-6 gap-x-12">
                    {/* Ethnicity */}
                    {profile?.showEthnicity && profile?.ethnicity && (
                      <div className="flex items-start gap-4">
                        <span className="text-xl mt-0.5">🌍</span>
                        <div className="flex flex-col gap-1">
                          <span className="text-[10px] font-black uppercase tracking-widest text-zinc-500">Ethnicity</span>
                          <span className="text-zinc-200 font-bold text-sm">{safeRender(profile.ethnicity)}</span>
                        </div>
                      </div>
                    )}

                    {/* Eye Color */}
                    {profile?.eyeColor && (
                      <div className="flex items-start gap-4">
                        <span className="text-xl mt-0.5">👁️</span>
                        <div className="flex flex-col gap-1">
                          <span className="text-[10px] font-black uppercase tracking-widest text-zinc-500">Eye Color</span>
                          <span className="text-zinc-200 font-bold text-sm">{safeRender(profile.eyeColor)}</span>
                        </div>
                      </div>
                    )}

                    {/* Hair Color */}
                    {profile?.hairColor && (
                      <div className="flex items-start gap-4">
                        <span className="text-xl mt-0.5">✨</span>
                        <div className="flex flex-col gap-1">
                          <span className="text-[10px] font-black uppercase tracking-widest text-zinc-500">Hair Color</span>
                          <span className="text-zinc-200 font-bold text-sm">{safeRender(profile.hairColor)}</span>
                        </div>
                      </div>
                    )}

                    {/* Height */}
                    {profile?.showHeightWeight && profile?.heightCm && (
                      <div className="flex items-start gap-4">
                        <span className="text-xl mt-0.5">📏</span>
                        <div className="flex flex-col gap-1">
                          <span className="text-[10px] font-black uppercase tracking-widest text-zinc-500">Height</span>
                          <span className="text-zinc-200 font-bold text-sm">{safeRender(profile.heightCm)} cm</span>
                        </div>
                      </div>
                    )}

                    {/* Weight */}
                    {profile?.showHeightWeight && profile?.weightKg && (
                      <div className="flex items-start gap-4">
                        <span className="text-xl mt-0.5">⚖️</span>
                        <div className="flex flex-col gap-1">
                          <span className="text-[10px] font-black uppercase tracking-widest text-zinc-500">Weight</span>
                          <span className="text-zinc-200 font-bold text-sm">{safeRender(profile.weightKg)} kg</span>
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              </>
            )}

            {/* Wishlist Tab Content */}
            {activeTab === 'wishlist' && (
              <div className="w-full mb-16">
                <h2 className="text-xs font-bold uppercase tracking-[0.2em] text-zinc-500 mb-6 flex items-center gap-2">
                  <span className="w-4 h-px bg-zinc-800" />
                  Wishlist & Socials
                </h2>
                <div className="flex flex-wrap gap-4">
                  {profile?.throneUrl && (
                    <a 
                      href={profile.throneUrl} 
                      target="_blank" 
                      rel="noopener noreferrer"
                      className="flex-1 min-w-[140px] flex items-center justify-center gap-3 px-6 py-4 bg-zinc-900/50 border border-zinc-800 rounded-xl hover:bg-zinc-800/50 transition-all group"
                    >
                      <span className="text-xl group-hover:scale-110 transition-transform">👑</span>
                      <span className="text-sm font-bold text-zinc-300">Throne</span>
                    </a>
                  )}
                  {profile?.onlyfansUrl && (
                    <a 
                      href={profile.onlyfansUrl} 
                      target="_blank" 
                      rel="noopener noreferrer"
                      className="flex-1 min-w-[140px] flex items-center justify-center gap-3 px-6 py-4 bg-zinc-900/50 border border-zinc-800 rounded-xl hover:bg-zinc-800/50 transition-all group"
                    >
                      <span className="text-xl group-hover:scale-110 transition-transform">💙</span>
                      <span className="text-sm font-bold text-zinc-300">OnlyFans</span>
                    </a>
                  )}
                  {profile?.wishlistUrl && (
                    <a 
                      href={profile.wishlistUrl} 
                      target="_blank" 
                      rel="noopener noreferrer"
                      className="flex-1 min-w-[140px] flex items-center justify-center gap-3 px-6 py-4 bg-zinc-900/50 border border-zinc-800 rounded-xl hover:bg-zinc-800/50 transition-all group"
                    >
                      <span className="text-xl group-hover:scale-110 transition-transform">🎁</span>
                      <span className="text-sm font-bold text-zinc-300">Wish List</span>
                    </a>
                  )}
                  {profile?.twitterUrl && (
                    <a 
                      href={profile.twitterUrl} 
                      target="_blank" 
                      rel="noopener noreferrer"
                      className="flex-1 min-w-[140px] flex items-center justify-center gap-3 px-6 py-4 bg-zinc-900/50 border border-zinc-800 rounded-xl hover:bg-zinc-800/50 transition-all group"
                    >
                      <span className="text-xl group-hover:scale-110 transition-transform">🐦</span>
                      <span className="text-sm font-bold text-zinc-300">Twitter</span>
                    </a>
                  )}
                  {profile?.instagramUrl && (
                    <a 
                      href={profile.instagramUrl} 
                      target="_blank" 
                      rel="noopener noreferrer"
                      className="flex-1 min-w-[140px] flex items-center justify-center gap-3 px-6 py-4 bg-zinc-900/50 border border-zinc-800 rounded-xl hover:bg-zinc-800/50 transition-all group"
                    >
                      <span className="text-xl group-hover:scale-110 transition-transform">📸</span>
                      <span className="text-sm font-bold text-zinc-300">Instagram</span>
                    </a>
                  )}
                  {!(profile?.throneUrl || profile?.onlyfansUrl || profile?.wishlistUrl || profile?.twitterUrl || profile?.instagramUrl) && (
                    <div className="w-full text-center py-12 bg-zinc-900/20 rounded-xl border border-zinc-800/50 text-zinc-500 text-sm italic">
                      No social links or wishlists added yet.
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Media & Clips Tabs Content */}
            {(activeTab === 'media' || activeTab === 'clips') && (
              <div className="w-full mb-20">
                <div className="flex justify-center mb-12">
                  <div className="flex p-1 bg-zinc-900/50 rounded-full border border-zinc-800/50 backdrop-blur-sm">
                    {activeTab === 'media' ? (
                      ['Photos', 'Videos', 'Premium'].map((tab) => (
                        <button
                          key={tab}
                          onClick={() => setMediaTab(tab as any)}
                          className={`
                            px-6 py-2 rounded-full text-[10px] font-black uppercase tracking-[0.2em] transition-all duration-300
                            ${mediaTab === tab 
                              ? 'bg-white text-black shadow-lg' 
                              : 'text-zinc-500 hover:text-zinc-300 hover:bg-white/5'}
                          `}
                        >
                          {tab}
                        </button>
                      ))
                    ) : (
                      <div className="px-6 py-2 rounded-full text-[10px] font-black uppercase tracking-[0.2em] bg-white text-black shadow-lg">
                        CLIPS
                      </div>
                    )}
                  </div>
                </div>

                <div className="w-full">
                  {mediaByTab[activeTab === 'media' ? mediaTab : 'Clips'].length > 0 ? (
                    <CreatorMediaTab 
                      media={mediaByTab[activeTab === 'media' ? mediaTab : 'Clips'].map(item => ({
                        id: item.id,
                        thumbnail: (item.type === "VIDEO" || item.type === "CLIP") ? item.thumbnailUrl : (item.mediaUrl || item.thumbnailUrl),
                        isPremium: item.accessLevel === "PREMIUM",
                        isLocked: item.accessLevel === "PREMIUM" && item.unlocked !== true,
                        unlockPrice: item.unlockPriceTokens,
                        title: item.title,
                        mediaUrl: item.mediaUrl,
                        type: item.type
                      }))}
                      onItemClick={(item) => setSelectedItem(item)}
                      onUnlock={handleUnlock}
                    />
                  ) : (
                    <div className="text-center py-20 bg-zinc-900/20 rounded-3xl border border-zinc-800/50">
                      <div className="text-4xl mb-6 opacity-20">
                        {activeTab === 'clips' ? '✂️' : mediaTab === 'Photos' ? '🖼️' : mediaTab === 'Videos' ? '🎥' : '💎'}
                      </div>
                      <h3 className="text-lg font-bold text-white mb-2">No {activeTab === 'clips' ? 'clips' : mediaTab.toLowerCase()} uploaded yet.</h3>
                      <p className="text-zinc-500 text-sm font-medium">Be the first to support this creator.</p>
                    </div>
                  )}
                </div>
              </div>
            )}
            

            {/* Support Section */}
            <div className="w-full bg-gradient-to-br from-zinc-900/80 to-zinc-900/40 border border-zinc-800 rounded-2xl p-8 mb-16 text-center">
              <h2 className="text-xl font-bold text-white mb-2 flex items-center justify-center gap-2">
                💖 Support {String(profile?.displayName || 'Esther')}
              </h2>
              <p className="text-zinc-500 text-sm mb-8">
                Your support helps me keep creating content you love.
              </p>
              
              <div className="bg-black/20 rounded-xl p-6 mb-8 border border-white/5">
                <div className="text-[10px] font-black uppercase tracking-[0.2em] text-zinc-500 mb-2">Suggested Tip</div>
                <div className="text-4xl font-black text-white mb-1">€8.00</div>
                <div className="text-xs font-bold text-zinc-500">Most fans tip between €6–€12</div>
              </div>

              <button
                onClick={() => handleAction(() => console.log('Send tip clicked'))}
                className="w-full py-4 rounded-xl font-bold bg-white text-black hover:bg-zinc-200 transition-all active:scale-[0.98] shadow-[0_0_20px_rgba(255,255,255,0.1)]"
              >
                Send Tip
              </button>
            </div>

          </>
        )}

        {selectedItem && (
           <div className="fixed inset-0 bg-black/80 flex items-center justify-center z-50" onClick={() => setSelectedItem(null)}>
              <div className="relative max-w-4xl w-full p-4" onClick={e => e.stopPropagation()}>
                 <button
                    className="absolute top-4 right-4 text-white text-3xl z-10"
                    onClick={() => setSelectedItem(null)}
                 >
                    ✕
                 </button>
                 {(selectedItem.type === 'VIDEO' || selectedItem.type === 'CLIP') ? (
                    <video 
                      src={selectedItem.mediaUrl} 
                      controls 
                      autoPlay 
                      className="w-full max-h-[90vh] object-contain rounded-lg"
                      poster={selectedItem.thumbnailUrl}
                    />
                 ) : (
                    <img
                       src={selectedItem.mediaUrl}
                       alt={selectedItem.title}
                       className="w-full max-h-[90vh] object-contain rounded-lg"
                    />
                 )}
              </div>
           </div>
        )}

      </div>
    </div>
  );
};

export default CreatorPublicProfile;
