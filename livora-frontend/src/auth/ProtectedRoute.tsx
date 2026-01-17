import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { authStore } from '../store/authStore';

interface ProtectedRouteProps {
  children: React.ReactNode;
  requiredRole?: string;
  fallbackPath?: string;
}

const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, requiredRole, fallbackPath = '/forbidden' }) => {
  const location = useLocation();
  const isAuthenticated = authStore.isAuthenticated;

  if (!isAuthenticated) {
    // Redirect to /login but save the current location they were trying to go to
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (requiredRole && !authStore.hasRole(requiredRole)) {
    // If authenticated but doesn't have the required role, redirect to fallback path
    return <Navigate to={fallbackPath} replace />;
  }

  return <>{children}</>;
};

export default ProtectedRoute;
