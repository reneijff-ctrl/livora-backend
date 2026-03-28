import { useEffect, useRef, useCallback } from 'react';
import { webSocketService } from '@/websocket/webSocketService';
import { IMessage } from '@stomp/stompjs';

/**
 * Well-known channel names matching StreamEventPublisher constants on the backend.
 */
export const StreamChannel = {
  CHAT: 'chat',
  TIP: 'tip',
  GOAL: 'goal',
  LEADERBOARD: 'leaderboard',
  VIEWERS: 'viewers',
  STREAM_STATUS: 'stream_status',
} as const;

export type StreamChannelType = (typeof StreamChannel)[keyof typeof StreamChannel];

/** Shape of a single event on the composite topic. */
export interface StreamEvent {
  type: 'STREAM_EVENT';
  channel: string;
  data: any;
}

/** Shape of a batched composite payload. */
export interface StreamEventBatch {
  type: 'STREAM_EVENT_BATCH';
  events: StreamEvent[];
}

type EventHandler = (data: any) => void;

/**
 * Subscribes to the single composite WebSocket topic for a given creator
 * (`/exchange/amq.topic/stream.events.{creatorId}`) and dispatches
 * incoming events to registered per-channel handlers.
 *
 * Handles both single `STREAM_EVENT` and batched `STREAM_EVENT_BATCH`
 * payloads from the backend `StreamEventPublisher`.
 *
 * Handlers are stored in refs so that re-registering a handler does NOT
 * cause re-subscription — the WebSocket subscription is stable across
 * handler changes, preventing unnecessary unsubscribe/resubscribe cycles.
 *
 * @example
 * ```tsx
 * const { on, off } = useStreamEvents(creatorUserId);
 *
 * useEffect(() => {
 *   on('tip', handleTipEvent);
 *   on('goal', handleGoalEvent);
 *   on('viewers', handleViewerEvent);
 *   return () => {
 *     off('tip');
 *     off('goal');
 *     off('viewers');
 *   };
 * }, [on, off, handleTipEvent, handleGoalEvent, handleViewerEvent]);
 * ```
 */
export function useStreamEvents(creatorUserId: number | undefined | null) {
  // Map of channel → handler, stored in a ref to avoid re-subscriptions
  const handlersRef = useRef<Map<string, EventHandler>>(new Map());

  /**
   * Register a handler for a specific channel.
   * Calling `on` again for the same channel replaces the previous handler.
   */
  const on = useCallback((channel: string, handler: EventHandler) => {
    handlersRef.current.set(channel, handler);
  }, []);

  /**
   * Remove the handler for a specific channel.
   */
  const off = useCallback((channel: string) => {
    handlersRef.current.delete(channel);
  }, []);

  // Single stable WebSocket subscription — depends only on creatorUserId
  useEffect(() => {
    if (!creatorUserId) return;

    const destination = `/exchange/amq.topic/stream.events.${creatorUserId}`;

    const unsub = webSocketService.subscribe(destination, (msg: IMessage) => {
      try {
        const payload = JSON.parse(msg.body);

        if (payload.type === 'STREAM_EVENT_BATCH' && Array.isArray(payload.events)) {
          // Batched composite: dispatch each event in order
          for (const event of payload.events) {
            dispatchEvent(event);
          }
        } else if (payload.type === 'STREAM_EVENT' && payload.channel) {
          // Single composite event
          dispatchEvent(payload);
        } else {
          // Fallback: try to dispatch using a top-level 'channel' field
          if (payload.channel) {
            dispatchEvent(payload);
          }
        }
      } catch (e) {
        console.error('[useStreamEvents] Failed to process composite event', e);
      }
    });

    return () => {
      if (typeof unsub === 'function') unsub();
    };
  }, [creatorUserId]);

  /**
   * Route a single parsed event to its registered handler.
   */
  function dispatchEvent(event: StreamEvent) {
    if (!event?.channel || event.data === undefined) return;
    const handler = handlersRef.current.get(event.channel);
    if (handler) {
      try {
        handler(event.data);
      } catch (e) {
        console.error(`[useStreamEvents] Handler error for channel "${event.channel}"`, e);
      }
    }
  }

  return { on, off };
}
