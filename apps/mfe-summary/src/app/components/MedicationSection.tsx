import { useMedications } from '@mono-repo/shared-state';
import styles from './sections.module.css';

interface MedicationSectionProps {
  memberId: string;
}

/**
 * Displays medication records for a member
 */
export function MedicationSection({ memberId }: MedicationSectionProps) {
  const { data: medications, isLoading, error } = useMedications(memberId);

  if (isLoading) {
    return (
      <div className={styles.section}>
        <div className={styles.loading}>Loading medication records...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className={styles.section}>
        <div className={styles.error}>
          Failed to load medications: {(error as Error).message}
        </div>
      </div>
    );
  }

  if (!medications || medications.length === 0) {
    return (
      <div className={styles.section}>
        <div className={styles.empty}>No medication records found</div>
      </div>
    );
  }

  return (
    <div className={styles.section}>
      <div className={styles.tableContainer}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Medication</th>
              <th>Dosage</th>
              <th>Frequency</th>
              <th>Prescribed</th>
              <th>Prescriber</th>
            </tr>
          </thead>
          <tbody>
            {medications.map((med) => (
              <tr key={med.id}>
                <td className={styles.primaryCell}>{med.name}</td>
                <td className={styles.monoCell}>{med.dosage}</td>
                <td>{med.frequency}</td>
                <td>{new Date(med.prescribedDate).toLocaleDateString()}</td>
                <td>{med.prescriber || '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
