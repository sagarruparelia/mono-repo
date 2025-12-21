// Types
export type {
  Persona,
  HsidPersona,
  ProxyPersona,
  User,
  AuthContext,
  MfeProps,
} from './types/persona';

export type {
  SessionCheckResponse,
  SessionInfoResponse,
} from './types/session';

// Auth Store
export {
  useAuthStore,
  useIsAuthenticated,
  usePersona,
  useUser,
  useDependents,
  useIsHsidUser,
  useIsProxyUser,
  useMemberEid,
  useOperatorInfo,
  useSelectedChildId,
  useEffectiveMemberEid,
} from './stores/auth.store';

// API Client
export { api, createApiClient, ApiClient } from './api/client';
export type { ApiResponse, ApiError } from './api/client';
export { ApiClientProvider, useApiClient } from './api/ApiClientContext';

// Query Client
export { queryClient, createQueryClient } from './queries/queryClient';

// User Queries
export {
  userKeys,
  useUserProfile,
  useSessionInfo,
  useLogout,
  useUserInfo,
  useDependentsMetadata,
} from './queries/user.queries';
export type { UserProfile, UserInfo, DependentMetadata } from './queries/user.queries';

// Summary Queries (MFE)
export { summaryKeys, useSummary } from './queries/summary.queries';
export type { SummaryData } from './queries/summary.queries';

// Profile Queries (MFE)
export { profileKeys, useProfile, useUpdateProfile } from './queries/profile.queries';
export type { ProfileData, ProfileUpdatePayload } from './queries/profile.queries';

// Document Queries (MFE)
export {
  documentKeys,
  useDocuments,
  useDocument,
  useUploadDocument,
  useDeleteDocument,
  getDocumentDownloadUrl,
  formatFileSize,
} from './queries/document.queries';
export type { DocumentData, DocumentUploadRequest, DocumentType } from './queries/document.queries';

// Health Queries (MFE)
export {
  healthKeys,
  useImmunizations,
  useAllergies,
  useMedications,
} from './queries/health.queries';
export type { Immunization, Allergy, Medication } from './queries/health.queries';

// Components
export { ErrorBoundary } from './components/ErrorBoundary';
export { ChildSelector } from './components/ChildSelector';
