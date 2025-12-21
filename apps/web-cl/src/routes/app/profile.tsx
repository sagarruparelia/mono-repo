import { createFileRoute } from '@tanstack/react-router';
import { lazy, Suspense } from 'react';
import styles from '../../app/app.module.css';

const Profile = lazy(() => import('../../app/routes/Profile'));

export const Route = createFileRoute('/app/profile')({
  component: ProfileRoute,
});

function ProfileRoute() {
  return (
    <Suspense fallback={<div className={styles.loading}>Loading...</div>}>
      <Profile />
    </Suspense>
  );
}
