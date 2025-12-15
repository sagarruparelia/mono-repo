import { Link } from 'react-router-dom';
import { useUser, usePersona } from '@mono-repo/shared-state';
import { ProfileApp } from '@mono-repo/mfe-profile';
import styles from './routes.module.css';

/**
 * Profile page - uses ProfileApp React component directly
 * (No web component overhead for internal usage)
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
        <ProfileApp
          memberId={memberId}
          persona={persona || 'individual'}
        />
      </div>
    </div>
  );
}

export default Profile;
