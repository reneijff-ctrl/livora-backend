import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './useAuth';
import Loader from '../components/Loader';

interface PremiumGuardProps {
  children?: React.ReactNode;
}

const PremiumGuard: React.FC<PremiumGuardProps> = ({ children }) => {
  const { user, isAuthenticated, isLoading, hasPremiumAccess } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return <Loader />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (!hasPremiumAccess()) {
    return <Navigate to="/upgrade" replace />;
  }

  return children ? <>{children}</> : <Outlet />;
};

export default PremiumGuard;
