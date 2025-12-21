/**
 * Response from GET /api/auth/check
 * Lightweight session validation for route guards
 */
export interface SessionCheckResponse {
  valid: boolean;
  expiresIn?: number;  // seconds until session expires
  persona?: string;
}

/**
 * Response from GET /api/auth/session
 * Full session info for auth state hydration
 */
export interface SessionInfoResponse {
  valid: boolean;
  reason?: string;           // Only present if invalid: "no_session", "session_not_found"
  hsidUuid?: string;         // OIDC subject claim
  name?: string;
  email?: string;
  persona?: string;          // "individual" or "parent"
  isParent: boolean;
  dependentIds?: string[];   // Enterprise IDs of dependents
  expiresAt?: string;        // ISO timestamp
  lastActivity?: string;     // ISO timestamp
}
