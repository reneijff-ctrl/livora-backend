import { useAuth } from '../auth/useAuth';
import { Permission } from '../types';

export function usePermissions() {
  const { user } = useAuth();

  function hasPermission(permission: Permission): boolean {
    return user?.permissions?.includes(permission) ?? false;
  }

  function hasAnyPermission(...permissions: Permission[]): boolean {
    return permissions.some((p) => hasPermission(p));
  }

  function hasAllPermissions(...permissions: Permission[]): boolean {
    return permissions.every((p) => hasPermission(p));
  }

  return { hasPermission, hasAnyPermission, hasAllPermissions };
}
