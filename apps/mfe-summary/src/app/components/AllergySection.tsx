import { useAllergies } from '@mono-repo/shared-state';
import styles from './sections.module.css';

interface AllergySectionProps {
  memberId: string;
}

/**
 * Displays allergy records for a member
 */
export function AllergySection({ memberId }: AllergySectionProps) {
  const { data: allergies, isLoading, error } = useAllergies(memberId);

  if (isLoading) {
    return (
      <div className={styles.section}>
        <div className={styles.loading}>Loading allergy records...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className={styles.section}>
        <div className={styles.error}>
          Failed to load allergies: {(error as Error).message}
        </div>
      </div>
    );
  }

  if (!allergies || allergies.length === 0) {
    return (
      <div className={styles.section}>
        <div className={styles.empty}>No allergy records found</div>
      </div>
    );
  }

  const getSeverityClass = (severity: string) => {
    switch (severity) {
      case 'severe':
        return styles.severitySevere;
      case 'moderate':
        return styles.severityModerate;
      case 'mild':
        return styles.severityMild;
      default:
        return '';
    }
  };

  return (
    <div className={styles.section}>
      <div className={styles.tableContainer}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Allergen</th>
              <th>Reaction</th>
              <th>Severity</th>
              <th>Onset Date</th>
            </tr>
          </thead>
          <tbody>
            {allergies.map((allergy) => (
              <tr key={allergy.id}>
                <td className={styles.primaryCell}>{allergy.allergen}</td>
                <td>{allergy.reaction}</td>
                <td>
                  <span className={`${styles.severityBadge} ${getSeverityClass(allergy.severity)}`}>
                    {allergy.severity}
                  </span>
                </td>
                <td>{allergy.onsetDate ? new Date(allergy.onsetDate).toLocaleDateString() : '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
