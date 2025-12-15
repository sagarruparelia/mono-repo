import { StrictMode } from 'react';
import * as ReactDOM from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { queryClient } from '@mono-repo/shared-state';
import { SummaryApp } from './app/SummaryApp';

// Export web component for MFE usage
export * from './web-component';

// Standalone app rendering (for development)
const rootElement = document.getElementById('root');
if (rootElement) {
  const root = ReactDOM.createRoot(rootElement);
  root.render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <SummaryApp
          memberId="dev-member-123"
          persona="individual"
        />
      </QueryClientProvider>
    </StrictMode>
  );
}
