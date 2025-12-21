import { Navigate } from '@tanstack/react-router';

/**
 * Legacy Summary route - redirects to Health Summary
 * Kept for backward compatibility
 */
export function Summary() {
  return <Navigate to="/app/health-summary" />;
}

export default Summary;
