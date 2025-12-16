// Library entry point for internal (React) usage
// Import this when using MFE within the same monorepo

export { HealthSummaryApp, SummaryApp } from './app/SummaryApp';
export type { HealthSummaryAppProps } from './app/SummaryApp';

// Keep old type name for backward compatibility
export type { HealthSummaryAppProps as SummaryAppProps } from './app/SummaryApp';

// Re-export types from shared-state for convenience
export type { Immunization, Allergy, Medication } from '@mono-repo/shared-state';
