import { useSummary } from '@mono-repo/shared-state';
import type { MfeProps } from '@mono-repo/shared-state';
import styles from './app.module.css';

export type SummaryAppProps = MfeProps;

export function SummaryApp({ memberId, persona, operatorId, operatorName }: SummaryAppProps) {
  const { data: summary, isLoading, error } = useSummary(memberId);

  if (isLoading) {
    return (
      <div className={styles.container}>
        <div className={styles.loading}>Loading summary...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className={styles.container}>
        <div className={styles.error}>
          Failed to load summary: {(error as Error).message}
        </div>
      </div>
    );
  }

  if (!summary) {
    return (
      <div className={styles.container}>
        <div className={styles.empty}>No summary data available</div>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <h2 className={styles.title}>Member Summary</h2>
        {operatorName && (
          <span className={styles.operator}>
            Viewing as: {operatorName} ({persona})
          </span>
        )}
      </header>

      <div className={styles.memberInfo}>
        <h3>{summary.name}</h3>
        <span className={styles.status}>{summary.status}</span>
        <span className={styles.lastUpdated}>
          Last updated: {new Date(summary.lastUpdated).toLocaleDateString()}
        </span>
      </div>

      <div className={styles.metrics}>
        {summary.metrics.map((metric) => (
          <div key={metric.key} className={styles.metricCard}>
            <span className={styles.metricLabel}>{metric.label}</span>
            <span className={styles.metricValue}>{metric.value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

export default SummaryApp;
