import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMemberId, useDependents, usePersona, useOperatorInfo, useUser } from '@mono-repo/shared-state';
import { SummaryApp } from '@mono-repo/mfe-summary';
import styles from './routes.module.css';

/**
 * Summary page - uses SummaryApp React component directly
 * Session context (memberId, persona, operatorInfo) comes from auth store
 */
export function Summary() {
  const user = useUser();
  const sessionMemberId = useMemberId();
  const persona = usePersona();
  const dependents = useDependents();
  const { operatorId, operatorName } = useOperatorInfo();

  // For parent persona, allow selecting a dependent
  const [selectedMemberId, setSelectedMemberId] = useState<string | null>(null);
  const memberId = selectedMemberId || sessionMemberId || '';

  return (
    <div className={styles.page}>
      <header className={styles.pageHeader}>
        <Link to="/app" className={styles.backLink}>
          Back to Dashboard
        </Link>
        <h1>Summary</h1>
      </header>

      {persona === 'parent' && dependents && dependents.length > 0 && (
        <div className={styles.dependentSelector}>
          <label>Viewing summary for:</label>
          <select
            className={styles.select}
            value={selectedMemberId || user?.sub || ''}
            onChange={(e) => setSelectedMemberId(e.target.value)}
          >
            <option value={user?.sub || ''}>Myself</option>
            {dependents.map((dep) => (
              <option key={dep} value={dep}>
                Dependent: {dep}
              </option>
            ))}
          </select>
        </div>
      )}

      <div className={styles.mfeContainer}>
        <SummaryApp
          memberId={memberId}
          persona={persona || 'individual'}
          operatorId={operatorId}
          operatorName={operatorName}
        />
      </div>
    </div>
  );
}

export default Summary;
