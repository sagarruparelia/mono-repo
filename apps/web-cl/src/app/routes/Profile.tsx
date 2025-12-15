import { useUser, usePersona } from '@mono-repo/shared-state';
import { Link } from 'react-router-dom';
import styles from './routes.module.css';

/**
 * Profile page - embeds mfe-profile
 */
export function Profile() {
  const user = useUser();
  const persona = usePersona();

  const memberId = user?.sub || '';

  return (
    <div className={styles.page}>
      <header className={styles.pageHeader}>
        <Link to="/app" className={styles.backLink}>
          Back to Dashboard
        </Link>
        <h1>Profile</h1>
      </header>

      <div className={styles.mfeContainer}>
        <mfe-profile member-id={memberId} persona={persona || 'individual'} />
      </div>
    </div>
  );
}

export default Profile;
