import { useState } from 'react';
import { useUserInfo } from '@mono-repo/shared-state';
import type { MfeProps } from '@mono-repo/shared-state';
import { ImmunizationSection } from './components/ImmunizationSection';
import { AllergySection } from './components/AllergySection';
import { MedicationSection } from './components/MedicationSection';
import styles from './app.module.css';

type Tab = 'immunizations' | 'allergies' | 'medications';

export type HealthSummaryAppProps = MfeProps;

/**
 * Health Summary MFE with tabbed navigation
 * Displays immunization, allergy, and medication records
 */
export function HealthSummaryApp({ memberEid, persona, operatorId, operatorName }: HealthSummaryAppProps) {
  // Fetch user info on first load - uses ApiClient from context (supports serviceBaseUrl for web components)
  useUserInfo();

  const [activeTab, setActiveTab] = useState<Tab>('immunizations');

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <h2 className={styles.title}>Health Summary</h2>
        {operatorName && (
          <span className={styles.operator}>
            Viewing as: {operatorName} ({persona})
          </span>
        )}
      </header>

      <nav className={styles.tabs}>
        <button
          className={`${styles.tab} ${activeTab === 'immunizations' ? styles.activeTab : ''}`}
          onClick={() => setActiveTab('immunizations')}
        >
          Immunizations
        </button>
        <button
          className={`${styles.tab} ${activeTab === 'allergies' ? styles.activeTab : ''}`}
          onClick={() => setActiveTab('allergies')}
        >
          Allergies
        </button>
        <button
          className={`${styles.tab} ${activeTab === 'medications' ? styles.activeTab : ''}`}
          onClick={() => setActiveTab('medications')}
        >
          Medications
        </button>
      </nav>

      <div className={styles.tabContent}>
        {activeTab === 'immunizations' && <ImmunizationSection memberEid={memberEid} />}
        {activeTab === 'allergies' && <AllergySection memberEid={memberEid} />}
        {activeTab === 'medications' && <MedicationSection memberEid={memberEid} />}
      </div>
    </div>
  );
}

// Keep backward compatibility with old name
export { HealthSummaryApp as SummaryApp };
export default HealthSummaryApp;
