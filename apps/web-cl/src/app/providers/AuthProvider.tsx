import { ReactNode, useEffect } from 'react';
import { useRouter } from '@tanstack/react-router';
import { useAuthStore, useSessionInfo } from '@mono-repo/shared-state';
import { useSessionCheck } from '../hooks/useSessionCheck';

interface AuthProviderProps {
  children: ReactNode;
}

/**
 * AuthProvider handles:
 * - Session state synchronization with backend
 * - Session expiry monitoring via useSessionCheck
 * - Auth state hydration from server session
 */
export function AuthProvider({ children }: AuthProviderProps) {
  const router = useRouter();
  const { setHsidAuth, clearAuth } = useAuthStore();

  // Full session info for initial hydration
  const { data: sessionInfo } = useSessionInfo();

  // Lightweight session check for monitoring (polls every 60s)
  const { data: sessionCheck } = useSessionCheck();

  // Hydrate auth state from full session response
  useEffect(() => {
    if (sessionInfo?.valid) {
      // Parse expiresAt to timestamp
      const expiresAt = sessionInfo.expiresAt
        ? new Date(sessionInfo.expiresAt).getTime()
        : Date.now() + 30 * 60 * 1000; // Default 30 min

      setHsidAuth(
        {
          sub: sessionInfo.hsidUuid || '',
          name: sessionInfo.name || '',
          email: sessionInfo.email || '',
        },
        sessionInfo.persona as 'individual' | 'parent',
        expiresAt,
        sessionInfo.dependentIds
      );
    } else if (sessionInfo && !sessionInfo.valid) {
      // Server explicitly says session is invalid
      clearAuth();
    }
  }, [sessionInfo, setHsidAuth, clearAuth]);

  // Handle session invalidation from lightweight check
  useEffect(() => {
    if (sessionCheck && !sessionCheck.valid) {
      clearAuth();
      router.navigate({ to: '/', search: { reason: 'session_expired' } });
    }
  }, [sessionCheck, clearAuth, router]);

  return children;
}
