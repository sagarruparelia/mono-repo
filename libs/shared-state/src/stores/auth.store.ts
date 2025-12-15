import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { AuthContext, Persona, User } from '../types/persona';

/**
 * Auth store actions
 */
interface AuthActions {
  /** Set authentication state for HSID (direct login) users */
  setHsidAuth: (
    user: User,
    persona: Persona,
    expiry: number,
    dependents?: string[]
  ) => void;

  /** Set authentication context for proxy (partner portal) access */
  setProxyContext: (
    memberId: string,
    persona: Persona,
    operatorId: string,
    operatorName: string
  ) => void;

  /** Clear all authentication state */
  clearAuth: () => void;

  /** Refresh session expiry (for sliding sessions) */
  refreshSession: (expiry: number) => void;
}

type AuthStore = AuthContext & AuthActions;

const initialState: AuthContext = {
  isAuthenticated: false,
  persona: null,
};

/**
 * Zustand store for authentication state
 * Persisted to sessionStorage for tab isolation
 */
export const useAuthStore = create<AuthStore>()(
  persist(
    (set) => ({
      ...initialState,

      setHsidAuth: (user, persona, expiry, dependents) =>
        set({
          isAuthenticated: true,
          user,
          persona,
          sessionExpiry: expiry,
          dependents,
          // Clear proxy fields
          memberId: undefined,
          operatorId: undefined,
          operatorName: undefined,
        }),

      setProxyContext: (memberId, persona, operatorId, operatorName) =>
        set({
          isAuthenticated: true,
          memberId,
          persona,
          operatorId,
          operatorName,
          // Clear HSID fields
          user: undefined,
          dependents: undefined,
          sessionExpiry: undefined,
        }),

      clearAuth: () => set(initialState),

      refreshSession: (expiry) => set({ sessionExpiry: expiry }),
    }),
    {
      name: 'auth-storage',
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({
        isAuthenticated: state.isAuthenticated,
        persona: state.persona,
        user: state.user,
        dependents: state.dependents,
        sessionExpiry: state.sessionExpiry,
        memberId: state.memberId,
        operatorId: state.operatorId,
        operatorName: state.operatorName,
      }),
    }
  )
);

/**
 * Selector hooks for common auth state access
 */
export const useIsAuthenticated = () =>
  useAuthStore((state) => state.isAuthenticated);

export const usePersona = () => useAuthStore((state) => state.persona);

export const useUser = () => useAuthStore((state) => state.user);

export const useDependents = () => useAuthStore((state) => state.dependents);

export const useIsHsidUser = () =>
  useAuthStore((state) => state.user !== undefined);

export const useIsProxyUser = () =>
  useAuthStore((state) => state.memberId !== undefined);

/**
 * Get the effective memberId from session
 * - HSID users: memberId comes from user.sub
 * - Proxy users: memberId is explicit in context
 */
export const useMemberId = () =>
  useAuthStore((state) => state.memberId ?? state.user?.sub);

/**
 * Get operator info for proxy users
 */
export const useOperatorInfo = () =>
  useAuthStore((state) => ({
    operatorId: state.operatorId,
    operatorName: state.operatorName,
  }));
