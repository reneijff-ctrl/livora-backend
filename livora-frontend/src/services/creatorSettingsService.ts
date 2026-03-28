import apiClient from '../api/apiClient';

export interface CreatorProfileSettings {
  displayName: string;
  username: string;
  bio?: string;
  avatarUrl?: string;
  bannerUrl?: string;
  gender?: string;
  interestedIn?: string | string[];
  languages?: string | string[];
  location?: string;
  state?: string;
  bodyType?: string;
  ethnicity?: string;
  eyeColor?: string;
  hairColor?: string;
  heightCm?: number;
  weightKg?: number;
  onlyfansUrl?: string;
  throneUrl?: string;
  wishlistUrl?: string;
  twitterUrl?: string;
  instagramUrl?: string;
  showAge: boolean;
  showLocation: boolean;
  locationVisibility?: 'hidden' | 'country' | 'full' | 'custom';
  customLocation?: string;
  showLanguages: boolean;
  showBodyType: boolean;
  showEthnicity: boolean;
  showHeightWeight: boolean;
}

export const getCreatorProfile = () => apiClient.get<CreatorProfileSettings>("/creator/profile");

export const updateCreatorProfile = (data: CreatorProfileSettings) => {
  // Convert arrays to comma-separated strings for backend compatibility
  const payload = {
    ...data,
    interestedIn: Array.isArray(data.interestedIn) ? data.interestedIn.join(', ') : data.interestedIn,
    languages: Array.isArray(data.languages) ? data.languages.join(', ') : data.languages,
  };
  return apiClient.put("/creator/profile", payload);
};

// Alias for updateCreatorProfile to match requested naming
export const updateProfile = updateCreatorProfile;
