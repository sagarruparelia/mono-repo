import { ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { usePersona, useUser, ChildSelector, useLogout } from '@mono-repo/shared-state';
import styles from './AppLayout.module.css';

interface AppLayoutProps {
  children: ReactNode;
}

/**
 * Main application layout with header navigation and global child selector
 */
export function AppLayout({ children }: AppLayoutProps) {
  const location = useLocation();
  const persona = usePersona();
  const user = useUser();
  const logout = useLogout();

  const handleLogout = () => {
    logout.mutate();
    window.location.href = '/';
  };

  const isActive = (path: string) => {
    if (path === '/app/dashboard' || path === '/app') {
      return location.pathname === '/app' || location.pathname === '/app/dashboard';
    }
    return location.pathname.startsWith(path);
  };

  return (
    <div className={styles.layout}>
      <header className={styles.header}>
        <div className={styles.topBar}>
          <div className={styles.logo}>Health Portal</div>
          <div className={styles.userInfo}>
            <span className={styles.userName}>{user?.name || 'User'}</span>
            <span className={styles.persona}>{persona}</span>
            <button className={styles.logoutButton} onClick={handleLogout}>
              Logout
            </button>
          </div>
        </div>

        <nav className={styles.nav}>
          <div className={styles.navLinks}>
            <Link
              to="/app/dashboard"
              className={`${styles.navLink} ${isActive('/app/dashboard') ? styles.active : ''}`}
            >
              Dashboard
            </Link>
            <Link
              to="/app/health-summary"
              className={`${styles.navLink} ${isActive('/app/health-summary') ? styles.active : ''}`}
            >
              Health Summary
            </Link>
            <Link
              to="/app/profile"
              className={`${styles.navLink} ${isActive('/app/profile') ? styles.active : ''}`}
            >
              Profile
            </Link>
          </div>

          <div className={styles.childSelectorContainer}>
            <ChildSelector />
          </div>
        </nav>
      </header>

      <main className={styles.main}>{children}</main>
    </div>
  );
}
