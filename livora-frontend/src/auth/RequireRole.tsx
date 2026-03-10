import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './useAuth';

interface RequireRoleProps {
  children: React.ReactNode;
  role: string | string[];
}

/**
 * A wrapper component that restricts access based on user role.
 * If the user does not have the required role, they are redirected to the appropriate fallback.
 * 
 * @param children The components to render if the role matches
 * @param role The required role (e.g., 'ADMIN', 'CREATOR') or an array of roles
 */
const RequireRole: React.FC<RequireRoleProps> = ({ children, role }) => {
  const { user, isAuthenticated, isInitialized } = useAuth();
  const location = useLocation();

  if (!isInitialized) {
    return null;
  }

  // If not authenticated, redirect to login with current location
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  const userRole = user?.role;
  const requiredRoles = Array.isArray(role) ? role : [role];
  
  // ADMIN role always has access
  const hasRole = userRole === 'ADMIN' || 
                 requiredRoles.some(r => userRole === r);

  if (!hasRole) {
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
};

export default RequireRole;
