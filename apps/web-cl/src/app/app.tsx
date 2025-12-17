import { Suspense, lazy } from 'react';
import { Route, Routes, Outlet } from 'react-router-dom';
import { AuthGuard } from './guards/AuthGuard';
import { AppLayout } from './components/AppLayout';
import styles from './app.module.css';

// Lazy load route components for code splitting
const LandingPage = lazy(() => import('./routes/LandingPage'));
const Dashboard = lazy(() => import('./routes/Dashboard'));
const HealthSummary = lazy(() => import('./routes/HealthSummary'));
const Summary = lazy(() => import('./routes/Summary'));
const Profile = lazy(() => import('./routes/Profile'));

function LoadingFallback() {
  return <div className={styles.loading}>Loading...</div>;
}

/** Protected layout wrapper - applies AuthGuard + AppLayout to child routes */
function ProtectedLayout() {
  return (
    <AuthGuard>
      <AppLayout>
        <Outlet />
      </AppLayout>
    </AuthGuard>
  );
}

export function App() {
  return (
    <div className={styles.app}>
      <Suspense fallback={<LoadingFallback />}>
        <Routes>
          {/* Public routes */}
          <Route path="/" element={<LandingPage />} />

          {/* Protected routes - all wrapped with AuthGuard + AppLayout */}
          <Route path="/app" element={<ProtectedLayout />}>
            <Route index element={<Dashboard />} />
            <Route path="dashboard" element={<Dashboard />} />
            <Route path="health-summary" element={<HealthSummary />} />
            <Route path="profile" element={<Profile />} />
          </Route>

          {/* Legacy route without AppLayout */}
          <Route path="/app/summary" element={<AuthGuard><Summary /></AuthGuard>} />
        </Routes>
      </Suspense>
    </div>
  );
}

export default App;
