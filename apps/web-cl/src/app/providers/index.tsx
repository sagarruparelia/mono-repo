import { ReactNode } from 'react';
import { QueryProvider } from './QueryProvider';
import { AuthProvider } from './AuthProvider';

interface AppProvidersProps {
  children: ReactNode;
}

/**
 * Combined providers for the application
 * Order matters: QueryProvider must wrap AuthProvider since AuthProvider uses queries
 */
export function AppProviders({ children }: AppProvidersProps) {
  return (
    <QueryProvider>
      <AuthProvider>{children}</AuthProvider>
    </QueryProvider>
  );
}

export { QueryProvider } from './QueryProvider';
export { AuthProvider } from './AuthProvider';
