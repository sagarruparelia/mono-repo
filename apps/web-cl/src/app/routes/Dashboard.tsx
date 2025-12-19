import { useUser, usePersona, useEffectiveMemberId, useDependentsMetadata, useUserInfo } from '@mono-repo/shared-state';
import { MemberDashboard } from '../components/MemberDashboard';
import { ResponsiblePartyDashboard } from '../components/ResponsiblePartyDashboard';

/**
 * Main dashboard page after authentication
 * Renders different views based on persona:
 * - "individual" → Member Dashboard (viewing own records)
 * - "parent" → Responsible Party Dashboard (can view dependents' records)
 */
export function Dashboard() {
  const user = useUser();
  const persona = usePersona();
  const effectiveMemberId = useEffectiveMemberId();
  const { data: dependents } = useDependentsMetadata();

  // Fetch user info on first load - data is cached by React Query
  useUserInfo();

  // Responsible Party persona (parent in HSID terms)
  if (persona === 'parent') {
    return (
      <ResponsiblePartyDashboard
        user={user}
        dependents={dependents || []}
        selectedMemberId={effectiveMemberId}
      />
    );
  }

  // Member persona (individual in HSID terms)
  return <MemberDashboard user={user} memberId={effectiveMemberId} />;
}

export default Dashboard;
