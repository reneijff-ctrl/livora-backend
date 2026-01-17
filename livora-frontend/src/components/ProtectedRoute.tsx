import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import Loader from './Loader';

interface ProtectedRouteProps {
  children: React.ReactNode;
  requiredRole?: 'USER' | 'PREMIUM' | 'ADMIN';
}

const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, requiredRole }) => {
  const { isAuthenticated, user, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return <Loader />;
  }

  if (!isAuthenticated) {
    // Redirect to login but save the current location they were trying to go to
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (requiredRole && user && user.role !== requiredRole && user.role !== 'ADMIN') {
    // Role not authorized - redirect to forbidden
    // Note: ADMIN has access to everything by default in our logic
    return <Navigate to="/forbidden" replace />;
  }

  return <>{children}</>;
};

export default ProtectedRoute;
