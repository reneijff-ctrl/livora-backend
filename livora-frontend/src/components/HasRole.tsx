import React from 'react';
import { useAuth } from '../auth/useAuth';
import { User } from '../types';

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

  const requiredRoles = Array.isArray(role) ? role : [role];
  
  if (user.role === 'ADMIN' || requiredRoles.includes(user.role)) {
    return <>{children}</>;
  }

  return <>{fallback}</>;
};

export default HasRole;
