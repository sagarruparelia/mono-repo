import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMemberId, usePersona, useOperatorInfo, useDependents, useUser } from '@mono-repo/shared-state';
import { ProfileApp } from '@mono-repo/mfe-profile';
import styles from './routes.module.css';

/**
 * Profile page - uses ProfileApp React component directly
 * Session context (memberId, persona, operatorInfo) comes from auth store
 */
export function Profile() {
  const user = useUser();
  const sessionMemberId = useMemberId();
  const persona = usePersona();
  const dependents = useDependents();
  const { operatorId, operatorName } = useOperatorInfo();

  // For parent persona, allow selecting a dependent
  const [selectedMemberId, setSelectedMemberId] = useState<string | null>(null);
  const memberId = selectedMemberId || sessionMemberId || '';

  // Guard: Don't render MFE without valid session
  if (!sessionMemberId) {
    return (
      <div className={styles.page}>
        <div className={styles.error}>Session not available. Please log in.</div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <header className={styles.pageHeader}>
        <Link to="/app" className={styles.backLink}>
          Back to Dashboard
        </Link>
        <h1>Profile</h1>
      </header>

      {persona === 'parent' && dependents && dependents.length > 0 && (
        <div className={styles.dependentSelector}>
          <label htmlFor="profile-member-select">Viewing profile for:</label>
          <select
            id="profile-member-select"
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
        <ProfileApp
          memberId={memberId}
          persona={persona || 'individual'}
          operatorId={operatorId}
          operatorName={operatorName}
        />
      </div>
    </div>
  );
}

export default Profile;
