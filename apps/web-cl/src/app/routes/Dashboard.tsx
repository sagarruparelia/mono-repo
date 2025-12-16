import { useUser, usePersona, useEffectiveMemberId, useDependentsMetadata, useUserInfo } from '@mono-repo/shared-state';
import { YouthDashboard } from '../components/YouthDashboard';
import { ParentDashboard } from '../components/ParentDashboard';

/**
 * Main dashboard page after authentication
 * Renders different views based on persona (youth vs parent)
 */
export function Dashboard() {
  const user = useUser();
  const persona = usePersona();
  const effectiveMemberId = useEffectiveMemberId();
  const { data: dependents } = useDependentsMetadata();

  // Fetch user info on first load - data is cached by React Query
  useUserInfo();

  if (persona === 'parent') {
    return (
      <ParentDashboard
        user={user}
        dependents={dependents || []}
        selectedMemberId={effectiveMemberId}
      />
    );
  }

  return <YouthDashboard user={user} memberId={effectiveMemberId} />;
}

export default Dashboard;
