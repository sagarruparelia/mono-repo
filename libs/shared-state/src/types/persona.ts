/**
 * Persona types for HSID (direct login) users
 */
export type HsidPersona = 'individual' | 'parent';

/**
 * Persona types for proxy access (via partner portals)
 */
export type ProxyPersona = 'agent' | 'config' | 'case_worker';

/**
 * Combined persona type
 */
export type Persona = HsidPersona | ProxyPersona;

/**
 * Basic user information from HSID claims
 */
export interface User {
  sub: string;
  name: string;
  email: string;
}

/**
 * Authentication context shared across the application
 */
export interface AuthContext {
  /** Whether the user is authenticated */
  isAuthenticated: boolean;

  /** Current persona (HSID or proxy) */
  persona: Persona | null;

  /** User info (HSID context only) */
  user?: User;

  /** Dependent IDs for parent persona */
  dependents?: string[];

  /** Session expiry timestamp (HSID context only) */
  sessionExpiry?: number;

  /** Target member EID - Enterprise ID (proxy context only) */
  memberEid?: string;

  /** Portal operator ID (proxy context only) */
  operatorId?: string;

  /** Portal operator display name (proxy context only) */
  operatorName?: string;
}

/**
 * Props passed to MFE web components
 */
export interface MfeProps {
  memberEid: string;
  persona: Persona;
  operatorId?: string;
  operatorName?: string;
  apiBase?: string;
}
