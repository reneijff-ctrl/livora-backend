export type Role = 'USER' | 'PREMIUM' | 'ADMIN' | 'CREATOR' | 'VIEWER';

export type AdminRole = 'CEO' | 'ADMIN' | 'MODERATOR' | 'SUPPORT';

export type Permission =
  | 'CREATOR_APPROVE'
  | 'CREATOR_REJECT'
  | 'CREATOR_SUSPEND'
  | 'CREATOR_VIEW'
  | 'ADMIN_MANAGE'
  | 'REPORTS_VIEW'
  | 'STREAMS_MODERATE';

export enum SubscriptionStatus {
  NONE = 'NONE',
  FREE = 'FREE',
  ACTIVE = 'ACTIVE',
  PAST_DUE = 'PAST_DUE',
  CANCELED = 'CANCELED'
}

export enum UserStatus {
  ACTIVE = 'ACTIVE',
  FLAGGED = 'FLAGGED',
  PAYOUTS_FROZEN = 'PAYOUTS_FROZEN',
  SUSPENDED = 'SUSPENDED',
  MANUAL_REVIEW = 'MANUAL_REVIEW',
  TERMINATED = 'TERMINATED'
}

export enum ProfileStatus {
  DRAFT = 'DRAFT',
  PENDING = 'PENDING',
  ACTIVE = 'ACTIVE',
  SUSPENDED = 'SUSPENDED'
}

export enum ProfileVisibility {
  PUBLIC = 'PUBLIC',
  PRIVATE = 'PRIVATE'
}


export interface CreatorMonetization {
  subscriptionPrice: number;
  tipEnabled: boolean;
  balance?: number;
  pendingBalance?: number;
  lifetimeEarnings?: number;
}

export interface User {
  id: string;
  email: string;
  username: string;
  displayName?: string;
  role: Role;
  adminRole?: AdminRole;
  permissions?: Permission[];
  status: UserStatus;
  emailVerified: boolean;
  tokenBalance?: number;
  fraudRiskLevel?: string;
  trustScore?: number;
  payoutsEnabled?: boolean;
  subscription: {
    status: SubscriptionStatus | string;
    currentPeriodEnd?: string | null;
    nextInvoiceDate?: string | null;
    paymentMethodBrand?: string | null;
    last4?: string | null;
    last4Digits?: string | null;
    cancelAtPeriodEnd?: boolean;
  };
  creatorProfile?: import('../domain/creator/ICreator').ICreator;
}

export interface FollowStatus {
  following: boolean;
  followers: number;
}

export interface AuthContextType {
  user: User | null;
  token: string | null;
  loading: boolean;
  isLoading: boolean;
  authLoading: boolean;
  isInitialized: boolean;
  isAuthenticated: boolean;
  hasPremiumAccess: () => boolean;
  subscriptionStatus: string;
  tokenBalance: number;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  bootstrap: () => Promise<void>;
  refresh: () => Promise<void>;
  refreshTokenBalance: () => Promise<number>;
  refreshSubscription: () => Promise<void>;
}

export interface CreatorPost {
  id: string;
  creatorId: number;
  displayName?: string;
  username?: string;
  avatarUrl?: string;
  title: string;
  content: string;
  createdAt: string;
  likeCount?: number;
  likedByMe?: boolean;
  isLocked?: boolean;
  type?: 'FREE' | 'PAID';
  price?: number;
}


export interface ExplorePost {
  postId: string;
  content: string;
  createdAt: string;
  creatorId: number;
  creatorDisplayName: string;
  creatorUsername: string;
  creatorProfileImageUrl?: string;
}


export interface SubscriptionTier {
  id: string;
  name: string;
  price: number;
  description: string;
}

export interface LoginResponse {
  token: string;
  refreshToken: string;
  user: {
    id: string;
    email: string;
    role: string;
    adminRole?: AdminRole;
    permissions?: Permission[];
  };
}

export interface CreatorEarningsDashboard {
  availableBalance: number;
  totalEarnings: number;
  pendingBalance: number;
  totalFees: number;
  lastPayoutDate: string | null;
}

export interface CreatorTip {
  id: string;
  amount: number;
  fromUserId: string;
  createdAt: string;
}

export interface TipEvent {
  amount: number
  username: string
  animationType: string
  rarity?: 'common' | 'rare' | 'epic' | 'legendary'
  soundProfile?: 'common' | 'rare' | 'epic' | 'legendary'
  giftId?: string
  senderUsername?: string
  gift?: {
    name: string
    rarity: 'common' | 'rare' | 'epic' | 'legendary'
    animationType: string
    soundProfile?: string
  }
}

export interface LiveStreamInfo {
  id: string;
  username: string;
  viewerCount: number;
  startedAt: string;
  userId: number;
  creatorId: number;
  slowMode?: boolean;
  fraudRiskScore?: number;
  messageRate?: number;
  privateActive?: boolean;
  privatePricePerMinute?: number | null;
  spyEnabled?: boolean;
  activeSpyCount?: number;
}


export interface AdminRealtimeEventDTO {
  type: string;
  eventType: string;
  message: string;
  timestamp: string;
  severity?: string;
  userId?: number;
  streamId?: string;
  metadata: Record<string, any>;
}

export interface StreamRiskStatus {
  streamId: string;
  creatorId: number;
  creatorUsername: string;
  viewerCount: number;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  viewerSpike: boolean;
  suspiciousTips: boolean;
  chatSpam: boolean;
  newAccountCluster: boolean;
  riskScore: number;
}

export interface MediasoupWorkerStats {
  workerId: string;
  cpuUsage: number;
  memoryUsage: number;
}

export interface MediasoupGlobalStats {
  routers: number;
  transports: number;
  producers: number;
  consumers: number;
}

export interface MediasoupStats {
  workers: MediasoupWorkerStats[];
  global: MediasoupGlobalStats;
}

export interface FraudDashboardMetrics {
  unresolvedSignals: number;
  criticalSignals: number;
  highSignals: number;
  enforcementLast24h: number;
  newAccountTippingHigh?: number;
  newAccountTippingMedium?: number;
  newAccountTipCluster?: number;
  rapidTipRepeats?: number;
}

export interface FraudSignal {
  id: string;
  userId: number;
  userEmail: string;
  creatorId?: number;
  creatorEmail?: string;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  type: string;
  reason: string;
  score?: number;
  createdAt: string;
  resolved: boolean;
}
