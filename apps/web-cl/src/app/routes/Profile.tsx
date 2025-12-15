import { Link } from 'react-router-dom';
import { useMemberId, usePersona, useOperatorInfo } from '@mono-repo/shared-state';
import { ProfileApp } from '@mono-repo/mfe-profile';
import styles from './routes.module.css';

/**
 * Profile page - uses ProfileApp React component directly
 * Session context (memberId, persona, operatorInfo) comes from auth store
 */
export function Profile() {
  const memberId = useMemberId() || '';
  const persona = usePersona();
  const { operatorId, operatorName } = useOperatorInfo();

  return (
    <div className={styles.page}>
      <header className={styles.pageHeader}>
        <Link to="/app" className={styles.backLink}>
          Back to Dashboard
        </Link>
        <h1>Profile</h1>
      </header>

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
