import React, { useEffect, useState, useMemo, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import SEO from '../components/SEO';
import creatorService from '../api/creatorService';
import { ICreator } from '../domain/creator/ICreator';
import CreatorCard from '../components/creator/CreatorCard';
import { COUNTRIES } from '../data/countries';
import { getCountryLabel } from '../data/countries';
import { useWs, useThumbnailCacheBuster } from '../ws/WsContext';
import { usePresence, useTrackPresence } from '../ws/PresenceContext';
import { useAuth } from '../auth/useAuth';

const ExploreCreatorsPage: React.FC = () => {
  const { subscribe } = useWs();
  const { presenceMap } = usePresence();
  const { isAuthenticated } = useAuth();
  const thumbnailCacheBuster = useThumbnailCacheBuster();
  const navigate = useNavigate();
  const [creators, setCreators] = useState<ICreator[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [category, setCategory] = useState("featured");
  const [searchQuery, setSearchQuery] = useState("");
  const [country, setCountry] = useState("all");
  const [liveOnly, setLiveOnly] = useState(false);
  const [paidFilter, setPaidFilter] = useState("all");
  const [sortOption, setSortOption] = useState("relevance");
  const [bodyType, setBodyType] = useState("all");
  const [hairColor, setHairColor] = useState("all");
  const [eyeColor, setEyeColor] = useState("all");
  const [ethnicity, setEthnicity] = useState("all");
  const [interestedIn, setInterestedIn] = useState("all");
  const [language, setLanguage] = useState("all");
  const [showAdvancedFilters, setShowAdvancedFilters] = useState(false);

  // Discovery state
  const [userCountry, setUserCountry] = useState<string>('');
  const [topCreators, setTopCreators] = useState<ICreator[]>([]);
  const [liveByCountry, setLiveByCountry] = useState<ICreator[]>([]);
  const [nearbyCreators, setNearbyCreators] = useState<ICreator[]>([]);

  const [debouncedSearchQuery, setDebouncedSearchQuery] = useState(searchQuery);

  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedSearchQuery(searchQuery);
    }, 500);
    return () => clearTimeout(handler);
  }, [searchQuery]);

  // Subscribe to global stream status updates (STREAM_STARTED / STREAM_ENDED)
  useEffect(() => {
    if (!isAuthenticated) return;

    const unsub = subscribe(
      '/exchange/amq.topic/streams.status',
      (message) => {
        try {
          const data = JSON.parse(message.body);
          const payload = data.payload;
          if (!payload?.creatorUserId) return;

          const updateCreatorList = (prev: ICreator[]) =>
            prev.map(c =>
              c.userId === payload.creatorUserId
                ? { ...c, isLive: payload.isLive, viewerCount: payload.viewerCount ?? c.viewerCount }
                : c
            );

          setCreators(updateCreatorList);
          setTopCreators(updateCreatorList);
          setLiveByCountry(updateCreatorList);
          setNearbyCreators(updateCreatorList);
        } catch (err) {
          console.error('Failed to parse stream status event:', err);
        }
      }
    );

    return () => { if (typeof unsub === 'function') unsub(); };
  }, [isAuthenticated, subscribe]);

  // Detect user country from browser locale
  useEffect(() => {
    try {
      const locale = Intl.DateTimeFormat().resolvedOptions().locale || navigator.language || '';
      const parts = locale.split('-');
      const code = parts.length > 1 ? parts[parts.length - 1].toUpperCase() : '';
      if (code && code.length === 2) {
        setUserCountry(code);
      }
    } catch {
      // Fallback: no country detected
    }
  }, []);

  // Fetch discovery sections
  useEffect(() => {
    const fetchDiscovery = async () => {
      const [top, live, nearby] = await Promise.all([
        userCountry ? creatorService.getTopCreators(userCountry, 10) : creatorService.getTopCreators(undefined, 10),
        userCountry ? creatorService.getLiveCreators(userCountry, 10) : creatorService.getLiveCreators(undefined, 10),
        userCountry ? creatorService.getTopCreators(userCountry, 10) : Promise.resolve([]),
      ]);
      setTopCreators(top);
      setLiveByCountry(live);
      setNearbyCreators(nearby);
    };
    fetchDiscovery();
  }, [userCountry]);

  // Pagination states
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [isFetchingNextPage, setIsFetchingNextPage] = useState(false);

  // Use a ref for the observer to prevent duplicate fetches
  const observer = useRef<IntersectionObserver | null>(null);

  // Use a ref for the AbortController to cancel previous requests
  const abortControllerRef = useRef<AbortController | null>(null);

  const fetchCreators = async (pageNum: number, isInitial = false) => {
    // Abort previous request if it exists
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    const controller = new AbortController();
    abortControllerRef.current = controller;

    try {
      if (isInitial) {
        setLoading(true);
      } else {
        setIsFetchingNextPage(true);
      }
      setError(null);

      const data = await creatorService.getPublicCreators(category, country, pageNum, 48, debouncedSearchQuery, controller.signal, liveOnly || undefined, paidFilter, sortOption, bodyType !== 'all' ? bodyType : undefined, hairColor !== 'all' ? hairColor : undefined, eyeColor !== 'all' ? eyeColor : undefined, ethnicity !== 'all' ? ethnicity : undefined, interestedIn !== 'all' ? interestedIn : undefined, language !== 'all' ? language : undefined);
      
      // If we got aborted during the await, skip updating state
      if (controller.signal.aborted) return;

      if (isInitial) {
        setCreators(data.content);
      } else {
        setCreators((prev) => {
          const merged = new Map<number, ICreator>();
          prev.forEach((c) => merged.set(c.userId, c));
          data.content.forEach((c) => merged.set(c.userId, c));
          return Array.from(merged.values());
        });
      }
      
      setHasMore(data.hasNext);
    } catch (err) {
      if (axios.isCancel(err) || (err && typeof err === 'object' && 'name' in err && err.name === 'AbortError')) {
        // Aborted request, do not update state or log as error
        return;
      }
      console.error('Failed to fetch creators', err);
      setError('Failed to load creators. Please try again.');
    } finally {
      // Only reset loading states if this request wasn't aborted
      if (!controller.signal.aborted) {
        setLoading(false);
        setIsFetchingNextPage(false);
      }
    }
  };

  // Reset pagination when filters change
  useEffect(() => {
    // Disconnect active observer to prevent orphan triggers during reset
    if (observer.current) {
      observer.current.disconnect();
    }
    
    setPage(0);
    setHasMore(true);
    fetchCreators(0, true);
    
    // Cleanup AbortController on unmount
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, [category, country, debouncedSearchQuery, liveOnly, paidFilter, sortOption, bodyType, hairColor, eyeColor, ethnicity, interestedIn, language]);

  // Fetch next page when page changes
  useEffect(() => {
    if (page > 0) {
      fetchCreators(page, false);
    }
  }, [page]);

  // Track creators for real-time presence updates and cleanup management
  const creatorIds = useMemo(() => creators.map((c) => c.userId), [creators]);
  useTrackPresence(creatorIds);

  // Observer callback for infinite scroll
  const lastCreatorElementRef = useCallback((node: HTMLDivElement | null) => {
    if (observer.current) observer.current.disconnect();

    if (loading || isFetchingNextPage || !hasMore) return;

    observer.current = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting && hasMore && !loading && !isFetchingNextPage) {
        if (observer.current) observer.current.disconnect();
        // Set fetching state immediately to prevent duplicate page increments
        // before the page change useEffect kicks in
        setIsFetchingNextPage(true);
        setPage(prev => {
          if (!hasMore) return prev;
          return prev + 1;
        });
      }
    }, { rootMargin: '1000px' });

    if (node) observer.current.observe(node);
  }, [loading, isFetchingNextPage, hasMore]);

  const allSortedCreators = useMemo(() => {
    return creators
      .map((c) => {
        let updatedCreator = c;
        const liveStatus = presenceMap[c.userId];
        
        if (liveStatus) {
          const isOnline = liveStatus.online;
          const isLive = liveStatus.availability === 'LIVE';
          const viewerCount = liveStatus.viewerCount;

          // Sync online/live status from presenceMap
          if (c.isOnline !== isOnline || c.online !== isOnline || c.isLive !== isLive || c.viewerCount !== viewerCount) {
            updatedCreator = {
              ...c,
              isOnline,
              online: isOnline,
              isLive,
              viewerCount
            };
          }
        }

        // Apply global cache buster to live thumbnails to ensure they refresh periodically
        if (updatedCreator.activeStream?.thumbnailUrl) {
          const baseUrl = updatedCreator.activeStream.thumbnailUrl.split('?')[0];
          const newThumbnailUrl = `${baseUrl}?t=${thumbnailCacheBuster}`;
          
          if (updatedCreator.activeStream.thumbnailUrl !== newThumbnailUrl) {
            updatedCreator = {
              ...updatedCreator,
              activeStream: {
                ...updatedCreator.activeStream,
                thumbnailUrl: newThumbnailUrl
              }
            };
          }
        }

        return updatedCreator;
      })
      .sort((a, b) => {
        const getPriority = (c: ICreator) => {
          if (c.isLive) return 1;
          if (c.isOnline) return 2;
          return 3;
        };

        const priorityA = getPriority(a);
        const priorityB = getPriority(b);

        if (priorityA !== priorityB) {
          return priorityA - priorityB;
        }

        // If both are LIVE, sort by viewerCount descending
        if (priorityA === 1) {
          return (b.viewerCount || 0) - (a.viewerCount || 0);
        }

        return 0;
      });
  }, [creators, presenceMap, thumbnailCacheBuster]);

  const { liveCreators, nonLiveCreators } = useMemo(() => {
    const live: ICreator[] = [];
    const nonLive: ICreator[] = [];

    for (const creator of allSortedCreators) {
      if (creator.isLive) {
        live.push(creator);
      } else {
        nonLive.push(creator);
      }
    }

    return { liveCreators: live, nonLiveCreators: nonLive };
  }, [allSortedCreators]);

  const handleCreatorClick = useCallback((creator: ICreator) => {
    navigate(`/creators/${creator.userId}`);
  }, [navigate]);

  const initialSkeletons = useMemo(() => Array.from({ length: 24 }), []);
  const nextSkeletons = useMemo(() => Array.from({ length: 8 }), []);

  return (
    <div className="min-h-screen bg-[#08080A] text-zinc-100 px-4 py-8 sm:px-6 lg:px-8">
      <SEO 
        title="Explore Creators" 
        description="Discover talented creators on Livora and find content you love."
        canonical="/explore"
      />
      
      <div className="max-w-7xl mx-auto">
        <header className="mb-10 text-center">
          <h1 className="text-4xl sm:text-5xl font-extrabold text-white mb-4 tracking-tight">
            Explore Creators
          </h1>
          <p className="text-zinc-500 text-lg max-w-2xl mx-auto leading-relaxed">
            Discover the most talented creators in our community.
          </p>
        </header>

        {/* Filters */}
        <div className="mb-8">
          <div className="flex flex-wrap gap-3 mb-6">
            {["featured","women","men","couples","trans"].map((cat) => (
              <button
                key={cat}
                onClick={() => setCategory(cat)}
                className={`
                  px-5 py-2 rounded-full text-sm font-semibold transition-all duration-300 capitalize
                  ${category === cat
                    ? "bg-indigo-600 text-white shadow-lg shadow-indigo-500/30"
                    : "bg-white/5 text-white/70 hover:bg-white/10 hover:text-white"}
                `}
              >
                {cat}
              </button>
            ))}
          </div>

          <div className="flex flex-col md:flex-row gap-4 mb-4">
            {/* Search */}
            <input
              type="text"
              placeholder="Search creators..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="premium-input md:w-1/2"
            />

            {/* Country */}
            <select
              value={country}
              onChange={(e) => setCountry(e.target.value)}
              className="premium-select md:w-1/4"
            >
              <option value="all">All Countries</option>
              {COUNTRIES.map((c) => (
                <option key={c.code} value={c.code}>
                  {c.label}
                </option>
              ))}
            </select>
          </div>

          {/* Phase 2: Filters & Sort */}
          <div className="flex flex-wrap items-center gap-3">
            {/* Live Now toggle */}
            <button
              onClick={() => setLiveOnly(prev => !prev)}
              className={`px-4 py-2 rounded-full text-sm font-semibold transition-all duration-300 flex items-center gap-1.5 ${
                liveOnly
                  ? 'bg-red-600 text-white shadow-lg shadow-red-500/30'
                  : 'bg-white/5 text-white/70 hover:bg-white/10 hover:text-white'
              }`}
            >
              <span className={`w-2 h-2 rounded-full ${liveOnly ? 'bg-white animate-pulse' : 'bg-red-500'}`} />
              Live Now
            </button>

            {/* Paid filter */}
            <select
              value={paidFilter}
              onChange={(e) => setPaidFilter(e.target.value)}
              className="premium-select text-sm py-2 px-3 rounded-full bg-white/5 border-0 text-white/80"
            >
              <option value="all">All Streams</option>
              <option value="free">Free Only</option>
              <option value="paid">Paid Only</option>
            </select>

            {/* Sort dropdown */}
            <select
              value={sortOption}
              onChange={(e) => setSortOption(e.target.value)}
              className="premium-select text-sm py-2 px-3 rounded-full bg-white/5 border-0 text-white/80"
            >
              <option value="relevance">Sort: Relevance</option>
              <option value="viewers">Sort: Most Viewers</option>
              <option value="newest">Sort: Newest Live</option>
              <option value="alphabetical">Sort: A–Z</option>
            </select>
          </div>

          {/* Advanced Appearance Filters */}
          <div className="mt-4">
            <button
              onClick={() => setShowAdvancedFilters(prev => !prev)}
              className={`px-4 py-2 rounded-full text-sm font-semibold transition-all duration-300 flex items-center gap-1.5 ${
                [bodyType, hairColor, eyeColor, ethnicity, interestedIn, language].some(v => v !== 'all')
                  ? 'bg-purple-600 text-white shadow-lg shadow-purple-500/30'
                  : 'bg-white/5 text-white/70 hover:bg-white/10 hover:text-white'
              }`}
            >
              <span className="text-base">⚙</span>
              More Filters
              {(() => {
                const count = [bodyType, hairColor, eyeColor, ethnicity, interestedIn, language].filter(v => v !== 'all').length;
                return count > 0 ? <span className="ml-1 bg-white/20 text-white text-xs rounded-full px-1.5 py-0.5">{count}</span> : null;
              })()}
              <span className={`ml-1 transition-transform duration-200 ${showAdvancedFilters ? 'rotate-180' : ''}`}>▾</span>
            </button>

            {showAdvancedFilters && (
              <div className="mt-3 flex flex-wrap gap-3 p-4 rounded-xl bg-white/[0.03] border border-white/[0.06]">
                <select
                  value={bodyType}
                  onChange={(e) => setBodyType(e.target.value)}
                  className="premium-select text-sm py-2 px-3 rounded-full bg-white/5 border-0 text-white/80"
                >
                  <option value="all">Body Type: All</option>
                  <option value="Slim">Slim</option>
                  <option value="Athletic">Athletic</option>
                  <option value="Average">Average</option>
                  <option value="Curvy">Curvy</option>
                  <option value="Muscular">Muscular</option>
                  <option value="Plus Size">Plus Size</option>
                </select>

                <select
                  value={hairColor}
                  onChange={(e) => setHairColor(e.target.value)}
                  className="premium-select text-sm py-2 px-3 rounded-full bg-white/5 border-0 text-white/80"
                >
                  <option value="all">Hair Color: All</option>
                  <option value="Blonde">Blonde</option>
                  <option value="Brown">Brown</option>
                  <option value="Black">Black</option>
                  <option value="Red">Red</option>
                  <option value="Grey">Grey</option>
                  <option value="Colored">Colored</option>
                </select>

                <select
                  value={eyeColor}
                  onChange={(e) => setEyeColor(e.target.value)}
                  className="premium-select text-sm py-2 px-3 rounded-full bg-white/5 border-0 text-white/80"
                >
                  <option value="all">Eye Color: All</option>
                  <option value="Blue">Blue</option>
                  <option value="Brown">Brown</option>
                  <option value="Green">Green</option>
                  <option value="Hazel">Hazel</option>
                  <option value="Grey">Grey</option>
                </select>

                <select
                  value={ethnicity}
                  onChange={(e) => setEthnicity(e.target.value)}
                  className="premium-select text-sm py-2 px-3 rounded-full bg-white/5 border-0 text-white/80"
                >
                  <option value="all">Ethnicity: All</option>
                  <option value="Caucasian">Caucasian</option>
                  <option value="Latina">Latina</option>
                  <option value="Asian">Asian</option>
                  <option value="Black">Black</option>
                  <option value="Middle Eastern">Middle Eastern</option>
                  <option value="Mixed">Mixed</option>
                  <option value="Other">Other</option>
                </select>

                {/* Interested In - chip/toggle buttons */}
                <div className="w-full flex flex-wrap items-center gap-2">
                  <span className="text-xs text-white/50 font-medium mr-1">Interested In:</span>
                  {['all', 'Men', 'Women', 'Couples', 'Trans', 'Everyone'].map(opt => (
                    <button
                      key={opt}
                      onClick={() => setInterestedIn(opt)}
                      className={`px-3 py-1.5 rounded-full text-xs font-semibold transition-all duration-200 ${
                        interestedIn === opt
                          ? 'bg-purple-600 text-white shadow-lg shadow-purple-500/20'
                          : 'bg-white/5 text-white/60 hover:bg-white/10 hover:text-white'
                      }`}
                    >
                      {opt === 'all' ? 'All' : opt}
                    </button>
                  ))}
                </div>

                {/* Language dropdown */}
                <select
                  value={language}
                  onChange={(e) => setLanguage(e.target.value)}
                  className="premium-select text-sm py-2 px-3 rounded-full bg-white/5 border-0 text-white/80"
                >
                  <option value="all">Language: All</option>
                  <option value="English">English</option>
                  <option value="Dutch">Dutch</option>
                  <option value="Spanish">Spanish</option>
                  <option value="German">German</option>
                  <option value="French">French</option>
                  <option value="Italian">Italian</option>
                </select>
              </div>
            )}
          </div>
        </div>

        {/* Discovery: Live Now in Your Country */}
        {liveByCountry.length > 0 && (
          <section className="mb-12">
            <div className="flex items-center gap-2 mb-6">
              <div className="w-2.5 h-2.5 rounded-full bg-red-500 animate-pulse" />
              <h2 className="text-2xl font-bold text-white tracking-tight">
                Live now{userCountry ? ` in ${getCountryLabel(userCountry)}` : ''}
              </h2>
            </div>
            <div className="flex overflow-x-auto pb-6 gap-6 scrollbar-hide -mx-4 px-4 sm:-mx-0 sm:px-0 snap-x">
              {liveByCountry.map((c) => (
                <div key={`live-country-${c.userId}`} className="flex-none snap-start w-56 sm:w-72">
                  <CreatorCard creator={c} onClick={handleCreatorClick} variant="explore" />
                </div>
              ))}
            </div>
          </section>
        )}

        {/* Discovery: Top Creators in Your Country */}
        {topCreators.length > 0 && (
          <section className="mb-12">
            <div className="flex items-center gap-2 mb-6">
              <span className="text-xl">🏆</span>
              <h2 className="text-2xl font-bold text-white tracking-tight">
                Top creators{userCountry ? ` in ${getCountryLabel(userCountry)}` : ''}
              </h2>
            </div>
            <div className="flex overflow-x-auto pb-6 gap-6 scrollbar-hide -mx-4 px-4 sm:-mx-0 sm:px-0 snap-x">
              {topCreators.map((c) => (
                <div key={`top-${c.userId}`} className="flex-none snap-start w-56 sm:w-72">
                  <CreatorCard creator={c} onClick={handleCreatorClick} variant="explore" />
                </div>
              ))}
            </div>
          </section>
        )}

        {/* Discovery: Creators Near You */}
        {nearbyCreators.length > 0 && userCountry && (
          <section className="mb-12">
            <div className="flex items-center gap-2 mb-6">
              <span className="text-xl">📍</span>
              <h2 className="text-2xl font-bold text-white tracking-tight">
                Creators near you
              </h2>
            </div>
            <div className="flex overflow-x-auto pb-6 gap-6 scrollbar-hide -mx-4 px-4 sm:-mx-0 sm:px-0 snap-x">
              {nearbyCreators.map((c) => (
                <div key={`nearby-${c.userId}`} className="flex-none snap-start w-56 sm:w-72">
                  <CreatorCard creator={c} onClick={handleCreatorClick} variant="explore" />
                </div>
              ))}
            </div>
          </section>
        )}

        {/* Live Now Section (all countries) */}
        {!loading && !error && liveCreators.length > 0 && (
          <section className="mb-12">
            <div className="flex items-center gap-2 mb-6">
              <div className="w-2.5 h-2.5 rounded-full bg-red-500 animate-pulse" />
              <h2 className="text-2xl font-bold text-white tracking-tight">Live Now</h2>
            </div>
            <div className="flex overflow-x-auto pb-6 gap-6 scrollbar-hide -mx-4 px-4 sm:-mx-0 sm:px-0 snap-x">
              {liveCreators.map((c) => (
                <div key={`live-${c.userId}`} className="flex-none snap-start w-56 sm:w-72">
                  <CreatorCard 
                    creator={c} 
                    onClick={handleCreatorClick}
                    variant="explore"
                  />
                </div>
              ))}
            </div>
          </section>
        )}

        {/* All Creators Section */}
        {!loading && !error && nonLiveCreators.length > 0 && (
          <h2 className="text-2xl font-bold text-white mb-6 tracking-tight">
            All Creators
          </h2>
        )}

        {loading ? (
          <div className="creators-grid">
            {initialSkeletons.map((_, i) => (
              <CreatorCard.Skeleton key={`initial-skeleton-${i}`} variant="explore" />
            ))}
          </div>
        ) : error ? (
          <div className="text-center py-20 bg-[#0F0F14] rounded-3xl border border-[#16161D] shadow-2xl">
            <p className="text-red-400 mb-6 font-bold">{error}</p>
            <button 
              onClick={() => fetchCreators(0, true)} 
              className="px-8 py-3 bg-indigo-600 text-white font-black rounded-xl hover:bg-indigo-700 transition-colors shadow-lg shadow-indigo-600/20"
            >
              Retry
            </button>
          </div>
        ) : allSortedCreators.length === 0 ? (
          <div className="col-span-full text-center py-20 text-white/40">
            No creators found matching your filters.
          </div>
        ) : (
          <>
            <div className="creators-grid">
              {nonLiveCreators.map((c) => (
                <CreatorCard
                  key={c.userId}
                  creator={c}
                  onClick={handleCreatorClick}
                  variant="explore"
                />
              ))}
            </div>

            {/* Sentinel for infinite scroll */}
            {hasMore && (
              <div ref={lastCreatorElementRef} className="h-4 w-full" />
            )}

            {isFetchingNextPage && (
              <div className="creators-grid mt-8">
                {nextSkeletons.map((_, i) => (
                  <CreatorCard.Skeleton key={`next-skeleton-${i}`} variant="explore" />
                ))}
              </div>
            )}
            {!hasMore && allSortedCreators.length > 0 && (
              <p className="text-center text-zinc-500 mt-12 mb-8">
                You've reached the end of the list.
              </p>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default ExploreCreatorsPage;
