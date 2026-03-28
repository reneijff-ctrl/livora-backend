# Livestream Monetization Architecture — JoinLivora

## WebSocket Event Streams

All events use the STOMP broker relay via RabbitMQ (`/exchange` prefix).

### Stream Topology

| Stream | Destination | Event Types |
|--------|------------|-------------|
| **Chat** | `/exchange/amq.topic/chat.{creatorId}` | `CHAT`, `BOT`, `SYSTEM`, `TIP`*, `SUPER_TIP`*, `PIN_MESSAGE`*, `ACTION_TRIGGERED`*, `TIP_MENU`*, `GOAL_PROGRESS`*, `GOAL_COMPLETED`*, `GOAL_SWITCH`* |
| **Monetization** | `/exchange/amq.topic/monetization.{creatorId}` | `TIP`, `SUPER_TIP`, `SUPER_TIP_END`, `PIN_MESSAGE`, `ACTION_TRIGGERED`, `TIP_MENU` |
| **Goals** | `/exchange/amq.topic/goals.{creatorId}` | `GOAL_PROGRESS`, `GOAL_COMPLETED`, `GOAL_SWITCH` |
| **Leaderboard** | `/exchange/amq.topic/leaderboard.{creatorId}` | `LEADERBOARD_UPDATE` |
| **Viewers** | `/exchange/amq.topic/viewers.{creatorId}` | Viewer count updates |
| **System** | `/exchange/amq.topic/system.{creatorId}` | Reserved for future system-level events |

> *Backward compatibility: Monetization and Goal events are dual-broadcast on both the chat stream and their dedicated stream. This ensures existing clients continue to work while new subscribers can use dedicated streams.

---

## Canonical Event Envelope

All dedicated stream events use the `LiveEvent<T>` envelope:

```json
{
  "id": "uuid",
  "type": "TIP",
  "timestamp": "2026-03-12T13:58:00Z",
  "data": {
    "messageId": "...",
    "senderUsername": "alice",
    "content": "alice tipped 100 TOKEN 💎",
    "amount": 100
  }
}
```

Backend class: `com.joinlivora.backend.websocket.LiveEvent<T>`

---

## Tip Event Pipeline

```
Viewer Tip Action (TipModal / TipBar / GiftSelectorModal)
        │
        ▼
  TipOrchestrationService.sendTokenTip()
        │
        ├── Fraud/Risk checks
        ├── Token deduction (TokenWalletService)
        ├── Tip persistence (Tip + TipRecord)
        │
        ▼
  TipNotificationService.notifyTip()
        │
        ├─► [1] ChatMessageResponse(type="TIP")
        │       → /exchange/amq.topic/chat.{creatorId}
        │       → /exchange/amq.topic/monetization.{creatorId}  [LiveEvent<ChatMessageResponse>]
        │
        ├─► [2] PIN_MESSAGE (if amount > 100)
        │       → /exchange/amq.topic/chat.{creatorId}
        │       → /exchange/amq.topic/monetization.{creatorId}  [LiveEvent<ChatMessageDto>]
        │
        ├─► [3] BOT thank-you (30s cooldown per creator)
        │       → /exchange/amq.topic/chat.{creatorId}
        │
        ├─► [4] BOT tipmenu hint (60s cooldown, 2nd+ tip only)
        │       → /exchange/amq.topic/chat.{creatorId}
        │
        ├─► [5] TipGoalService.processTip()
        │       ├── GOAL_PROGRESS
        │       │   → /exchange/amq.topic/chat.{creatorId}
        │       │   → /exchange/amq.topic/goals.{creatorId}  [LiveEvent<GoalStatusEventDto>]
        │       ├── GOAL_COMPLETED (if target reached)
        │       │   → /exchange/amq.topic/chat.{creatorId}
        │       │   → /exchange/amq.topic/goals.{creatorId}  [LiveEvent<GoalStatusEventDto>]
        │       └── GOAL_SWITCH (if auto-advance)
        │           → /exchange/amq.topic/chat.{creatorId}
        │           → /exchange/amq.topic/goals.{creatorId}  [LiveEvent<GoalStatusEventDto>]
        │
        ├─► [6] TipActionService.checkAction()
        │       └── ACTION_TRIGGERED (if amount matches configured action)
        │           → /exchange/amq.topic/chat.{creatorId}
        │           → /exchange/amq.topic/monetization.{creatorId}  [LiveEvent<ActionTriggeredEventDto>]
        │
        ├─► [7] WeeklyTipService.registerTip()
        │       └── LEADERBOARD_UPDATE
        │           → /exchange/amq.topic/leaderboard.{creatorId}  [LiveEvent<List<LeaderboardEntry>>]
        │
        ├─► [8] SuperTipHighlightTracker (if qualifies)
        │       └── SUPER_TIP
        │           → /exchange/amq.topic/chat.{creatorId}
        │           → /exchange/amq.topic/monetization.{creatorId}  [LiveEvent<Map>]
        │       (on expiry) SUPER_TIP_END
        │           → /exchange/amq.topic/chat.{creatorId}
        │           → /exchange/amq.topic/monetization.{creatorId}  [LiveEvent<Map>]
        │
        └─► [9] Creator private notifications
            → /user/{creatorId}/queue/notifications
            → /user/{creatorId}/queue/dashboard/stats
            → /user/{creatorId}/queue/tips
```

