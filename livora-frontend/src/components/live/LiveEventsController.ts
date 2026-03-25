import { Message } from '@/types/chat';

export type LiveEventType = "TIP" | "CHAT" | "GOAL" | "PIN" | "SYSTEM" | "ACTION" | "TIP_MENU" | "SUPER_TIP_END";

/**
 * Canonical envelope for all livestream events.
 * All frontend components should consume LiveEvent<T> instead of raw DTOs.
 */
export interface LiveEvent<T = any> {
  id: string;
  type: string;
  timestamp: string;
  data: T;
}

export interface RoutedEvent {
  event: LiveEventType;
  payload: any;
}

export function routeLiveEvent(message: any): RoutedEvent | undefined {
  if (!message?.type) return;

  switch (message.type) {
    case "TIP":
    case "SUPER_TIP":
      console.debug("TIP ROUTED THROUGH CONTROLLER", message);
      return {
        event: "TIP",
        payload: message
      };

    case "CHAT":
    case "BOT":
      return {
        event: "CHAT",
        payload: message
      };

    case "SYSTEM":
      return {
        event: "SYSTEM",
        payload: message
      };

    case "GOAL_PROGRESS":
    case "GOAL_COMPLETED":
    case "GOAL_SWITCH":
      return {
        event: "GOAL",
        payload: message
      };

    case "PIN_MESSAGE":
      return {
        event: "PIN",
        payload: message
      };

    case "ACTION_TRIGGERED":
      return {
        event: "ACTION",
        payload: message
      };

    case "SUPER_TIP_END":
      return {
        event: "SUPER_TIP_END",
        payload: message
      };

    case "TIP_MENU":
      return {
        event: "TIP_MENU",
        payload: message
      };

    default:
      console.warn("Unknown live event", message);
      return undefined;
  }
}

/**
 * Wraps any normalized Message into a canonical LiveEvent envelope.
 * Components should prefer consuming LiveEvent<T> for type consistency.
 */
export function toLiveEvent<T = any>(message: Message): LiveEvent<T> {
  return {
    id: message.id || message.messageId || `evt-${Date.now()}`,
    type: message.type || 'CHAT',
    timestamp: message.timestamp || new Date().toISOString(),
    data: {
      ...message,
    } as unknown as T,
  };
}

/**
 * Unwraps a LiveEvent envelope if present, returning the inner data with type preserved.
 * Events from dedicated streams arrive as { id, type, timestamp, data: {...} }.
 */
export function unwrapLiveEvent(incoming: any): any {
  if (incoming && incoming.data && typeof incoming.data === 'object' && incoming.type) {
    return { ...incoming.data, type: incoming.type, _liveEventId: incoming.id, _liveEventTimestamp: incoming.timestamp };
  }
  return incoming;
}

/**
 * Normalizes any backend event DTO into a canonical Message object
 * suitable for React rendering. Ensures `content` is always a string.
 * Automatically unwraps LiveEvent<T> envelopes from dedicated streams.
 */
