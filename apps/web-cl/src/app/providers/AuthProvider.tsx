import { ReactNode, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  useAuthStore,
  useSessionInfo,
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
  // Track sessionExpiry via selector to properly re-run effect when it changes
  const sessionExpiry = useAuthStore((state) => state.sessionExpiry);
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

  // Handle session expiry - re-runs when sessionExpiry changes
  useEffect(() => {
    if (sessionExpiry) {
      const timeUntilExpiry = sessionExpiry - Date.now();
      if (timeUntilExpiry > 0) {
        const timer = setTimeout(() => {
          clearAuth();
          navigate('/');
        }, timeUntilExpiry);

        return () => clearTimeout(timer);
      }
    }
  }, [sessionExpiry, clearAuth, navigate]);

  return <>{children}</>;
}
