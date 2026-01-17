import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import Loader from './Loader';

interface PremiumRouteProps {
  children: React.ReactNode;
}

const PremiumRoute: React.FC<PremiumRouteProps> = ({ children }) => {
  const { isAuthenticated, hasPremiumAccess, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return <Loader />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (!hasPremiumAccess()) {
    return <Navigate to="/subscription" replace />;
  }

  return <>{children}</>;
};

export default PremiumRoute;
