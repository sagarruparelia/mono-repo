// Types
export type {
  Persona,
  HsidPersona,
  ProxyPersona,
  User,
  AuthContext,
  MfeProps,
} from './types/persona';

// Auth Store
export {
  useAuthStore,
  useIsAuthenticated,
  usePersona,
  useUser,
  useDependents,
  useIsHsidUser,
  useIsProxyUser,
  useMemberId,
  useOperatorInfo,
} from './stores/auth.store';

// API Client
export { api, createApiClient, ApiClient } from './api/client';
export type { ApiResponse, ApiError } from './api/client';
export { ApiClientProvider, useApiClient } from './api/ApiClientContext';

// Query Client
export { queryClient, createQueryClient } from './queries/queryClient';

// User Queries
export { userKeys, useUserProfile, useSessionInfo, useLogout } from './queries/user.queries';
export type { UserProfile, SessionInfo } from './queries/user.queries';

// Summary Queries (MFE)
export { summaryKeys, useSummary } from './queries/summary.queries';
export type { SummaryData } from './queries/summary.queries';

// Profile Queries (MFE)
export { profileKeys, useProfile, useUpdateProfile } from './queries/profile.queries';
export type { ProfileData, ProfileUpdatePayload } from './queries/profile.queries';
