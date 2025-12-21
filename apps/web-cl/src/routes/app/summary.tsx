import { createFileRoute } from '@tanstack/react-router';
import { lazy, Suspense } from 'react';
import styles from '../../app/app.module.css';

const Summary = lazy(() => import('../../app/routes/Summary'));

// Legacy route - keep for backward compatibility
export const Route = createFileRoute('/app/summary')({
  component: SummaryRoute,
});

function SummaryRoute() {
  return (
    <Suspense fallback={<div className={styles.loading}>Loading...</div>}>
      <Summary />
    </Suspense>
  );
}
