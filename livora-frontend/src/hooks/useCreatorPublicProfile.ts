import { useParams } from 'react-router-dom';
import { usePublicCreator } from './usePublicCreator';

export const useCreatorPublicProfile = () => {
  const { identifier } = useParams<{ identifier: string }>();
  return usePublicCreator(identifier);
};
