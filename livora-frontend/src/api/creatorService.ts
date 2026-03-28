import apiClient, { publicApiClient } from './apiClient';
import { ContentItem } from './contentService';
import { ICreator } from '../domain/creator/ICreator';
import { adaptCreator } from '../adapters/CreatorAdapter';
import { CreatorPost, FollowStatus, ExplorePost, CreatorEarningsDashboard, CreatorTip, CreatorMonetization } from '../types';
import authStore from '../store/authStore';
import { CreatorVerificationRequest, CreatorVerificationResponse } from '../types/verification';

// Cache for creator pages to prevent redundant network requests
interface CachedPage {
  data: { content: ICreator[]; totalPages: number; hasNext?: boolean };
  timestamp: number;
}

const PAGE_CACHE = new Map<string, CachedPage>();
const CACHE_TTL = 5 * 60 * 1000; // 5 minutes
const MAX_CACHE_SIZE = 100; // Maximum number of pages to cache

const creatorService = {
  /**
   * Fetches public posts for a creator.
   */
  async getCreatorPosts(identifier: string, skipToast = false): Promise<CreatorPost[]> {
    const response = await publicApiClient.get(`/creators/${identifier}/posts`, {
      // @ts-ignore
      _skipToast: skipToast
    });
    return response.data as CreatorPost[];
  },

  /**
   * Creates a new post for the currently authenticated creator.
   */
  async createPost(data: { title: string; content: string }): Promise<CreatorPost> {
    const response = await apiClient.post<CreatorPost>('/creator/posts', data);
    return response.data;
  },

  /**
   * Fetches the profile of the currently authenticated creator.
   */
  async getMyProfile(): Promise<ICreator> {
    const token = localStorage.getItem("token");
    if (!token) return adaptCreator(null);
    const response = await apiClient.get<any>('/creator/profile');
    return adaptCreator(response.data);
  },

  /**
   * Updates the profile of the currently authenticated creator.
   */
  async updateMyProfile(data: { displayName: string; bio?: string; profileImageUrl?: string; bannerUrl?: string }): Promise<ICreator> {
    const response = await apiClient.put<any>('/creator/profile', data);
    return adaptCreator(response.data);
  },

  /**
   * Uploads a profile or banner image for the currently authenticated creator.
   */
  async uploadCreatorImage(file: File, type: 'PROFILE' | 'BANNER'): Promise<ICreator> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('type', type);

    const response = await apiClient.post<any>('/creator/profile/upload', formData, {
      headers: {
        'Content-Type': undefined,
      },
    });
    return adaptCreator(response.data);
  },

  /**
   * Fetches a list of public, active creators for discovery.
   * This method is public and handles paginated responses from the backend.
   * Results are cached for 5 minutes to prevent redundant requests on filter changes.
   */
  async getPublicCreators(
    category?: string, country?: string, page = 0, size = 48, search?: string,
    signal?: AbortSignal, liveOnly?: boolean, paidFilter?: string, sort?: string,
    bodyType?: string, hairColor?: string, eyeColor?: string, ethnicity?: string,
    interestedIn?: string, language?: string
  ): Promise<{ content: ICreator[]; totalPages: number; hasNext: boolean }> {
    const cacheKey = `creators-${category || 'all'}-${country || 'all'}-${page}-${size}-${search || ''}-${liveOnly ?? 'any'}-${paidFilter || 'all'}-${sort || 'relevance'}-${bodyType || 'all'}-${hairColor || 'all'}-${eyeColor || 'all'}-${ethnicity || 'all'}-${interestedIn || 'all'}-${language || 'all'}`;
    
    // Check cache first
    const cached = PAGE_CACHE.get(cacheKey);
    if (cached && (Date.now() - cached.timestamp < CACHE_TTL)) {
      return cached.data as { content: ICreator[]; totalPages: number; hasNext: boolean };
    }

    try {
      const response = await publicApiClient.get<any>('/creators', {
        params: { category, country, page, size, search, liveOnly, paidFilter, sort, bodyType, hairColor, eyeColor, ethnicity, interestedIn, language },
        signal
      });
      // Handle 204 No Content or missing data
      if (response.status === 204 || !response.data) {
        return { content: [], totalPages: 0, hasNext: false };
      }
      
      const data = response.data;
      const rawContent = Array.isArray(data) ? data : (data.content || []);
      const totalPages = data.totalPages !== undefined ? data.totalPages : 1;
      // Spring Data Page object returns 'last' as true if it's the last page.
      // So hasNext is !data.last.
      const hasNext = data.last !== undefined ? !data.last : (rawContent.length === size);
      
      const result = {
        content: rawContent.map(adaptCreator),
        totalPages,
        hasNext
      };

      // Store in cache
      if (PAGE_CACHE.size >= MAX_CACHE_SIZE) {
        const firstKey = PAGE_CACHE.keys().next().value;
        if (firstKey) PAGE_CACHE.delete(firstKey);
      }
      PAGE_CACHE.set(cacheKey, { data: result, timestamp: Date.now() });

      return result;
    } catch (err) {
      // If it was an abort, propagate it so the caller can handle it correctly
      if (signal?.aborted || (err && typeof err === 'object' && 'name' in err && err.name === 'AbortError')) {
        throw err;
      }
      console.warn('API fetch failed, falling back to empty list', err);
      return { content: [], totalPages: 0, hasNext: false };
    }
  },

  /**
   * Fetches public creators for the homepage.
   * Results are cached for 5 minutes.
   */
  async getPublicCreatorsForHomepage(): Promise<{ content: ICreator[]; totalPages: number }> {
    const cacheKey = 'creators-homepage';
    const cached = PAGE_CACHE.get(cacheKey);
    if (cached && (Date.now() - cached.timestamp < CACHE_TTL)) {
      return cached.data as { content: ICreator[]; totalPages: number };
    }

    try {
      const response = await publicApiClient.get<any[]>('/creators/online');
      if (response.status === 204 || !response.data) {
        return { content: [], totalPages: 0 };
      }
      
      const result = {
        content: response.data.map(adaptCreator),
        totalPages: 1
      };

      // Store in cache
      if (PAGE_CACHE.size >= MAX_CACHE_SIZE) {
        const firstKey = PAGE_CACHE.keys().next().value;
        if (firstKey) PAGE_CACHE.delete(firstKey);
      }
      PAGE_CACHE.set(cacheKey, { data: result, timestamp: Date.now() });

      return result;
    } catch (err) {
      console.warn('Failed to fetch online creators for homepage:', err);
      return { content: [], totalPages: 0 };
    }
  },

  /**
   * Fetches creators who are currently online.
   */
  async getOnlineCreators(): Promise<ICreator[]> {
    try {
      const response = await publicApiClient.get<any[]>('/creators/online');
      if (response.status === 204 || !response.data) {
        return [];
      }
      return response.data.map(adaptCreator);
    } catch (err) {
      console.warn('Failed to fetch online creators:', err);
      return [];
    }
  },

  /**
   * Fetches public creator info by identifier.
   */
  async getPublicCreatorInfo(identifier: string): Promise<ICreator> {
    const token = localStorage.getItem("token");
    if (!token) return adaptCreator(null);
    const response = await apiClient.get<any>(`/creators/${identifier}`);
    return adaptCreator(response.data);
  },

  /**
   * Fetches a detailed public profile by numeric ID.
   */
  async getPublicProfileById(creatorId: number): Promise<ICreator> {
    const response = await publicApiClient.get<any>(`/public/creators/${creatorId}`);
    return adaptCreator(response.data);
  },

  /**
   * Fetches a detailed public profile by identifier (username or ID).
   */
  async getPublicProfile(identifier: string): Promise<ICreator> {
    const token = localStorage.getItem("token");
    if (!token) return adaptCreator(null);
    const response = await apiClient.get<any>(`/creators/profile/${identifier}`);
    return adaptCreator(response.data);
  },

  /**
   * Follows a creator.
   */
  async followCreator(creatorId: number): Promise<FollowStatus> {
    const response = await apiClient.post<FollowStatus>(`/creators/${creatorId}/follow`);
    console.log(`Followed creator ${creatorId}:`, response.data);
    return response.data;
  },

  /**
   * Unfollows a creator.
   */
  async unfollowCreator(creatorId: number): Promise<FollowStatus> {
    const response = await apiClient.delete<FollowStatus>(`/creators/${creatorId}/follow`);
    console.log(`Unfollowed creator ${creatorId}:`, response.data);
    return response.data;
  },

  /**
   * Gets follow status for a creator.
   */
  async getFollowStatus(creatorId: number): Promise<FollowStatus> {
    const token = localStorage.getItem("token");
    if (!token) return { following: false, followers: 0 };
    const response = await apiClient.get<FollowStatus>(`/creators/${creatorId}/follow/status`);
    return response.data;
  },

  /**
   * Gets the live viewer list for a creator's stream.
   */
  async getViewers(creatorUserId: number): Promise<{ id: number; username: string; displayName: string | null; isFollower?: boolean; isModerator?: boolean }[]> {
    const response = await apiClient.get<{ id: number; username: string; displayName: string | null; isFollower?: boolean; isModerator?: boolean }[]>(`/creators/${creatorUserId}/viewers`);
    return response.data;
  },

  /**
   * Gets follower count for a creator.
   */
  async getFollowerCount(username: string, skipToast = false): Promise<number> {
    const response = await publicApiClient.get(`/creators/${encodeURIComponent(username)}/followers/count`, {
      // @ts-ignore
      _skipToast: skipToast
    });
    return response.data as number;
  },

  async getMyContent(): Promise<ContentItem[]> {
    const token = localStorage.getItem("token");
    if (!token) return [];
    const response = await apiClient.get<ContentItem[]>('/creators/content');
    return response.data;
  },

  async createContent(data: Partial<ContentItem>): Promise<ContentItem> {
    const response = await apiClient.post<ContentItem>('/creators/content', data);
    return response.data;
  },

  async updateContent(id: string, data: any): Promise<ContentItem> {
    const response = await apiClient.put<ContentItem>(`/creators/content/${id}`, data);
    return response.data;
  },

  async deleteContent(id: string): Promise<void> {
    await apiClient.delete(`/creators/content/${id}`);
  },

  async getDashboardStats(): Promise<any> {
    const token = localStorage.getItem("token");
    if (!token) return null;
    const response = await apiClient.get('/creator/dashboard/stats');
    return response.data;
  },

  async getMyStats(skipToast = false): Promise<{ totalPosts: number; totalEarnings: number; pendingBalance: number }> {
    const token = localStorage.getItem("token");
    if (!token) return { totalPosts: 0, totalEarnings: 0, pendingBalance: 0 };
    const response = await apiClient.get('/creators/me/stats', {
      // @ts-ignore
      _skipToast: skipToast
    });
    return response.data;
  },

  async getMyEarnings(): Promise<CreatorEarningsDashboard> {
    const token = localStorage.getItem("token");
    if (!token) return {
      totalEarnings: 0,
      availableBalance: 0,
      pendingBalance: 0,
      lastEarnings: [],
      payouts: []
    } as any;
    const response = await apiClient.get<CreatorEarningsDashboard>('/creators/me/earnings');
    return response.data;
  },

  async getEarningsHistory(): Promise<any[]> {
    const token = localStorage.getItem("token");
    if (!token) return [];
    const response = await apiClient.get('/creator/dashboard/earnings');
    return response.data;
  },

  async getEarningsSummary(skipToast = false): Promise<{
    totalEarnings: number;
    monthEarnings: number;
    pendingEarnings: number;
    pendingBalance: number;
    availableEarnings: number;
    availableBalance: number;
    lastPayoutDate: string | null;
  }> {
    const token = localStorage.getItem("token");
    if (!token) return {
      totalEarnings: 0,
      monthEarnings: 0,
      pendingEarnings: 0,
      pendingBalance: 0,
      availableEarnings: 0,
      availableBalance: 0,
      lastPayoutDate: null
    };
    const response = await apiClient.get('/creator/earnings/summary', {
      // @ts-ignore
      _skipToast: skipToast
    });
    return response.data;
  },

  async getRecentTips(): Promise<CreatorTip[]> {
    const token = localStorage.getItem("token");
    if (!token) return [];
    const response = await apiClient.get<CreatorTip[]>('/creator/tips');
    return response.data;
  },

  async getConnectStatus(): Promise<any> {
    const token = localStorage.getItem("token");
    if (!token) return null;
    const response = await apiClient.get('/creator/connect/status');
    return response.data;
  },

  async getMonetizationSettings(): Promise<CreatorMonetization> {
    const token = localStorage.getItem("token");
    if (!token) return {
      subscriptionPrice: 0,
      payoutsEnabled: false,
      payoutMethod: null,
      currency: 'USD'
    } as any;
    const response = await apiClient.get<CreatorMonetization>('/creator/monetization/me');
    return response.data;
  },

  async startOnboarding(): Promise<{ onboardingUrl: string }> {
    const response = await apiClient.post('/creator/payouts/onboard');
    return response.data;
  },

  /**
   * Fetches personalized feed for the authenticated user.
   */
  async getFeed(page = 0, size = 20): Promise<{ content: CreatorPost[]; totalPages: number }> {
    const token = localStorage.getItem("token");
    if (!token) return { content: [], totalPages: 0 };
    const response = await apiClient.get<any>(`/feed?page=${page}&size=${size}`);
    return response.data;
  },

  /**
   * Likes a post.
   */
  async likePost(postId: string): Promise<void> {
    await apiClient.post(`/posts/${postId}/like`);
  },

  /**
   * Unlikes a post.
   */
  async unlikePost(postId: string): Promise<void> {
    await apiClient.delete(`/posts/${postId}/like`);
  },

  /**
   * Gets like count for a post.
   */
  async getLikeCount(postId: string): Promise<number> {
    const token = localStorage.getItem("token");
    if (!token) return 0;
    const response = await apiClient.get<number>(`/posts/${postId}/likes/count`);
    return response.data;
  },

  /**
   * Fetches public posts for the homepage feed.
   */
  async getPublicPosts(page = 0, size = 20): Promise<{ content: CreatorPost[]; totalPages: number }> {
    const response = await publicApiClient.get<any>(`/posts/public?page=${page}&size=${size}`);
    return response.data;
  },

  /**
   * Fetches explore posts for the discovery feed.
   */
  async getExplorePosts(page = 0, size = 20): Promise<{ content: ExplorePost[]; totalPages: number }> {
    const response = await publicApiClient.get<any>(`/creators/posts/explore?page=${page}&size=${size}`);
    return response.data;
  },

  /**
   * Fetches explore creators for the discovery view.
   */
  async getExploreCreators(page = 0, size = 12): Promise<{ content: ICreator[]; totalPages: number }> {
    const response = await publicApiClient.get<any>(`/creators/public?page=${page}&size=${size}`);
    return {
      content: response.data.content.map(adaptCreator),
      totalPages: response.data.totalPages
    };
  },

  /**
   * Fetches top creators, optionally filtered by country (sorted by followers DESC).
   */
  async getTopCreators(country?: string, size = 10): Promise<ICreator[]> {
    try {
      const params: Record<string, any> = { page: 0, size };
      if (country) params.country = country;
      const response = await publicApiClient.get<any>('/public/creators/top', { params });
      const content = response.data?.content ?? [];
      return Array.isArray(content) ? content.map(adaptCreator) : [];
    } catch {
      return [];
    }
  },

  /**
   * Fetches live creators, optionally filtered by country (sorted by viewers DESC).
   */
  async getLiveCreators(country?: string, size = 10): Promise<ICreator[]> {
    try {
      const params: Record<string, any> = { page: 0, size };
      if (country) params.country = country;
      const response = await publicApiClient.get<any>('/public/creators/live', { params });
      const content = response.data?.content ?? [];
      return Array.isArray(content) ? content.map(adaptCreator) : [];
    } catch {
      return [];
    }
  },

  /**
   * Stripe Connect methods
   */
  async getStripeStatus(): Promise<{ hasAccount: boolean; onboardingCompleted: boolean; payoutsEnabled: boolean }> {
    const token = localStorage.getItem("token");
    if (!token) return { hasAccount: false, onboardingCompleted: false, payoutsEnabled: false };
    const response = await apiClient.get('/creator/stripe/status');
    return response.data;
  },

  /**
   * Creator Application methods
   */
  async startApplication(): Promise<CreatorApplicationResponse> {
    const response = await apiClient.post<CreatorApplicationResponse>('/creator/onboarding/start');
    return response.data;
  },

  async submitApplication(termsAccepted: boolean, ageVerified: boolean): Promise<CreatorApplicationResponse> {
    const response = await apiClient.post<CreatorApplicationResponse>('/creator/onboarding/submit', {
      termsAccepted,
      ageVerified
    });
    return response.data;
  },

  async getApplicationStatus(): Promise<CreatorApplicationResponse | null> {
    const token = localStorage.getItem("token");
    if (!token) return null;
    try {
      const response = await apiClient.get<CreatorApplicationResponse>('/creator/onboarding/status');
      return response.data;
    } catch (error: any) {
      if (error.response && error.response.status === 404) {
        return null;
      }
      throw error;
    }
  },

  /**
   * New Multi-step Onboarding Methods
   */
  async saveOnboardingBasics(data: { username: string; displayName: string; bio: string; profilePicture: string }): Promise<void> {
    await apiClient.post('/creator/onboarding/basics', data);
  },

  async saveOnboardingProfile(data: any): Promise<void> {
    await apiClient.post('/creator/onboarding/profile', data);
  },

  async saveOnboardingVerification(data: { governmentIdImage: string; selfieWithId: string }): Promise<void> {
    await apiClient.post('/creator/onboarding/verification', data);
  },

  async getOnboardingPayoutStatus(): Promise<boolean> {
    const token = localStorage.getItem("token");
    if (!token) return false;
    const response = await apiClient.get<boolean>('/creator/onboarding/payout-status');
    return response.data;
  },

  async createStripeAccount(): Promise<{ stripeAccountId: string; onboardingUrl: string }> {
    const response = await apiClient.post('/creator/stripe/account');
    return response.data;
  },

  async getStripeOnboardingLink(): Promise<{ onboardingUrl: string }> {
    const response = await apiClient.get('/creator/stripe/onboarding-link');
    return response.data;
  },

  /**
   * Transitions creator profile from DRAFT to PENDING.
   */
  async completeOnboarding(): Promise<ICreator> {
    const response = await apiClient.post<any>('/creator/profile/complete-onboarding');
    const updatedProfile = adaptCreator(response.data);
    
    // Trigger a full state refresh to ensure all parts of the app recognize the updated profile status.
    // We avoid partial setState here to maintain auth store integrity.
    await authStore.refresh();
    
    return updatedProfile;
  },

  /**
   * Submits a new creator verification request.
   */
  async submitVerification(data: CreatorVerificationRequest): Promise<CreatorVerificationResponse> {
    const response = await apiClient.post<CreatorVerificationResponse>('/creator/verification', data);
    return response.data;
  },

  /**
   * Fetches the verification status of the currently authenticated creator.
   */
  async getMyVerification(): Promise<CreatorVerificationResponse | null> {
    const token = localStorage.getItem("token");
    if (!token) return null;
    try {
      const response = await apiClient.get<CreatorVerificationResponse>('/creator/verification-status');
      return response.data;
    } catch (error: any) {
      if (error.response && error.response.status === 404) {
        return null;
      }
      throw error;
    }
  },

  /**
   * Uploads an ID image for creator verification.
   */
  async uploadVerificationId(file: File, onProgress?: (progress: number) => void): Promise<string> {
    const formData = new FormData();
    formData.append('file', file);

    const response = await apiClient.post<{ url: string }>('/creator/verification/upload-id', formData, {
      headers: {
        'Content-Type': undefined,
      },
      onUploadProgress: (progressEvent) => {
        if (onProgress && progressEvent.total) {
          const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          onProgress(percentCompleted);
        }
      },
    });
    return response.data.url;
  },

  /**
   * Uploads a selfie image for creator verification.
   */
  async uploadVerificationSelfie(file: File, onProgress?: (progress: number) => void): Promise<string> {
    const formData = new FormData();
    formData.append('file', file);

    const response = await apiClient.post<{ url: string }>('/creator/verification/upload-selfie', formData, {
      headers: {
        'Content-Type': undefined,
      },
      onUploadProgress: (progressEvent) => {
        if (onProgress && progressEvent.total) {
          const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          onProgress(percentCompleted);
        }
      },
    });
    return response.data.url;
  },
};

export interface CreatorApplicationResponse {
  status: 'PENDING' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED';
  submittedAt?: string;
  approvedAt?: string;
  reviewNotes?: string;
}

export default creatorService;
