import { createContext, useContext, useMemo, type ReactNode } from 'react';
import { ApiClient, createApiClient } from './client';

/**
 * Context for providing API client to hooks
 * Allows web components to have isolated API clients
 */
const ApiClientContext = createContext<ApiClient | null>(null);

interface ApiClientProviderProps {
  children: ReactNode;
  /** Optional service base URL (defaults to same-origin) */
  serviceBaseUrl?: string;
}

/**
 * Provider for API client
 * - Internal usage (web-cl): Use without serviceBaseUrl for same-origin
 * - External usage (web components): Pass serviceBaseUrl for proxy routing
 */
export function ApiClientProvider({ children, serviceBaseUrl }: ApiClientProviderProps) {
  const client = useMemo(
    () => createApiClient(serviceBaseUrl || ''),
    [serviceBaseUrl]
  );

  return (
    <ApiClientContext.Provider value={client}>
      {children}
    </ApiClientContext.Provider>
  );
}

/**
 * Hook to get API client from context
 * Falls back to default client if no provider (for backwards compatibility)
 */
export function useApiClient(): ApiClient {
  const contextClient = useContext(ApiClientContext);
  if (!contextClient) {
    // Fallback to default client (same-origin)
    // This allows hooks to work without explicit provider
    return createApiClient('');
  }
  return contextClient;
}
