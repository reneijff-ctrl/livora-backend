import React from 'react';
import ContentCard from '@/components/ContentCard';
import { ContentItem, ContentAccessLevel } from '@/api/contentService';

export interface MediaItem {
  id: string;
  thumbnail: string;
  isPremium: boolean;
  isLocked?: boolean;
  unlockPrice?: number;
  type?: string;
  mediaUrl?: string;
  title?: string;
  description?: string;
  unlocked?: boolean;
  accessLevel?: string;
}

interface CreatorMediaTabProps {
  media: MediaItem[];
  onItemClick?: (item: MediaItem) => void;
  onUnlock?: (id: string) => void;
}

const CreatorMediaTab: React.FC<CreatorMediaTabProps> = ({ media, onItemClick, onUnlock }) => {
  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
      {media.map((item) => {
        const contentItem: ContentItem = {
          id: item.id,
          title: item.title || '',
          description: item.description || '',
          thumbnailUrl: item.thumbnail,
          mediaUrl: item.mediaUrl,
          accessLevel: (item.accessLevel as ContentAccessLevel) || (item.isPremium ? 'PREMIUM' : 'FREE'),
          type: item.type as ContentItem['type'],
          unlockPriceTokens: item.unlockPrice,
          unlocked: item.unlocked,
        };

        return (
          <ContentCard
            key={item.id}
            content={contentItem}
            onUnlock={onUnlock}
            onClick={() => onItemClick?.(item)}
          />
        );
      })}
    </div>
  );
};

export default CreatorMediaTab;
