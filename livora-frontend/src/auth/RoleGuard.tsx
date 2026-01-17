import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './useAuth';
import Loader from '../components/Loader';

interface RoleGuardProps {
  allowedRoles: Array<'USER' | 'PREMIUM' | 'ADMIN' | 'CREATOR'>;
}

const RoleGuard: React.FC<RoleGuardProps> = ({ allowedRoles }) => {
  const { user, isAuthenticated, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return <Loader />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (user && !allowedRoles.includes(user.role)) {
    return <Navigate to="/403" replace />;
  }

  return <Outlet />;
};

export default RoleGuard;
