import { createRouter } from '@tanstack/react-router';
import { routeTree } from './routeTree.gen';
import { queryClient } from '@mono-repo/shared-state';

// Create the router instance with context
export const router = createRouter({
  routeTree,
  context: {
    queryClient,
  },
  defaultPreload: 'intent',
  defaultPendingComponent: () => (
    <div className="app-loading" role="status" aria-live="polite">
      Loading...
    </div>
  ),
});

// Type registration for type-safe navigation
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
