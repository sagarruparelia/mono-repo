import { Navigate } from 'react-router-dom';

/**
 * Legacy Summary route - redirects to Health Summary
 * Kept for backward compatibility
 */
export function Summary() {
  return <Navigate to="/app/health-summary" replace />;
}

export default Summary;
