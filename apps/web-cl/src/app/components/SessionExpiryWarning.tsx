import { useState, useEffect, useCallback } from 'react';
import { api } from '@mono-repo/shared-state';
import { useSessionCheck } from '../hooks/useSessionCheck';
import styles from './SessionExpiryWarning.module.css';

const WARNING_THRESHOLD = 5 * 60; // Show warning 5 minutes before expiry

/**
 * Session expiry warning modal
 *
 * Displays a warning when session is about to expire,
 * allowing users to extend their session without losing work.
 */
export function SessionExpiryWarning() {
  const [showWarning, setShowWarning] = useState(false);
  const [extending, setExtending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { data: sessionCheck } = useSessionCheck();

  // Check if warning should be shown
  useEffect(() => {
    if (sessionCheck?.valid && sessionCheck.expiresIn !== undefined) {
      const shouldWarn = sessionCheck.expiresIn <= WARNING_THRESHOLD && sessionCheck.expiresIn > 0;
      setShowWarning(shouldWarn);

      // Clear error when session is extended (expiresIn increases)
      if (!shouldWarn) {
        setError(null);
      }
    } else {
      setShowWarning(false);
    }
  }, [sessionCheck?.expiresIn, sessionCheck?.valid]);

  const handleExtend = useCallback(async () => {
    setExtending(true);
    setError(null);

    try {
      const result = await api.post<{ refreshed: boolean }>('/api/auth/refresh');
      if (result.refreshed) {
        setShowWarning(false);
      } else {
        setError('Could not extend session. Please log in again.');
      }
    } catch (e) {
      console.error('Failed to extend session', e);
      setError('Failed to extend session. Please try again.');
    } finally {
      setExtending(false);
    }
  }, []);

  if (!showWarning) return null;

  const minutes = Math.ceil((sessionCheck?.expiresIn || 0) / 60);

  return (
    <div className={styles.overlay} role="dialog" aria-modal="true" aria-labelledby="session-warning-title">
      <div className={styles.modal}>
        <h3 id="session-warning-title" className={styles.title}>
          Session Expiring Soon
        </h3>
        <p className={styles.message}>
          Your session will expire in {minutes} minute{minutes !== 1 ? 's' : ''}.
        </p>
        {error && <p className={styles.error}>{error}</p>}
        <div className={styles.actions}>
          <button
            onClick={handleExtend}
            disabled={extending}
            className={styles.button}
          >
            {extending ? 'Extending...' : 'Stay Logged In'}
          </button>
        </div>
      </div>
    </div>
  );
}
