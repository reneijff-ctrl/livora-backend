import { ICreator } from "@/domain/creator/ICreator";

/**
 * Helper to ensure a value is always a safe primitive string.
 * - return '' if null or undefined
 * - if value is an array -> join with ', '
 * - if value is an object -> Object.values(value).join(', ')
 * - otherwise return String(value)
 */
function safeString(value: any): string {
  if (value === null || value === undefined) {
    return '';
  }
  if (Array.isArray(value)) {
    return value.join(', ');
  }
  if (typeof value === 'object') {
    return Object.values(value).join(', ');
  }
  return String(value);
}

/**
 * Helper to ensure a value is always a safe primitive number.
 * - convert using Number(value)
 * - if NaN return 0
 */
function safeNumber(value: any): number {
  const num = Number(value);
  return isNaN(num) ? 0 : num;
}

/**
 * Represents a normalized creator profile containing only primitives.
 */
export type SafeCreatorProfile = {
  id: number;
  creatorId: number;
  userId: number;
  username: string;
  displayName: string;
  avatarUrl: string;
  bannerUrl: string;
  profileImage: string;
  bio: string;
  gender: string;
  languages: string;
  location: string;
  interestedIn: string;
  rating: number;
  followers: number;
  posts: number;
  likes: number;
  isOnline: boolean;
  online: boolean;
  isLive: boolean;
  viewerCount: number;
  followersCount: number;
  streamCount: number;
  postCount: number;
  status: string;
  visibility: string;
  isOwner: boolean;
  followedByCurrentUser: boolean;
  createdAt: string;
  category: string;
  realName: string;
  bodyType: string;
  ethnicity: string;
  eyeColor: string;
  hairColor: string;
  heightCm: number;
  weightKg: number;
  birthDate: string;
  onlyfansUrl: string;
  throneUrl: string;
  wishlistUrl: string;
  twitterUrl: string;
  instagramUrl: string;
  showAge: boolean;
  showLocation: boolean;
  showLanguages: boolean;
  showBodyType: boolean;
  showEthnicity: boolean;
  showHeightWeight: boolean;
  activeStream: string;
  socialLinks: string;
};

