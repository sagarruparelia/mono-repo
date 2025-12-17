import { ReactNode } from 'react';
import styles from './sections.module.css';

export interface Column<T> {
  key: keyof T | string;
  header: string;
  render?: (item: T) => ReactNode;
  className?: string;
}

export interface DataSectionProps<T> {
  data: T[] | undefined;
  isLoading: boolean;
  error: Error | null;
  columns: Column<T>[];
  loadingMessage: string;
  emptyMessage: string;
  errorPrefix: string;
  getKey: (item: T) => string;
}

/**
 * Generic data section component for rendering tabular health data.
 * Eliminates duplication across ImmunizationSection, AllergySection, MedicationSection.
 */
export function DataSection<T>({
  data,
  isLoading,
  error,
  columns,
  loadingMessage,
  emptyMessage,
  errorPrefix,
  getKey,
}: DataSectionProps<T>) {
  if (isLoading) {
    return (
      <div className={styles.section}>
        <div className={styles.loading}>{loadingMessage}</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className={styles.section}>
        <div className={styles.error}>
          {errorPrefix}: {error.message}
        </div>
      </div>
    );
  }

  if (!data || data.length === 0) {
    return (
      <div className={styles.section}>
        <div className={styles.empty}>{emptyMessage}</div>
      </div>
    );
  }

  return (
    <div className={styles.section}>
      <div className={styles.tableContainer}>
        <table className={styles.table}>
          <thead>
            <tr>
              {columns.map((col) => (
                <th key={String(col.key)}>{col.header}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {data.map((item) => (
              <tr key={getKey(item)}>
                {columns.map((col) => (
                  <td key={String(col.key)} className={col.className}>
                    {col.render
                      ? col.render(item)
                      : String((item as Record<string, unknown>)[col.key as string] ?? '-')}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
