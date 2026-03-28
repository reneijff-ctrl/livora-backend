import { useState, useEffect, useCallback } from 'react';
import creatorService from '@/api/creatorService';
import { showToast } from '@/components/Toast';

interface UseFollowStateResult {
  isFollowing: boolean;
  followLoading: boolean;
  followerCount: number;
  toggleFollow: () => Promise<void>;
}

/**
 * useFollowState — Manages follow/unfollow state synced from creator profile data.
 */
export const useFollowState = (
  creatorUserId: number | undefined,
  creatorProfile: { followedByCurrentUser?: boolean; followersCount?: number } | undefined,
  hasUser: boolean,
): UseFollowStateResult => {
  const [isFollowing, setIsFollowing] = useState(false);
  const [followLoading, setFollowLoading] = useState(false);
  const [followerCount, setFollowerCount] = useState(0);

  // Sync follow state from creator profile data
  useEffect(() => {
    if (!creatorProfile) return;
    setIsFollowing(!!creatorProfile.followedByCurrentUser);
    setFollowerCount(creatorProfile.followersCount || 0);
  }, [creatorProfile]);

  const toggleFollow = useCallback(async () => {
    if (followLoading || !creatorUserId || !hasUser) return;
    setFollowLoading(true);
    try {
      if (isFollowing) {
        const status = await creatorService.unfollowCreator(creatorUserId);
        setIsFollowing(status.following);
        setFollowerCount(status.followers ?? followerCount);
      } else {
        const status = await creatorService.followCreator(creatorUserId);
        setIsFollowing(status.following);
        setFollowerCount(status.followers ?? followerCount);
      }
    } catch (err: any) {
      console.error('Follow action failed:', err);
      showToast(err.response?.data?.message || 'Follow action failed', 'error');
    } finally {
      setFollowLoading(false);
    }
  }, [followLoading, creatorUserId, hasUser, isFollowing, followerCount]);

  return {
    isFollowing,
    followLoading,
    followerCount,
    toggleFollow,
  };
};
