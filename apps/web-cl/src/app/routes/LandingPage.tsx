import { useIsAuthenticated } from '@mono-repo/shared-state';
import { Navigate } from 'react-router-dom';
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
      <div className={styles.hero}>
        <h1 className={styles.title}>Welcome</h1>
        <p className={styles.subtitle}>
          Sign in to access your dashboard and manage your account.
        </p>
        <button className={styles.loginButton} onClick={handleLogin}>
          Sign In with HSID
        </button>
      </div>
    </div>
  );
}

export default LandingPage;