export function normalizeLiveEvent(incoming: any): Message {
  const unwrapped = unwrapLiveEvent(incoming);
  const rawMsg = unwrapped.chatMessage || unwrapped;
  const type = unwrapped.type || rawMsg.type || 'CHAT';

  const uniqueId = String(
    rawMsg.messageId ||
    rawMsg.id ||
    rawMsg.clientRequestId ||
    rawMsg.payload?.id ||
    `msg-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`
  );

  switch (type) {
    case 'SUPER_TIP': {
      // Support both chat stream (rawMsg.payload exists) and monetization stream
      // (unwrapLiveEvent flattens data into top-level fields, so rawMsg IS the payload)
      const p = rawMsg.payload || rawMsg.data || rawMsg;
      return {
        id: uniqueId,
        messageId: uniqueId,
        type,
        username:
          typeof p.viewer === 'string' ? p.viewer
          : p.viewer?.username || (typeof p.username === 'string' ? p.username : null)
          || rawMsg.senderUsername || 'Anonymous',
        senderUsername:
          typeof p.viewer === 'string' ? p.viewer
          : p.viewer?.username || (typeof p.username === 'string' ? p.username : null)
          || rawMsg.senderUsername,
        content:
          typeof p.message === 'string' ? p.message
          : typeof p.content === 'string' ? p.content
          : '',
        amount: Number(p.amount) || 0,
        giftName: p.giftName,
        timestamp: rawMsg.timestamp || new Date().toISOString(),
        role: rawMsg.senderRole || rawMsg.role,
        highlight: rawMsg.highlight || p.highlightLevel,
        payload: p
      };
    }

    case 'SUPER_TIP_END': {
      return {
        id: uniqueId,
        messageId: uniqueId,
        type,
        username: 'System',
        content: '',
        timestamp: rawMsg.timestamp || new Date().toISOString(),
        payload: rawMsg.payload ?? undefined
      };
    }

    case 'ACTION_TRIGGERED': {
      return {
        id: uniqueId,
        messageId: uniqueId,
        type,
        username: rawMsg.donor || 'Someone',
        senderUsername: rawMsg.donor,
        content: typeof rawMsg.description === 'string' ? rawMsg.description : '',
        amount: rawMsg.amount,
        timestamp: rawMsg.timestamp || new Date().toISOString(),
        payload: {
          donor: rawMsg.donor,
          amount: rawMsg.amount,
          description: rawMsg.description
        }
      };
    }

    case 'TIP_MENU': {
      return {
        id: uniqueId,
        messageId: uniqueId,
        type,
        username: 'System',
        content: 'Tip menu',
        timestamp: rawMsg.timestamp || new Date().toISOString(),
        payload: {
          actions: rawMsg.actions || []
        }
      };
    }

    case 'GOAL_PROGRESS':
    case 'GOAL_COMPLETED':
    case 'GOAL_SWITCH':
    case 'GOAL_STATUS':
    case 'GOAL_GROUP_PROGRESS':
    case 'GOAL_GROUP_COMPLETED':
    case 'MILESTONE_REACHED': {
      return {
        id: uniqueId,
        messageId: uniqueId,
        type,
        username: 'System',
        content: rawMsg.title || '',
        amount: rawMsg.currentAmount,
        timestamp: rawMsg.timestamp || new Date().toISOString(),
        payload: {
          title: rawMsg.title,
          targetAmount: rawMsg.targetAmount,
          currentAmount: rawMsg.currentAmount,
          percentage: rawMsg.percentage,
          isCompleted: rawMsg.isCompleted,
          active: rawMsg.active,
          milestones: rawMsg.milestones ?? rawMsg.milestoneStatuses ?? []
        }
      };
    }

    default: {
      // CHAT, TIP, BOT, SYSTEM, PIN_MESSAGE and other flat DTOs
      return {
        id: uniqueId,
        messageId: uniqueId,
        type,
        senderId: rawMsg.senderId != null ? String(rawMsg.senderId) : undefined,
        username:
          (typeof rawMsg.username === 'string' ? rawMsg.username : null) ||
          (typeof rawMsg.senderUsername === 'string' ? rawMsg.senderUsername : null) ||
          (typeof rawMsg.sender === 'string' ? rawMsg.sender : rawMsg.sender?.username) ||
          (typeof rawMsg.viewer === 'string' ? rawMsg.viewer : rawMsg.viewer?.username) ||
          (typeof rawMsg.payload?.displayName === 'string' ? rawMsg.payload.displayName : null) ||
          (typeof rawMsg.payload?.username === 'string' ? rawMsg.payload.username : null) ||
          (typeof rawMsg.payload?.viewer === 'string' ? rawMsg.payload.viewer : null) ||
          'Anonymous',
        senderUsername: rawMsg.senderUsername,
        content:
          typeof rawMsg.content === 'string' ? rawMsg.content
          : typeof rawMsg.message === 'string' ? rawMsg.message
          : typeof rawMsg === 'string' ? rawMsg
          : '',
        timestamp: rawMsg.timestamp || new Date().toISOString(),
        role: rawMsg.senderRole || rawMsg.role,
        amount: rawMsg.amount,
        giftName: rawMsg.giftName,
        highlight: rawMsg.highlight,
        payload: rawMsg.payload ?? undefined
      };
    }
  }
}
