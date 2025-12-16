import { Link } from 'react-router-dom';
import type { User, DependentMetadata } from '@mono-repo/shared-state';
import { useAuthStore } from '@mono-repo/shared-state';
import styles from './Dashboard.module.css';

interface ParentDashboardProps {
  user: User | undefined;
  dependents: DependentMetadata[];
  selectedMemberId: string | undefined;
}

/**
 * Dashboard view for parent persona
 * Shows overview of all children with quick access to their health data
 */
export function ParentDashboard({ user, dependents, selectedMemberId }: ParentDashboardProps) {
  const setSelectedChild = useAuthStore((state) => state.setSelectedChild);

  const handleChildSelect = (childId: string | null) => {
    setSelectedChild(childId);
  };

  return (
    <div className={styles.dashboard}>
      <div className={styles.welcome}>
        <h1>Welcome{user?.name ? `, ${user.name}` : ''}</h1>
        {user?.email && <p className={styles.email}>{user.email}</p>}
      </div>

      {/* Quick Actions */}
      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Quick Actions</h2>
        <div className={styles.cards}>
          <Link to="/app/health-summary" className={styles.card}>
            <div className={styles.cardIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
              </svg>
            </div>
            <h3>Health Summary</h3>
            <p>View health records for selected family member</p>
          </Link>

          <Link to="/app/profile" className={styles.card}>
            <div className={styles.cardIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                <circle cx="12" cy="7" r="4" />
              </svg>
            </div>
            <h3>Profile</h3>
            <p>Manage profile and documents for selected family member</p>
          </Link>
        </div>
      </section>

      {/* Family Members */}
      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Family Members</h2>
        <div className={styles.familyGrid}>
          {/* Parent's own card */}
          <div
            className={`${styles.familyCard} ${!selectedMemberId || selectedMemberId === user?.sub ? styles.selectedCard : ''}`}
            onClick={() => handleChildSelect(null)}
          >
            <div className={styles.familyCardAvatar}>
              {user?.name?.[0] || 'M'}
            </div>
            <div className={styles.familyCardInfo}>
              <h4>{user?.name || 'Myself'}</h4>
              <span className={styles.familyCardLabel}>Parent</span>
            </div>
            {(!selectedMemberId || selectedMemberId === user?.sub) && (
              <span className={styles.selectedBadge}>Selected</span>
            )}
          </div>

          {/* Children cards */}
          {dependents.map((child) => (
            <div
              key={child.id}
              className={`${styles.familyCard} ${selectedMemberId === child.id ? styles.selectedCard : ''}`}
              onClick={() => handleChildSelect(child.id)}
            >
              <div className={styles.familyCardAvatar}>
                {child.name[0]}
              </div>
              <div className={styles.familyCardInfo}>
                <h4>{child.name}</h4>
                {child.dateOfBirth && (
                  <span className={styles.familyCardLabel}>
                    DOB: {new Date(child.dateOfBirth).toLocaleDateString()}
                  </span>
                )}
              </div>
              {selectedMemberId === child.id && (
                <span className={styles.selectedBadge}>Selected</span>
              )}
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
