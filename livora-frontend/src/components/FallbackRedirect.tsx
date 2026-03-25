import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { getDashboardRouteByRole } from '../store/authStore';

/**
 * FallbackRedirect component handles unknown routes.
 * Redirects authenticated users to the dashboard and unauthenticated users to the home page.
 */
const FallbackRedirect: React.FC = () => {
  const { isAuthenticated, user } = useAuth();

  if (isAuthenticated) {
    return <Navigate to={getDashboardRouteByRole(user?.role)} replace />;
  }

  return <Navigate to="/" replace />;
};

export default FallbackRedirect;
