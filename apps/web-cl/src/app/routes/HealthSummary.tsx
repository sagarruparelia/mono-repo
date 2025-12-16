import { useEffectiveMemberId, usePersona, useOperatorInfo } from '@mono-repo/shared-state';
import { HealthSummaryApp } from '@mono-repo/mfe-summary';
import styles from './routes.module.css';

/**
 * Health Summary page route
 * Uses the effective memberId which respects the global child selector
 */
export function HealthSummary() {
  const memberId = useEffectiveMemberId();
  const persona = usePersona();
  const { operatorId, operatorName } = useOperatorInfo();

  if (!memberId) {
    return (
      <div className={styles.page}>
        <div className={styles.error}>Session not available. Please log in.</div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <div className={styles.mfeContainer}>
        <HealthSummaryApp
          memberId={memberId}
          persona={persona || 'individual'}
          operatorId={operatorId}
          operatorName={operatorName}
        />
      </div>
    </div>
  );
}

export default HealthSummary;
