import { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useIsAuthenticated } from '@mono-repo/shared-state';

interface AuthGuardProps {
  children: ReactNode;
}

/**
 * AuthGuard protects routes that require authentication
 * Redirects to landing page if not authenticated
 */
export function AuthGuard({ children }: AuthGuardProps) {
  const isAuthenticated = useIsAuthenticated();
  const location = useLocation();

  if (!isAuthenticated) {
    // Redirect to landing page, preserving the intended destination
    return <Navigate to="/" state={{ from: location }} replace />;
  }

  return <>{children}</>;
}
