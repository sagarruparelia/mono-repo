import { Suspense, lazy } from 'react';
import { Route, Routes } from 'react-router-dom';
import { AuthGuard } from './guards/AuthGuard';
import styles from './app.module.css';

// Lazy load route components for code splitting
const LandingPage = lazy(() => import('./routes/LandingPage'));
const Dashboard = lazy(() => import('./routes/Dashboard'));
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

          {/* Protected routes */}
          <Route
            path="/app"
            element={
              <AuthGuard>
                <Dashboard />
              </AuthGuard>
            }
          />
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
                <Profile />
              </AuthGuard>
            }
          />
        </Routes>
      </Suspense>
    </div>
  );
}

export default App;
