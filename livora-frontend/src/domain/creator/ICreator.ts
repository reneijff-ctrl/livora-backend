export interface ICreator {
  id: number;
  creatorId: number;
  userId: number;
  displayName: string;
  username: string;
  avatarUrl?: string | null;
  profileImage?: string | null;
  activeStream?: {
    thumbnailUrl?: string | null;
    description?: string | null;
    goal?: {
      title: string;
      targetAmount: number;
      currentAmount: number;
      goalTitle?: string;
      goalTargetTokens?: number;
      goalCurrentTokens?: number;
    } | null | string;
  };
  bannerUrl?: string | null;
  isOnline: boolean;
  online: boolean;
  isLive?: boolean;
  viewerCount?: number;
  followersCount: number;
  rating?: number;
  streamCount?: number;

  // Additional fields used across the app
  bio?: string;
  status?: string;
  visibility?: "PUBLIC" | "PRIVATE";
  isOwner?: boolean;
  followedByCurrentUser?: boolean;
  postCount?: number;
  createdAt?: string;
  category?: string;
  country?: string;
  socialLinks?: Record<string, string>;

  // Profile Details
  realName?: string;
  gender?: string;
  interestedIn?: string;
  languages?: string;
  location?: string;
  bodyType?: string;
  ethnicity?: string;
  eyeColor?: string;
  hairColor?: string;
  heightCm?: number;
  weightKg?: number;
  birthDate?: string;
  onlyfansUrl?: string;
  throneUrl?: string;
  wishlistUrl?: string;
  twitterUrl?: string;
  instagramUrl?: string;

  // Visibility Settings
  showAge?: boolean;
  showLocation?: boolean;
  showLanguages?: boolean;
  showBodyType?: boolean;
  showEthnicity?: boolean;
  showHeightWeight?: boolean;

  // Phase 1: Explore card enrichment
  followerCount?: number;
  streamTitle?: string;

  // Phase 2: Stream metadata for explore filters/cards
  streamStartedAt?: string | null;
  isPaid?: boolean;
  admissionPrice?: number | null;
  streamCategory?: string | null;
}
