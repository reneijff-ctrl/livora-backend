import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from './useAuth';
import { getDashboardRouteByRole } from '../store/authStore';
import Loader from '../components/Loader';

interface PublicOnlyRouteProps {
  children: React.ReactNode;
}

/**
 * A component that restricts access to users who are already authenticated.
 * If authenticated, it redirects to the home page.
 */
const PublicOnlyRoute: React.FC<PublicOnlyRouteProps> = ({ children }) => {
  const { user, isAuthenticated, isInitialized } = useAuth();

  if (!isInitialized) {
    return <Loader />;
  }

  if (isAuthenticated) {
    return <Navigate to={getDashboardRouteByRole(user?.role)} replace />;
  }

  return <>{children}</>;
};

export default PublicOnlyRoute;
