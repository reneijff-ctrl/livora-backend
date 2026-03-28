import apiClient from './apiClient';

export interface PrivateSettings {
  creatorId: number;
  enabled: boolean;
  pricePerMinute: number;
  allowSpyOnPrivate: boolean;
  spyPricePerMinute: number;
  maxSpyViewers: number | null;
}

export const getPrivateSettings = () =>
  apiClient.get<PrivateSettings>('/private-settings');

export const getPrivateSettingsByCreator = (creatorId: number) =>
  apiClient.get<PrivateSettings>(`/private-settings/by-creator/${creatorId}`);

export const updatePrivateSettings = (data: {
  enabled: boolean;
  pricePerMinute: number;
  allowSpyOnPrivate?: boolean;
  spyPricePerMinute?: number;
  maxSpyViewers?: number | null;
}) =>
  apiClient.patch<PrivateSettings>('/private-settings', data);
