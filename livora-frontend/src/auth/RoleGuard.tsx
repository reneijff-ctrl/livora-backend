import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './useAuth';
import Loader from '../components/Loader';
import { Role } from '../types';

interface RoleGuardProps {
  allowedRoles: Role[];
}

const RoleGuard: React.FC<RoleGuardProps> = ({ allowedRoles }) => {
  const { user, isAuthenticated, isInitialized } = useAuth();
  const location = useLocation();

  if (!isInitialized) {
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
