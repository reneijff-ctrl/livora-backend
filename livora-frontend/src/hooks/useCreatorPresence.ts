import { usePresence } from '@/ws/PresenceContext';

/**
 * Hook to check if a creator is online/live.
 * Derives state from the global presenceMap in PresenceContext.
 *
 * NOTE: creators.presence is subscribed globally via PresenceProvider — do not subscribe here.
 */
export const useCreatorPresence = (creatorId: number | string, initialOnline: boolean = false) => {
  const { presenceMap } = usePresence();

  const presence = presenceMap[Number(creatorId)];
  if (presence) {
    return presence.online === true || presence.availability === 'LIVE';
  }

  return initialOnline;
};
