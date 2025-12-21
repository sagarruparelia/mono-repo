import { render, screen, waitFor } from '@testing-library/react';
import { QueryClientProvider, QueryClient } from '@tanstack/react-query';
import { RouterProvider, createMemoryHistory, createRouter } from '@tanstack/react-router';
import { routeTree } from '../routeTree.gen';

// Create a fresh query client for each test
const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

// Create a test router with memory history
const createTestRouter = () => {
  const queryClient = createTestQueryClient();
  return createRouter({
    routeTree,
    context: { queryClient },
    history: createMemoryHistory({ initialEntries: ['/'] }),
  });
};

describe('App', () => {
  it('should render the landing page successfully', async () => {
    const testRouter = createTestRouter();
    const queryClient = createTestQueryClient();

    const { baseElement } = render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={testRouter} />
      </QueryClientProvider>
    );

    expect(baseElement).toBeTruthy();

    // Wait for lazy-loaded LandingPage to render
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /sign in/i })).toBeTruthy();
    });
  });

  it('should display the Health Portal title', async () => {
    const testRouter = createTestRouter();
    const queryClient = createTestQueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={testRouter} />
      </QueryClientProvider>
    );

    // Wait for lazy-loaded LandingPage to render
    await waitFor(() => {
      expect(screen.getByText('Health Portal')).toBeTruthy();
    });
  });
});
