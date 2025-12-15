import { ReactNode, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  useAuthStore,
  useSessionInfo,
  useIsAuthenticated,
} from '@mono-repo/shared-state';

interface AuthProviderProps {
  children: ReactNode;
}

/**
 * AuthProvider handles:
 * - Session state synchronization with backend
 * - Session expiry monitoring
 * - Auth state hydration from session storage
 */
export function AuthProvider({ children }: AuthProviderProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const isAuthenticated = useIsAuthenticated();
  const { setHsidAuth, clearAuth } = useAuthStore();

  // Fetch session info to verify/sync auth state
  const { data: sessionInfo, error: sessionError } = useSessionInfo();

  // Handle session sync
  useEffect(() => {
    if (sessionInfo) {
      // Session exists on server - sync local state
      setHsidAuth(
        {
          sub: sessionInfo.userId,
          name: '', // Will be populated from profile query
          email: '',
        },
        sessionInfo.persona as 'individual' | 'parent',
        sessionInfo.expiresAt
      );
    } else if (sessionError) {
      // Session invalid - clear local state
      clearAuth();
    }
  }, [sessionInfo, sessionError, setHsidAuth, clearAuth]);

  // Handle session expiry
  useEffect(() => {
    const state = useAuthStore.getState();
    if (state.sessionExpiry) {
      const timeUntilExpiry = state.sessionExpiry - Date.now();
      if (timeUntilExpiry > 0) {
        const timer = setTimeout(() => {
          clearAuth();
          navigate('/');
        }, timeUntilExpiry);

        return () => clearTimeout(timer);
      }
    }
  }, [clearAuth, navigate]);

  return <>{children}</>;
}
