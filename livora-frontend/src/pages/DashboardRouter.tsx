import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { getDashboardRouteByRole } from '../store/authStore';

/**
 * DashboardRouter component.
 * Reads the authenticated user role and redirects to the appropriate specific area (Viewer Hub, Creator Dashboard, etc.).
 */
const DashboardRouter: React.FC = () => {
  const { user } = useAuth();

  return <Navigate to={getDashboardRouteByRole(user?.role)} replace />;
};

export default DashboardRouter;