export function adaptCreator(raw: any): ICreator {
  if (!raw) {
    return {
      id: 0,
      creatorId: 0,
      userId: 0,
      username: "",
      displayName: "Creator",
      avatarUrl: "",
      profileImage: "",
      bannerUrl: "",
      bio: "",
      gender: "",
      languages: "",
      location: "",
      interestedIn: "",
      rating: 0,
      followers: 0,
      posts: 0,
      likes: 0,
      isOnline: false,
      online: false,
      isLive: false,
      viewerCount: 0,
      followersCount: 0,
      streamCount: 0,
      postCount: 0,
      status: "DRAFT",
      visibility: "PUBLIC",
      isOwner: false,
      followedByCurrentUser: false,
      createdAt: "",
      category: "",
      realName: "Creator",
      bodyType: "",
      ethnicity: "",
      eyeColor: "",
      hairColor: "",
      heightCm: 0,
      weightKg: 0,
      birthDate: "",
      onlyfansUrl: "",
      throneUrl: "",
      wishlistUrl: "",
      twitterUrl: "",
      instagramUrl: "",
      showAge: true,
      showLocation: true,
      showLanguages: true,
      showBodyType: true,
      showEthnicity: true,
      showHeightWeight: true,
      activeStream: "",
      socialLinks: ""
    } as any as ICreator;
  }

  const result: SafeCreatorProfile = {
    id: safeNumber(raw.creatorId || raw.id || raw.creator_id || raw.userId || 0),
    creatorId: safeNumber(raw.creatorId || raw.id || raw.creator_id || raw.userId || 0),
    userId: safeNumber(raw.userId || raw.id || 0),
    username: safeString((raw.username && raw.username !== "unknown") ? raw.username : (raw.creatorUsername && raw.creatorUsername !== "unknown") ? raw.creatorUsername : (raw.id || raw.userId || raw.creatorId ? `user_${raw.id || raw.userId || raw.creatorId}` : "")),
    displayName: safeString(raw.displayName ?? raw.creatorDisplayName ?? ((raw.username && raw.username !== "unknown") ? raw.username : (raw.creatorUsername && raw.creatorUsername !== "unknown") ? raw.creatorUsername : "Creator")),

    avatarUrl: safeString(
      raw.avatarUrl ??
      raw.profileImageUrl ??
      raw.profileImage ??
      raw.creatorProfileImageUrl ??
      raw.creator_profile_image_url
    ),
    profileImage: safeString(
      raw.profileImage ??
      raw.avatarUrl ??
      raw.profileImageUrl ??
      raw.creatorProfileImageUrl ??
      raw.creator_profile_image_url
    ),
    bannerUrl: safeString(
      raw.bannerUrl ??
      raw.profileBannerUrl ??
      raw.bannerImageUrl ??
      raw.creatorProfileBannerUrl ??
      raw.creator_profile_banner_url
    ),

    bio: safeString(raw.bio ?? raw.shortBio ?? raw.creatorBio ?? raw.short_bio ?? ""),
    isOnline: Boolean(raw.isOnline ?? raw.creatorIsOnline ?? raw.is_online ?? raw.online),
    online: Boolean(raw.online ?? raw.isOnline ?? raw.creatorIsOnline ?? raw.is_online),
    isLive: Boolean(raw.isLive ?? raw.isOnline ?? raw.online ?? false),
    viewerCount: safeNumber(raw.viewerCount ?? raw.viewer_count ?? 0),
    followersCount: safeNumber(
      raw.followersCount ??
      raw.followerCount ??
      raw.totalFollowers ??
      raw.followers ??
      raw.creatorFollowersCount ??
      raw.follower_count ??
      raw.followers_count ??
      0
    ),
    followers: safeNumber(
      raw.followersCount ??
      raw.followerCount ??
      raw.totalFollowers ??
      raw.followers ??
      raw.creatorFollowersCount ??
      raw.follower_count ??
      raw.followers_count ??
      0
    ),
    postCount: safeNumber(raw.postCount ?? raw.posts ?? 0),
    posts: safeNumber(raw.postCount ?? raw.posts ?? 0),
    likes: safeNumber(raw.likes ?? 0),
    rating: safeNumber(raw.rating ?? 0),
    streamCount: safeNumber(raw.streamCount ?? 0),

    visibility: safeString(raw.visibility ?? "PUBLIC"),
    status: safeString(raw.status || raw.profileStatus || "DRAFT"),

    isOwner: Boolean(raw.isOwner ?? false),
    followedByCurrentUser: Boolean(raw.followedByCurrentUser ?? false),
    createdAt: safeString(raw.createdAt ?? ""),
    category: safeString(raw.category ?? ""),

    // Profile Details
    realName: safeString(raw.realName ?? raw.displayName),
    gender: safeString(raw.gender),
    languages: safeString(raw.languages),
    location: safeString(raw.location),
    interestedIn: safeString(raw.interestedIn),
    bodyType: safeString(raw.bodyType ?? ""),
    ethnicity: safeString(raw.ethnicity ?? ""),
    eyeColor: safeString(raw.eyeColor ?? ""),
    hairColor: safeString(raw.hairColor ?? ""),
    heightCm: safeNumber(raw.heightCm ?? 0),
    weightKg: safeNumber(raw.weightKg ?? 0),
    birthDate: safeString(raw.birthDate ?? ""),
    onlyfansUrl: safeString(raw.onlyfansUrl ?? ""),
    throneUrl: safeString(raw.throneUrl ?? ""),
    wishlistUrl: safeString(raw.wishlistUrl ?? ""),
    twitterUrl: safeString(raw.twitterUrl ?? ""),
    instagramUrl: safeString(raw.instagramUrl ?? ""),

    // Visibility Settings
    showAge: Boolean(raw.showAge ?? true),
    showLocation: Boolean(raw.showLocation ?? true),
    showLanguages: Boolean(raw.showLanguages ?? true),
    showBodyType: Boolean(raw.showBodyType ?? true),
    showEthnicity: Boolean(raw.showEthnicity ?? true),
    showHeightWeight: Boolean(raw.showHeightWeight ?? true),

    // Phase 1: Explore card enrichment
    followerCount: safeNumber(raw.followerCount ?? raw.followersCount ?? 0),
    streamTitle: safeString(raw.streamTitle ?? ""),

    // Phase 2: Stream metadata
    streamStartedAt: raw.streamStartedAt ?? null,
    isPaid: Boolean(raw.isPaid ?? false),
    admissionPrice: raw.admissionPrice != null ? Number(raw.admissionPrice) : null,
    streamCategory: raw.streamCategory ?? null,

    // Ensure NO objects or undefined values are returned
    activeStream: "",
    socialLinks: ""
  };

  // Build nested activeStream from flat API fields for CreatorCard compatibility
  const thumbnailUrl = raw.activeStreamThumbnailUrl ?? raw.activeStream?.thumbnailUrl ?? null;
  const goalTitle = raw.goalTitle ?? raw.activeStream?.goal?.title ?? null;
  const goalTarget = raw.goalTargetTokens ?? raw.activeStream?.goal?.targetAmount ?? null;
  const goalCurrent = raw.goalCurrentTokens ?? raw.activeStream?.goal?.currentAmount ?? null;
  const streamDescription = raw.streamTitle ?? raw.activeStreamDescription ?? raw.activeStream?.description ?? null;

  const hasGoal = goalTitle != null && goalTarget != null;
  const hasActiveStreamData = thumbnailUrl || hasGoal || streamDescription;

  const activeStream = hasActiveStreamData ? {
    thumbnailUrl: thumbnailUrl || null,
    description: streamDescription || null,
    goal: hasGoal ? {
      title: String(goalTitle),
      targetAmount: Number(goalTarget) || 0,
      currentAmount: Number(goalCurrent) || 0,
    } : null,
  } : undefined;

  const finalResult = {
    ...result,
    activeStream,
  };

  return finalResult as any as ICreator;
}
