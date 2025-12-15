import { Link } from 'react-router-dom';
import { useUser, usePersona, useDependents } from '@mono-repo/shared-state';
import styles from './routes.module.css';

/**
 * Main dashboard page after authentication
 */
export function Dashboard() {
  const user = useUser();
  const persona = usePersona();
  const dependents = useDependents();

  return (
    <div className={styles.dashboard}>
      <header className={styles.header}>
        <h1>Dashboard</h1>
        <span className={styles.persona}>{persona}</span>
      </header>

      <div className={styles.welcome}>
        <h2>Welcome{user?.name ? `, ${user.name}` : ''}</h2>
        {user?.email && <p className={styles.email}>{user.email}</p>}
      </div>

      <nav className={styles.cards}>
        <Link to="/app/summary" className={styles.card}>
          <h3>Summary</h3>
          <p>View your account summary and key metrics</p>
        </Link>

        <Link to="/app/profile" className={styles.card}>
          <h3>Profile</h3>
          <p>Manage your profile and settings</p>
        </Link>

        {persona === 'parent' && dependents && dependents.length > 0 && (
          <div className={styles.card}>
            <h3>Dependents</h3>
            <p>
              You have access to {dependents.length} dependent
              {dependents.length > 1 ? 's' : ''}
            </p>
            <ul className={styles.dependentList}>
              {dependents.map((dep) => (
                <li key={dep}>{dep}</li>
              ))}
            </ul>
          </div>
        )}
      </nav>
    </div>
  );
}

export default Dashboard;
