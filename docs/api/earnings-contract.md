# Creator Earnings API Contract

This document defines the contract between the Livora Backend and Frontend for handling creator earnings data.

## 1. Data Structure

The `/api/creator/earnings` endpoint returns a `CreatorEarningsDTO`:

```typescript
interface CreatorEarningsDTO {
  todayTokens: number;      // Tokens earned today (UTC)
  todayRevenue: number;     // Revenue in EUR earned today
  totalTokens: number;      // Total tokens earned (net)
  totalRevenue: number;     // Total revenue in EUR earned (net)
  pendingPayout: number;    // Amount currently pending payout in EUR
  lastUpdated: string;      // ISO-8601 timestamp of last update
}
```

## 2. Real-time Updates (WebSocket)

Creators should subscribe to the following topic for real-time updates:
`topic/creator/{creatorId}/earnings`

The message payload is a `CreatorEarningsUpdateDTO`:

```typescript
interface CreatorEarningsUpdateDTO {
  type: 'TIP' | 'PPV' | 'SUBSCRIPTION' | 'HIGHLIGHTED_CHAT';
  amount: number;
  currency: string;
  currentAggregatedEarnings: CreatorEarningsDTO;
}
```

## 3. Refresh & Fallback Logic

### 3.1. Refresh Interval
When the creator is viewing an earnings-related dashboard:
- **WebSocket Active**: Updates are pushed from the server. No polling required.
- **WebSocket Inactive/Fallback**: The frontend MUST poll `GET /api/creator/earnings` every **60 seconds**.

### 3.2. WebSocket Fallback
The frontend implementation MUST handle cases where WebSocket is unavailable:
1. If the WebSocket connection cannot be established or is lost.
2. If the subscription to the creator-specific earnings topic is denied.

In these cases, the frontend should automatically switch to the REST polling mechanism defined in 3.1.

## 4. Currency Formatting Rules

### 4.1. Tokens
- Tokens are the primary internal unit for small transactions.
- Format: Whole numbers, usually accompanied by a token icon/symbol.
- Example: `1,250 Tokens`

### 4.2. Revenue (EUR)
- Revenue is displayed in Euro (EUR).
- Locale: Default to `de-DE` for currency formatting (period as decimal separator, comma as thousand separator is NOT de-DE style, de-DE uses comma for decimal and period for thousand). 
  - Actually, `de-DE` uses `,` for decimal and `.` for thousand: `1.234,56 €`.
- Rule: Always show 2 decimal places.
- Example: `€ 12,50`

### 4.3. Conversion Display
- 100 Tokens = 1.00 EUR.
- This conversion rate is fixed and handled by the backend, but may be used by the frontend for descriptive purposes (e.g. "You earned 100 tokens (~€1.00)").
