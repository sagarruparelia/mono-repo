// Library entry point for internal (React) usage
// Import this when using MFE within the same monorepo

export { ProfileApp } from './app/ProfileApp';
export type { ProfileAppProps } from './app/ProfileApp';

// Re-export types from shared-state for convenience
export type { ProfileData, ProfileUpdatePayload } from '@mono-repo/shared-state';
