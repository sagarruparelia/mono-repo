// Library entry point for internal (React) usage
// Import this when using MFE within the same monorepo

export { SummaryApp } from './app/SummaryApp';
export type { SummaryAppProps } from './app/SummaryApp';

// Re-export types from shared-state for convenience
export type { SummaryData } from '@mono-repo/shared-state';
