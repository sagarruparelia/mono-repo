import { createFileRoute } from '@tanstack/react-router';
import { lazy, Suspense } from 'react';
import styles from '../../app/app.module.css';

const Dashboard = lazy(() => import('../../app/routes/Dashboard'));

export const Route = createFileRoute('/app/')({
  component: DashboardRoute,
});

function DashboardRoute() {
  return (
    <Suspense fallback={<div className={styles.loading}>Loading...</div>}>
      <Dashboard />
    </Suspense>
  );
}
