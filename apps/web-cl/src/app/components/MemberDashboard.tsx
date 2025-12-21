import { Link } from '@tanstack/react-router';
import type { User } from '@mono-repo/shared-state';
import styles from './Dashboard.module.css';

interface MemberDashboardProps {
  user: User | undefined;
}

/**
 * Dashboard view for member (individual) persona
 * Shows personal health summary cards with links to health records and profile
 */
export function MemberDashboard({ user }: Readonly<MemberDashboardProps>) {
  return (
    <div className={styles.dashboard}>
      <div className={styles.welcome}>
        <h1>Welcome{user?.name ? `, ${user.name}` : ''}</h1>
        {user?.email && <p className={styles.email}>{user.email}</p>}
        <p className={styles.memberLabel}>Member Dashboard</p>
      </div>

      <div className={styles.cards}>
        <Link to="/app/health-summary" className={styles.card}>
          <div className={styles.cardIcon}>
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
            </svg>
          </div>
          <h3>Health Summary</h3>
          <p>View your immunizations, allergies, and medications</p>
        </Link>

        <Link to="/app/profile" className={styles.card}>
          <div className={styles.cardIcon}>
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
              <circle cx="12" cy="7" r="4" />
            </svg>
          </div>
          <h3>Profile</h3>
          <p>Manage your personal information and documents</p>
        </Link>
      </div>
    </div>
  );
}
