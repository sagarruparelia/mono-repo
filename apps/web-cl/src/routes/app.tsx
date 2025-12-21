import { createFileRoute, redirect, Outlet } from '@tanstack/react-router';
import { api, useAuthStore, SessionCheckResponse } from '@mono-repo/shared-state';
import { AppLayout } from '../app/components/AppLayout';

export const Route = createFileRoute('/app')({
  beforeLoad: async () => {
    // Check local auth state first (fast)
    const { isAuthenticated, clearAuth } = useAuthStore.getState();

    if (!isAuthenticated) {
      throw redirect({ to: '/', search: { reason: 'not_authenticated' } });
    }

    // Verify with server (authoritative)
    try {
      const session = await api.get<SessionCheckResponse>('/api/auth/check');
      if (!session.valid) {
        clearAuth();
        throw redirect({ to: '/', search: { reason: 'session_expired' } });
      }
    } catch (error) {
      // If it's already a redirect, rethrow it
      if (error instanceof Error && 'to' in error) {
        throw error;
      }
      clearAuth();
      throw redirect({ to: '/', search: { reason: 'session_error' } });
    }
  },
  pendingComponent: () => (
    <div className="auth-loading" role="status" aria-live="polite">
      <span>Verifying session...</span>
    </div>
  ),
  component: ProtectedLayout,
});

function ProtectedLayout() {
  return (
    <AppLayout>
      <Outlet />
    </AppLayout>
  );
}
