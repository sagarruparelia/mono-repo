import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useUser, useDependents, usePersona } from '@mono-repo/shared-state';
import { SummaryApp } from '@mono-repo/mfe-summary';
import styles from './routes.module.css';

/**
 * Summary page - uses SummaryApp React component directly
 * (No web component overhead for internal usage)
 */
export function Summary() {
  const user = useUser();
  const persona = usePersona();
  const dependents = useDependents();

  // For HSID users, member-id is their own sub
  // For parent persona, they can select a dependent
  const [selectedMemberId, setSelectedMemberId] = useState<string | null>(null);
  const memberId = selectedMemberId || user?.sub || '';

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
        />
      </div>
    </div>
  );
}

export default Summary;
