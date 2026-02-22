import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from './useAuth';

interface RequireRoleProps {
  children: React.ReactNode;
  role: string;
}

/**
 * A wrapper component that restricts access based on user role.
 * If the user does not have the required role, they are redirected to the dashboard.
 * 
 * @param children The components to render if the role matches
 * @param role The required role (e.g., 'ADMIN', 'CREATOR')
 */
const RequireRole: React.FC<RequireRoleProps> = ({ children, role }) => {
  const { user, isAuthenticated, isInitialized } = useAuth();

  if (!isInitialized) {
    return null;
  }

  // If not authenticated at all, the ProtectedRoute guard should have handled it,
  // but as a fallback we redirect to login or dashboard.
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const userRole = user?.role;
  
  // Check for both the raw role and the 'ROLE_' prefixed version if necessary,
  // though the authStore and AuthContext seem to normalize it or use specific strings.
  // Based on Dashboard.tsx: user.role === 'CREATOR' || user.role === 'ROLE_CREATOR'
  const hasRole = userRole === role || userRole === `ROLE_${role}`;

  if (!hasRole) {
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
};

export default RequireRole;
