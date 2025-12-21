import { createFileRoute } from '@tanstack/react-router';
import { lazy, Suspense } from 'react';
import styles from '../../app/app.module.css';

const HealthSummary = lazy(() => import('../../app/routes/HealthSummary'));

export const Route = createFileRoute('/app/health-summary')({
  component: HealthSummaryRoute,
});

function HealthSummaryRoute() {
  return (
    <Suspense fallback={<div className={styles.loading}>Loading...</div>}>
      <HealthSummary />
    </Suspense>
  );
}
