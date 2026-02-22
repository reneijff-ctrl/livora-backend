import apiClient, { publicApiClient } from './apiClient';
import { ContentItem } from './contentService';
import { ICreator } from '../domain/creator/ICreator';
import { adaptCreator } from '../adapters/CreatorAdapter';
import { CreatorPost, FollowStatus, ExplorePost, CreatorEarningsDashboard, CreatorTip, CreatorMonetization } from '../types';
import { CreatorVerificationRequest, CreatorVerificationResponse } from '../types/verification';

const creatorService = {
  /**
   * Fetches public posts for a creator.
   */
  async getCreatorPosts(identifier: string, skipToast = false): Promise<CreatorPost[]> {
    const response = await publicApiClient.get(`/api/creators/${identifier}/posts`, {
      // @ts-ignore
      _skipToast: skipToast
    });
    return response.data as CreatorPost[];
  },

  /**
   * Creates a new post for the currently authenticated creator.
   */
  async createPost(data: { title: string; content: string }): Promise<CreatorPost> {
    const response = await apiClient.post<CreatorPost>('/api/creator/posts', data);
    return response.data;
  },

  /**
   * Fetches the profile of the currently authenticated creator.
   */
  async getMyProfile(): Promise<ICreator> {
    const response = await apiClient.get<any>('/api/creator/profile');
    return adaptCreator(response.data);
  },

  /**
   * Updates the profile of the currently authenticated creator.
   */
  async updateMyProfile(data: { displayName: string; bio?: string; profileImageUrl?: string; bannerUrl?: string }): Promise<ICreator> {
    const response = await apiClient.put<any>('/api/creator/profile', data);
    return adaptCreator(response.data);
  },

  /**
   * Uploads a profile or banner image for the currently authenticated creator.
   */
  async uploadCreatorImage(file: File, type: 'PROFILE' | 'BANNER'): Promise<ICreator> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('type', type);

    const response = await apiClient.post<any>('/api/creator/profile/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return adaptCreator(response.data);
  },

  /**
   * Fetches a list of public, active creators for discovery.
   * This method is public and handles paginated responses from the backend.
   */
  async getPublicCreators(): Promise<ICreator[]> {
    try {
      const response = await publicApiClient.get<any>('/api/creators');
      // Handle 204 No Content or missing data
      if (response.status === 204 || !response.data) {
        return [];
      }
      // Handle both direct list and paginated response for robustness
      const rawData = Array.isArray(response.data) ? response.data : (response.data.content || []);
      return rawData.map(adaptCreator);
    } catch (err) {
      console.warn('API fetch failed, falling back to empty list', err);
      return [];
    }
  },

  /**
   * Fetches public creators for the homepage.
   */
  async getPublicCreatorsForHomepage(): Promise<{ content: ICreator[]; totalPages: number }> {
    try {
      const response = await publicApiClient.get<any[]>('/api/creators/online');
      if (response.status === 204 || !response.data) {
        return { content: [], totalPages: 0 };
      }
      return {
        content: response.data.map(adaptCreator),
        totalPages: 1
      };
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
      const response = await publicApiClient.get<any[]>('/api/creators/online');
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
    const response = await apiClient.get<any>(`/api/creators/${identifier}`);
    return adaptCreator(response.data);
  },

  /**
   * Fetches a detailed public profile by numeric ID.
   */
  async getPublicProfileById(creatorId: number): Promise<ICreator> {
    const response = await publicApiClient.get<any>(`/api/public/creators/${creatorId}`);
    return adaptCreator(response.data);
  },

  /**
   * Fetches a detailed public profile by identifier (username or ID).
   */
  async getPublicProfile(identifier: string): Promise<ICreator> {
    const response = await apiClient.get<any>(`/api/creators/profile/${identifier}`);
    return adaptCreator(response.data);
  },

  /**
   * Follows a creator.
   */
  async followCreator(creatorId: number): Promise<FollowStatus> {
    const response = await apiClient.post<FollowStatus>(`/api/creators/${creatorId}/follow`);
    console.log(`Followed creator ${creatorId}:`, response.data);
    return response.data;
  },

  /**
   * Unfollows a creator.
   */
  async unfollowCreator(creatorId: number): Promise<FollowStatus> {
    const response = await apiClient.delete<FollowStatus>(`/api/creators/${creatorId}/follow`);
    console.log(`Unfollowed creator ${creatorId}:`, response.data);
    return response.data;
  },

  /**
   * Gets follow status for a creator.
   */
  async getFollowStatus(creatorId: number): Promise<FollowStatus> {
    const response = await apiClient.get<FollowStatus>(`/api/creators/${creatorId}/follow/status`);
    console.log(`Follow status for creator ${creatorId}:`, response.data);
    return response.data;
  },

  /**
   * Gets follower count for a creator.
   */
  async getFollowerCount(username: string, skipToast = false): Promise<number> {
    const response = await publicApiClient.get(`/api/creators/${encodeURIComponent(username)}/followers/count`, {
      // @ts-ignore
      _skipToast: skipToast
    });
    return response.data as number;
  },

  async getMyContent(): Promise<ContentItem[]> {
    const response = await apiClient.get<ContentItem[]>('/api/creator/content/mine');
    return response.data;
  },

  async createContent(data: Partial<ContentItem>): Promise<ContentItem> {
    const response = await apiClient.post<ContentItem>('/api/creator/content', data);
    return response.data;
  },

  async updateContent(id: string, data: Partial<ContentItem>): Promise<ContentItem> {
    const response = await apiClient.put<ContentItem>(`/api/creator/content/${id}`, data);
    return response.data;
  },

  async deleteContent(id: string): Promise<void> {
    await apiClient.delete(`/api/creator/content/${id}`);
  },

  async getDashboardStats(): Promise<any> {
    const response = await apiClient.get('/api/creator/dashboard/stats');
    return response.data;
  },

  async getMyStats(skipToast = false): Promise<{ totalPosts: number; totalEarnings: number; pendingBalance: number }> {
    const response = await apiClient.get('/api/creators/me/stats', {
      // @ts-ignore
      _skipToast: skipToast
    });
    return response.data;
  },

  async getMyEarnings(): Promise<CreatorEarningsDashboard> {
    const response = await apiClient.get<CreatorEarningsDashboard>('/api/creators/me/earnings');
    return response.data;
  },

  async getEarningsHistory(): Promise<any[]> {
    const response = await apiClient.get('/api/creator/dashboard/earnings');
    return response.data;
  },

  async getEarningsSummary(skipToast = false): Promise<{
    totalEarnings: number;
    totalEarned: number;
    monthEarnings: number;
    pendingEarnings: number;
    pendingBalance: number;
    availableEarnings: number;
    availableBalance: number;
    lastPayoutDate: string | null;
  }> {
    const response = await apiClient.get('/api/creator/earnings/summary', {
      // @ts-ignore
      _skipToast: skipToast
    });
    return response.data;
  },

  async getRecentTips(): Promise<CreatorTip[]> {
    const response = await apiClient.get<CreatorTip[]>('/api/creator/tips');
    return response.data;
  },

  async getConnectStatus(): Promise<any> {
    const response = await apiClient.get('/api/creator/connect/status');
    return response.data;
  },

  async getMonetizationSettings(): Promise<CreatorMonetization> {
    const response = await apiClient.get<CreatorMonetization>('/api/creator/monetization/me');
    return response.data;
  },

  async startOnboarding(): Promise<{ onboardingUrl: string }> {
    const response = await apiClient.post('/api/creator/payouts/onboard');
    return response.data;
  },

  /**
   * Fetches personalized feed for the authenticated user.
   */
  async getFeed(page = 0, size = 20): Promise<{ content: CreatorPost[]; totalPages: number }> {
    const response = await apiClient.get<any>(`/api/feed?page=${page}&size=${size}`);
    return response.data;
  },

  /**
   * Likes a post.
   */
  async likePost(postId: string): Promise<void> {
    await apiClient.post(`/api/posts/${postId}/like`);
  },

  /**
   * Unlikes a post.
   */
  async unlikePost(postId: string): Promise<void> {
    await apiClient.delete(`/api/posts/${postId}/like`);
  },

  /**
   * Gets like count for a post.
   */
  async getLikeCount(postId: string): Promise<number> {
    const response = await apiClient.get<number>(`/api/posts/${postId}/likes/count`);
    return response.data;
  },

  /**
   * Fetches public posts for the homepage feed.
   */
  async getPublicPosts(page = 0, size = 20): Promise<{ content: CreatorPost[]; totalPages: number }> {
    const response = await publicApiClient.get<any>(`/api/posts/public?page=${page}&size=${size}`);
    return response.data;
  },

  /**
   * Fetches explore posts for the discovery feed.
   */
  async getExplorePosts(page = 0, size = 20): Promise<{ content: ExplorePost[]; totalPages: number }> {
    const response = await publicApiClient.get<any>(`/api/creators/posts/explore?page=${page}&size=${size}`);
    return response.data;
  },

  /**
   * Fetches explore creators for the discovery view.
   */
  async getExploreCreators(page = 0, size = 12): Promise<{ content: ICreator[]; totalPages: number }> {
    const response = await publicApiClient.get<any>(`/api/creators/public?page=${page}&size=${size}`);
    return {
      content: response.data.content.map(adaptCreator),
      totalPages: response.data.totalPages
    };
  },

  /**
   * Stripe Connect methods
   */
  async getStripeStatus(): Promise<{ hasAccount: boolean; onboardingCompleted: boolean; payoutsEnabled: boolean }> {
    const response = await apiClient.get('/api/creator/stripe/status');
    return response.data;
  },

  async createStripeAccount(): Promise<{ stripeAccountId: string; onboardingUrl: string }> {
    const response = await apiClient.post('/api/creator/stripe/account');
    return response.data;
  },

  async getStripeOnboardingLink(): Promise<{ onboardingUrl: string }> {
    const response = await apiClient.get('/api/creator/stripe/onboarding-link');
    return response.data;
  },

  /**
   * Transitions creator profile from DRAFT to PENDING.
   */
  async completeOnboarding(): Promise<ICreator> {
    const response = await apiClient.post<any>('/api/creator/profile/complete-onboarding');
    return adaptCreator(response.data);
  },

  /**
   * Submits a new creator verification request.
   */
  async submitVerification(data: CreatorVerificationRequest): Promise<CreatorVerificationResponse> {
    const response = await apiClient.post<CreatorVerificationResponse>('/api/creator/verification', data);
    return response.data;
  },

  /**
   * Fetches the verification status of the currently authenticated creator.
   */
  async getMyVerification(): Promise<CreatorVerificationResponse | null> {
    try {
      const response = await apiClient.get<CreatorVerificationResponse>('/api/creator/verification/me');
      return response.data;
    } catch (error: any) {
      if (error.response && error.response.status === 404) {
        return null;
      }
      throw error;
    }
  },

  /**
   * Uploads an image for creator verification.
   */
  async uploadVerificationImage(file: File, type: 'front' | 'back' | 'selfie'): Promise<string> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('type', type);

    const response = await apiClient.post<{ url: string }>('/api/creator/verification/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data.url;
  },
};

export default creatorService;