---

## Service Responsibilities

| Service | Responsibility | Publishes To |
|---------|---------------|-------------|
| `TipOrchestrationService` | Orchestrates full tip flow: validation, deduction, persistence, delegation | — (delegates to TipNotificationService) |
| `TipNotificationService` | Broadcasts tip events, pins high-value tips, triggers bot/goal/action | chat + monetization + creator queues |
| `TipGoalService` | Manages tip goals, tracks progress, handles auto-switch | chat + goals |
| `TipActionService` | Matches tips to configured actions, broadcasts triggers | chat + monetization |
| `WeeklyTipService` | Maintains weekly leaderboard (Redis + DB), broadcasts updates | leaderboard |
| `SuperTipHighlightTracker` | Manages highlight queue/lifecycle, broadcasts start/end | chat + monetization |
| `BasicStreamAssistantBotService` | Generates contextual bot responses with rate limiting | chat |
| `TipMenuCommandHandler` | Handles `/tipmenu` command, lists available actions | chat + monetization |

---

## Anti-Spam Rate Limiting

| Bot Message | Cooldown | Scope |
|-------------|----------|-------|
| Thank-you message | 30 seconds | Per creator |
| Tipmenu hint | 60 seconds | Per creator per user |
| Chat engagement reminder | 60 seconds | Per creator |

---

## Frontend Architecture

### Subscription Model

```
WatchPage
├── /exchange/amq.topic/viewers.{creatorId}       → ViewerCountBadge
├── /exchange/amq.topic/monetization.{creatorId}   → TipOverlayManager (via handleTip)
├── /exchange/amq.topic/goals.{creatorId}          → GoalOverlay (via handleGoalUpdate)
├── /exchange/amq.topic/leaderboard.{creatorId}    → LeaderboardPanel + TopTipperBanner
│
└── LiveChatPanel
    └── ChatComponent
        └── /exchange/amq.topic/chat.{creatorId}   → ChatMessage rendering
```

### Event Normalization

All incoming events pass through `normalizeLiveEvent()` which:
1. Unwraps `LiveEvent<T>` envelopes via `unwrapLiveEvent()`
2. Normalizes backend DTOs into canonical `Message` objects
3. Ensures `content` is always a string (never an object)
4. Applies type-specific field mapping (e.g., SUPER_TIP `payload.message` → `content`)

### UI Component Layer

```
WatchPage
├── LiveLayout
│   ├── Video Slot
│   │   ├── LiveStreamPlayer
│   │   │   ├── TopTipperBanner        ← leaderboard stream
│   │   │   ├── LeaderboardPanel       ← leaderboard stream
│   │   │   └── LiveTipOverlays
│   │   │       ├── TipOverlayManager  ← monetization stream
│   │   │       ├── TokenCounterExplosion
│   │   │       └── LegendaryEffectOverlay
│   │   └── GoalOverlay               ← goals stream
│   │
│   └── Chat Slot
│       └── LiveChatPanel
│           └── ChatComponent          ← chat stream only
│               ├── PinnedMessageBanner
│               ├── ChatMessage (per message)
│               ├── ChatInput
│               └── TipBar
│
├── TipModal
├── GiftSelectorModal
└── Goal Completed Banner (full-screen overlay)
```

---

## Event Categories

### Chat Stream (chat-only rendering)
| Type | Source | Rendered By |
|------|--------|------------|
| `CHAT` | ChatMessageService | ChatMessage |
| `BOT` | BasicStreamAssistantBotService | ChatMessage |
| `SYSTEM` | Various (presence, moderation) | ChatMessage |

### Monetization Stream (overlays + animations)
| Type | Source | Rendered By |
|------|--------|------------|
| `TIP` | TipNotificationService | TipOverlayManager + ChatMessage |
| `SUPER_TIP` | SuperTipHighlightTracker | TipOverlayManager + LegendaryEffect |
| `SUPER_TIP_END` | SuperTipHighlightTracker | Clears LegendaryEffect |
| `PIN_MESSAGE` | TipNotificationService | PinnedMessageBanner |
| `ACTION_TRIGGERED` | TipActionService | ChatMessage |
| `TIP_MENU` | TipMenuCommandHandler | ChatMessage |

### Goals Stream (progress tracking)
| Type | Source | Rendered By |
|------|--------|------------|
| `GOAL_PROGRESS` | TipGoalService | GoalOverlay |
| `GOAL_COMPLETED` | TipGoalService | GoalOverlay + Goal Banner |
| `GOAL_SWITCH` | TipGoalService | GoalOverlay |

### Leaderboard Stream (ranking updates)
| Type | Source | Rendered By |
|------|--------|------------|
| `LEADERBOARD_UPDATE` | WeeklyTipService | LeaderboardPanel + TopTipperBanner |
