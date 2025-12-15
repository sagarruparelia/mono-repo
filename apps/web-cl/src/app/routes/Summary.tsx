import { useUser, useDependents, usePersona } from '@mono-repo/shared-state';
import { Link } from 'react-router-dom';
import styles from './routes.module.css';

/**
 * Summary page - embeds mfe-summary
 */
export function Summary() {
  const user = useUser();
  const persona = usePersona();
  const dependents = useDependents();

  // For HSID users, member-id is their own sub
  // For parent persona, they can select a dependent
  const memberId = user?.sub || '';

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
          <select className={styles.select}>
            <option value={memberId}>Myself</option>
            {dependents.map((dep) => (
              <option key={dep} value={dep}>
                Dependent: {dep}
              </option>
            ))}
          </select>
        </div>
      )}

      <div className={styles.mfeContainer}>
        <mfe-summary member-id={memberId} persona={persona || 'individual'} />
      </div>
    </div>
  );
}

export default Summary;
