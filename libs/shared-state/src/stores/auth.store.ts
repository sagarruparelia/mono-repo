import { create } from 'zustand';
import { useShallow } from 'zustand/react/shallow';
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
    memberEid: string,
    persona: Persona,
    operatorId: string,
    operatorName: string
  ) => void;

  /** Clear all authentication state */
  clearAuth: () => void;

  /** Refresh session expiry (for sliding sessions) */
  refreshSession: (expiry: number) => void;

  /** Set selected child for parent persona (global child selector) */
  setSelectedChild: (childId: string | null) => void;
}

type AuthStore = AuthContext & AuthActions & {
  /** Selected child ID for parent persona (null means viewing self) */
  selectedChildId?: string | null;
};

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
          memberEid: undefined,
          operatorId: undefined,
          operatorName: undefined,
        }),

      setProxyContext: (memberEid, persona, operatorId, operatorName) =>
        set({
          isAuthenticated: true,
          memberEid,
          persona,
          operatorId,
          operatorName,
          // Clear HSID fields
          user: undefined,
          dependents: undefined,
          sessionExpiry: undefined,
        }),

      clearAuth: () => set({ ...initialState, selectedChildId: null }),

      refreshSession: (expiry) => set({ sessionExpiry: expiry }),

      setSelectedChild: (childId) => set({ selectedChildId: childId }),
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
        memberEid: state.memberEid,
        operatorId: state.operatorId,
        operatorName: state.operatorName,
        selectedChildId: state.selectedChildId,
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
  useAuthStore((state) => state.memberEid !== undefined);

/**
 * Get the effective memberEid from session
 * - HSID users: memberEid comes from user.sub
 * - Proxy users: memberEid is explicit in context
 */
export const useMemberEid = () =>
  useAuthStore((state) => state.memberEid ?? state.user?.sub);

/**
 * Get operator info for proxy users
 * Uses shallow comparison to prevent unnecessary re-renders
 */
export const useOperatorInfo = () =>
  useAuthStore(
    useShallow((state) => ({
      operatorId: state.operatorId,
      operatorName: state.operatorName,
    }))
  );

/**
 * Get the currently selected child ID (for parent persona)
 * Returns null if viewing self
 */
export const useSelectedChildId = () =>
  useAuthStore((state) => state.selectedChildId);

/**
 * Get the effective memberEid for API calls
 * - Parent with child selected: returns selectedChildId
 * - Parent with no selection: returns parent's memberEid (user.sub)
 * - Individual/other: returns their own memberEid
 */
export const useEffectiveMemberEid = () =>
  useAuthStore((state) => {
    if (state.persona === 'parent' && state.selectedChildId) {
      return state.selectedChildId;
    }
    return state.memberEid ?? state.user?.sub;
  });
