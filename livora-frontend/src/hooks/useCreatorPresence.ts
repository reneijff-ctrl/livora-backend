import { useWs } from '@/ws/WsContext';

/**
 * Hook to check if a creator is online/live.
 * Derives state from the global presenceMap in WsContext.
 *
 * NOTE: creators.presence is subscribed globally via WsContext — do not subscribe here.
 */
export const useCreatorPresence = (creatorId: number | string, initialOnline: boolean = false) => {
  const { presenceMap } = useWs();

  const presence = presenceMap[Number(creatorId)];
  if (presence) {
    return presence.online === true || presence.availability === 'LIVE';
  }

  return initialOnline;
};
