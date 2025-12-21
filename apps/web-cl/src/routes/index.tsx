import { createFileRoute } from '@tanstack/react-router';
import { lazy, Suspense } from 'react';
import styles from '../app/app.module.css';

const LandingPage = lazy(() => import('../app/routes/LandingPage'));

export const Route = createFileRoute('/')({
  component: LandingPageRoute,
});

function LandingPageRoute() {
  return (
    <Suspense fallback={<div className={styles.loading}>Loading...</div>}>
      <LandingPage />
    </Suspense>
  );
}
