import { useImmunizations } from '@mono-repo/shared-state';
import styles from './sections.module.css';

interface ImmunizationSectionProps {
  memberId: string;
}

/**
 * Displays immunization records for a member
 */
export function ImmunizationSection({ memberId }: ImmunizationSectionProps) {
  const { data: immunizations, isLoading, error } = useImmunizations(memberId);

  if (isLoading) {
    return (
      <div className={styles.section}>
        <div className={styles.loading}>Loading immunization records...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className={styles.section}>
        <div className={styles.error}>
          Failed to load immunizations: {(error as Error).message}
        </div>
      </div>
    );
  }

  if (!immunizations || immunizations.length === 0) {
    return (
      <div className={styles.section}>
        <div className={styles.empty}>No immunization records found</div>
      </div>
    );
  }

  return (
    <div className={styles.section}>
      <div className={styles.tableContainer}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Vaccine</th>
              <th>Date</th>
              <th>Provider</th>
              <th>Lot Number</th>
            </tr>
          </thead>
          <tbody>
            {immunizations.map((imm) => (
              <tr key={imm.id}>
                <td className={styles.primaryCell}>{imm.name}</td>
                <td>{new Date(imm.date).toLocaleDateString()}</td>
                <td>{imm.provider || '-'}</td>
                <td className={styles.monoCell}>{imm.lotNumber || '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
