import { Link } from '@tanstack/react-router';
import type { User, DependentMetadata } from '@mono-repo/shared-state';
import { useAuthStore } from '@mono-repo/shared-state';
import styles from './Dashboard.module.css';

interface ResponsiblePartyDashboardProps {
  user: User | undefined;
  dependents: DependentMetadata[];
  selectedMemberEid: string | undefined;
}

/**
 * Dashboard view for responsible party persona
 * Shows overview of all members under their care with quick access to health data
 */
export function ResponsiblePartyDashboard({
  user,
  dependents,
  selectedMemberEid
}: Readonly<ResponsiblePartyDashboardProps>) {
  const setSelectedChild = useAuthStore((state) => state.setSelectedChild);

  const handleMemberSelect = (memberEid: string | null) => {
    setSelectedChild(memberEid);
  };

  const isViewingSelf = !selectedMemberEid || selectedMemberEid === user?.sub;

  return (
    <div className={styles.dashboard}>
      <div className={styles.welcome}>
        <h1>Welcome{user?.name ? `, ${user.name}` : ''}</h1>
        {user?.email && <p className={styles.email}>{user.email}</p>}
        <p className={styles.memberLabel}>Responsible Party Dashboard</p>
      </div>

      {/* Currently Viewing */}
      <section className={styles.section}>
        <div className={styles.viewingBanner}>
          <span className={styles.viewingLabel}>Currently viewing:</span>
          <span className={styles.viewingName}>
            {isViewingSelf
              ? 'Your own records'
              : dependents.find(d => d.id === selectedMemberEid)?.name || 'Member'}
          </span>
        </div>
      </section>

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
            <p>View health records for selected member</p>
          </Link>

          <Link to="/app/profile" className={styles.card}>
            <div className={styles.cardIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                <circle cx="12" cy="7" r="4" />
              </svg>
            </div>
            <h3>Profile</h3>
            <p>Manage profile and documents for selected member</p>
          </Link>
        </div>
      </section>

      {/* Members */}
      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Members</h2>
        <div className={styles.familyGrid}>
          {/* Responsible Party's own card */}
          <div
            className={`${styles.familyCard} ${isViewingSelf ? styles.selectedCard : ''}`}
            onClick={() => handleMemberSelect(null)}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => e.key === 'Enter' && handleMemberSelect(null)}
          >
            <div className={styles.familyCardAvatar}>
              {user?.name?.[0] || 'M'}
            </div>
            <div className={styles.familyCardInfo}>
              <h4>{user?.name || 'Myself'}</h4>
              <span className={styles.familyCardLabel}>Responsible Party</span>
            </div>
            {isViewingSelf && (
              <span className={styles.selectedBadge}>Viewing</span>
            )}
          </div>

          {/* Dependent member cards */}
          {dependents.map((member) => (
            <div
              key={member.id}
              className={`${styles.familyCard} ${selectedMemberEid === member.id ? styles.selectedCard : ''}`}
              onClick={() => handleMemberSelect(member.id)}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => e.key === 'Enter' && handleMemberSelect(member.id)}
            >
              <div className={styles.familyCardAvatar}>
                {member.name[0]}
              </div>
              <div className={styles.familyCardInfo}>
                <h4>{member.name}</h4>
                <span className={styles.familyCardLabel}>Member</span>
                {member.dateOfBirth && (
                  <span className={styles.familyCardDob}>
                    DOB: {new Date(member.dateOfBirth).toLocaleDateString()}
                  </span>
                )}
              </div>
              {selectedMemberEid === member.id && (
                <span className={styles.selectedBadge}>Viewing</span>
              )}
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
