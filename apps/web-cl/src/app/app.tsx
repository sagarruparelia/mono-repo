import { Suspense, lazy } from 'react';
import { Route, Routes } from 'react-router-dom';
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

export function App() {
  return (
    <div className={styles.app}>
      <Suspense fallback={<LoadingFallback />}>
        <Routes>
          {/* Public routes */}
          <Route path="/" element={<LandingPage />} />

          {/* Protected routes with AppLayout */}
          <Route
            path="/app"
            element={
              <AuthGuard>
                <AppLayout>
                  <Dashboard />
                </AppLayout>
              </AuthGuard>
            }
          />
          <Route
            path="/app/dashboard"
            element={
              <AuthGuard>
                <AppLayout>
                  <Dashboard />
                </AppLayout>
              </AuthGuard>
            }
          />
          <Route
            path="/app/health-summary"
            element={
              <AuthGuard>
                <AppLayout>
                  <HealthSummary />
                </AppLayout>
              </AuthGuard>
            }
          />
          {/* Legacy summary route - redirects to health-summary */}
          <Route
            path="/app/summary"
            element={
              <AuthGuard>
                <Summary />
              </AuthGuard>
            }
          />
          <Route
            path="/app/profile"
            element={
              <AuthGuard>
                <AppLayout>
                  <Profile />
                </AppLayout>
              </AuthGuard>
            }
          />
        </Routes>
      </Suspense>
    </div>
  );
}

export default App;
