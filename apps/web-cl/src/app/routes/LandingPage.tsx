import { useIsAuthenticated } from '@mono-repo/shared-state';
import { Navigate } from '@tanstack/react-router';
import styles from './routes.module.css';

/**
 * Public landing page
 * Redirects authenticated users to dashboard
 */
export function LandingPage() {
  const isAuthenticated = useIsAuthenticated();

  if (isAuthenticated) {
    return <Navigate to="/app" replace />;
  }

  const handleLogin = () => {
    // Redirect to BFF login endpoint (HSID PKCE flow)
    window.location.href = '/api/auth/login';
  };

  return (
    <div className={styles.landing}>
      <div className={styles.landingContent}>
        <div className={styles.hero}>
          <div className={styles.logo}>
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
            </svg>
          </div>
          <h1 className={styles.title}>Health Portal</h1>
          <p className={styles.subtitle}>
            Access your health records, manage appointments, and stay on top of your wellness journey.
          </p>
          <button className={styles.loginButton} onClick={handleLogin}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" />
              <polyline points="10 17 15 12 10 7" />
              <line x1="15" y1="12" x2="3" y2="12" />
            </svg>
            Sign In
          </button>
          <p className={styles.secureNote}>
            Secure sign-in powered by HealthSafe ID
          </p>
        </div>

        <div className={styles.features}>
          <div className={styles.feature}>
            <div className={styles.featureIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
              </svg>
            </div>
            <h3>Health Summary</h3>
            <p>View immunizations, allergies, and medications in one place</p>
          </div>

          <div className={styles.feature}>
            <div className={styles.featureIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                <circle cx="12" cy="7" r="4" />
              </svg>
            </div>
            <h3>Profile Management</h3>
            <p>Keep your personal information and documents up to date</p>
          </div>

          <div className={styles.feature}>
            <div className={styles.featureIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                <circle cx="9" cy="7" r="4" />
                <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
                <path d="M16 3.13a4 4 0 0 1 0 7.75" />
              </svg>
            </div>
            <h3>Family Access</h3>
            <p>Responsible parties can manage health records for their dependents</p>
          </div>
        </div>
      </div>
    </div>
  );
}

export default LandingPage;
