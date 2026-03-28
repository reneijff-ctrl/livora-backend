# Livestream UI — Component Hierarchy

## Overall Layout (70% Video / 30% Chat)

```
WatchPage
├── Navbar
│
├── LiveLayout (flex: live-video 7 / live-chat 3)
│   │
│   ├── VIDEO AREA (70%) ─────────────────────────────────────────────
│   │   └── premium-video-container (relative, full height)
│   │       │
│   │       ├── Header Bar (absolute, top, z-20)
│   │       │   ├── Back button + Creator name + Status indicator
│   │       │   └── Controls (LIVE only):
│   │       │       ├── 🎁 Gifts button → GiftSelectorModal
│   │       │       ├── Send Tokens button → TipModal
│   │       │       ├── 🏆 Leaderboard toggle → LeaderboardPanel
│   │       │       └── ViewerCountBadge
│   │       │
│   │       ├── LiveStreamPlayer (video element)
│   │       │   │
│   │       │   ├── TopTipperBanner (top-left)
│   │       │   │   └── Shows #1 tipper name + amount
│   │       │   │
│   │       │   ├── LeaderboardPanel (slide-in, right side, over video)
│   │       │   │   └── Top 5 tippers with weekly totals
│   │       │   │
│   │       │   └── LiveTipOverlays (center overlay, z-40)
│   │       │       ├── TokenCounterExplosion (particle effect, >50 tokens)
│   │       │       ├── TipOverlayManager (animation queue, center)
│   │       │       │   └── Animation Tiers:
│   │       │       │       ├── 1-50 tokens    → small floating token
│   │       │       │       ├── 51-200 tokens  → medium glow animation
│   │       │       │       ├── 201-1000 tokens → large overlay animation
│   │       │       │       └── 1001+ tokens   → legendary fullscreen effect
│   │       │       └── LegendaryEffectOverlay (fullscreen, 1000+ tokens)
│   │       │
│   │       └── GoalOverlay (absolute, bottom-left, z-30)
│   │           └── Progress bar + title + amount
│   │
│   └── CHAT PANEL (30%) ────────────────────────────────────────────
│       └── LiveChatPanel (flex column, full height)
│           │
│           ├── Tab Bar (Chat / Users)
│           │
│           ├── Chat Content (flex-1, scrollable)
│           │   └── ChatComponent
│           │       ├── PinnedMessageBanner (sticky top)
│           │       ├── Scrollable Message List
│           │       │   └── ChatMessage (per message)
│           │       │       ├── CHAT → username + content
│           │       │       ├── TIP → amount badge + content
│           │       │       ├── BOT → bot icon + content
│           │       │       ├── SYSTEM → system styling + content
│           │       │       ├── SUPER_TIP → highlighted tip message
│           │       │       ├── ACTION_TRIGGERED → action description
│           │       │       ├── TIP_MENU → action list
│           │       │       └── GOAL_PROGRESS → progress info
│           │       └── Chat Input
│           │
│           └── Quick Tip Bar (fixed bottom, border-top separator)
│               └── TipBar
│                   ├── Quick amounts: 🪙 10 | 🪙 25 | 🪙 50 | 🪙 100 | 🎁 Gift
│                   └── Custom amount input + 💎 Send button
│
├── Modals (portal layer)
│   ├── GiftSelectorModal
│   ├── TipModal
│   └── AbuseReportModal
│
└── Goal Completed Banner (fixed fullscreen overlay, 8s auto-dismiss)
    └── "Goal Reached!" + title + target amount
```

## Event Flow to UI Components

```
WebSocket Events
│
├── Chat stream (/exchange/amq.topic/chat.{creatorId})
│   └── ChatComponent → ChatMessage rendering
│       Handles: CHAT, BOT, SYSTEM, TIP, SUPER_TIP,
│                ACTION_TRIGGERED, TIP_MENU, PIN_MESSAGE
│
├── Monetization stream (/exchange/amq.topic/monetization.{creatorId})
│   └── WatchPage → handleTip() →
│       ├── TipOverlayManager.queueTip() → animation tiers
│       ├── LegendaryEffectOverlay (if legendary)
│       ├── TokenCounterExplosion (if >50 tokens)
│       └── Optimistic leaderboard update
│
├── Goals stream (/exchange/amq.topic/goals.{creatorId})
│   └── WatchPage → handleGoalUpdate() →
│       ├── GoalOverlay (progress bar)
│       └── Goal Completed Banner (fullscreen)
│
├── Leaderboard stream (/exchange/amq.topic/leaderboard.{creatorId})
│   └── WatchPage → setLeaderboard() →
│       ├── LeaderboardPanel (top 5 list)
│       └── TopTipperBanner (#1 tipper)
│
└── Viewers stream (/exchange/amq.topic/viewers.{creatorId})
    └── WatchPage → setViewerCount() → ViewerCountBadge
```

## Animation Tiers (TipOverlayManager)

| Token Amount | Tier      | Visual Style                                    |
|-------------|-----------|------------------------------------------------|
| 1–50        | Small     | Subtle floating bubble, white text, 2.5s        |
| 51–200      | Medium    | Indigo glow border, bold text, 3.5s             |
| 201–1000    | Large     | Yellow/gold glow, "HUGE TIP!" label, 4s         |
| 1001+       | Legendary | Purple glow, "LEGENDARY!" label, fullscreen, 5s |

## Design Principles

- **Video is king**: 70% of screen width, all monetization visuals are overlays
- **Chat is secondary**: 30% width, handles text interactions only
- **Overlays for celebrations**: TipOverlayManager handles all tip animations over video
- **Quick access tips**: TipBar at bottom of chat with 10/25/50/100 + Gift + Custom
- **Leaderboard slides in**: Panel overlays on video, not a modal that blocks content
- **Minimal chat spam**: One TIP message per tip in chat, celebrations go to overlays
