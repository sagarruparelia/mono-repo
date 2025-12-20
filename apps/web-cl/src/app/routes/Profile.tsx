import { useEffectiveMemberEid, usePersona, useOperatorInfo } from '@mono-repo/shared-state';
import { ProfileApp } from '@mono-repo/mfe-profile';
import styles from './routes.module.css';

/**
 * Profile page - uses ProfileApp React component directly
 * Uses the effective memberEid which respects the global child selector
 */
export function Profile() {
  const memberEid = useEffectiveMemberEid();
  const persona = usePersona();
  const { operatorId, operatorName } = useOperatorInfo();

  // Guard: Don't render MFE without valid session
  if (!memberEid) {
    return (
      <div className={styles.page}>
        <div className={styles.error}>Session not available. Please log in.</div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <div className={styles.mfeContainer}>
        <ProfileApp
          memberEid={memberEid}
          persona={persona || 'individual'}
          operatorId={operatorId}
          operatorName={operatorName}
        />
      </div>
    </div>
  );
}

export default Profile;
