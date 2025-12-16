import { usePersona, useUser, useSelectedChildId, useAuthStore } from '../stores/auth.store';
import { useDependentsMetadata } from '../queries/user.queries';

/**
 * Global child selector component for parent persona
 * Displays a dropdown to switch between viewing self and dependents
 * Selection persists across pages via the auth store
 */
export function ChildSelector() {
  const persona = usePersona();
  const user = useUser();
  const selectedChildId = useSelectedChildId();
  const setSelectedChild = useAuthStore((state) => state.setSelectedChild);
  const { data: dependents, isLoading } = useDependentsMetadata();

  // Only render for parent persona with dependents
  if (persona !== 'parent') {
    return null;
  }

  if (isLoading) {
    return (
      <div className="child-selector child-selector--loading">
        <span>Loading...</span>
      </div>
    );
  }

  if (!dependents || dependents.length === 0) {
    return null;
  }

  const handleChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const value = e.target.value;
    // If selecting "Myself" (user's own ID), set to null
    setSelectedChild(value === user?.sub ? null : value);
  };

  return (
    <div className="child-selector">
      <label htmlFor="global-child-select" className="child-selector__label">
        Viewing:
      </label>
      <select
        id="global-child-select"
        className="child-selector__select"
        value={selectedChildId || user?.sub || ''}
        onChange={handleChange}
      >
        <option value={user?.sub || ''}>Myself</option>
        {dependents.map((dep) => (
          <option key={dep.id} value={dep.id}>
            {dep.name}
          </option>
        ))}
      </select>
    </div>
  );
}
