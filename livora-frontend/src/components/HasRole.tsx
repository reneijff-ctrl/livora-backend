import React from 'react';
import { useAuth } from '../auth/useAuth';
import { User } from '../auth/AuthContext';

interface HasRoleProps {
  children: React.ReactNode;
  role: User['role'] | User['role'][];
  fallback?: React.ReactNode;
}

/**
 * A UI guard component that only renders its children if the current user has the required role(s).
 * ADMIN role always has access.
 */
const HasRole: React.FC<HasRoleProps> = ({ children, role, fallback = null }) => {
  const { user, isAuthenticated } = useAuth();

  if (!isAuthenticated || !user) {
    return <>{fallback}</>;
  }

  // ADMIN bypass
  if (user.role === 'ADMIN') {
    return <>{children}</>;
  }

  const requiredRoles = Array.isArray(role) ? role : [role];
  
  if (requiredRoles.includes(user.role)) {
    return <>{children}</>;
  }

  return <>{fallback}</>;
};

export default HasRole;
