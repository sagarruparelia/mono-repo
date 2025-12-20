import { useEffectiveMemberEid, usePersona, useOperatorInfo } from '@mono-repo/shared-state';
import { HealthSummaryApp } from '@mono-repo/mfe-summary';
import styles from './routes.module.css';

/**
 * Health Summary page route
 * Uses the effective memberEid which respects the global child selector
 */
export function HealthSummary() {
  const memberEid = useEffectiveMemberEid();
  const persona = usePersona();
  const { operatorId, operatorName } = useOperatorInfo();

  if (!memberEid) {
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
          memberEid={memberEid}
          persona={persona || 'individual'}
          operatorId={operatorId}
          operatorName={operatorName}
        />
      </div>
    </div>
  );
}

export default HealthSummary;
