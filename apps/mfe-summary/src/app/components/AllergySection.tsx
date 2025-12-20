import { useAllergies } from '@mono-repo/shared-state';
import { DataSection, Column } from './DataSection';
import styles from './sections.module.css';

interface Allergy {
  id: string;
  allergen: string;
  reaction: string;
  severity: string;
  onsetDate?: string;
}

interface AllergySectionProps {
  memberEid: string;
}

const getSeverityClass = (severity: string) => {
  switch (severity) {
    case 'severe': return styles.severitySevere;
    case 'moderate': return styles.severityModerate;
    case 'mild': return styles.severityMild;
    default: return '';
  }
};

const columns: Column<Allergy>[] = [
  { key: 'allergen', header: 'Allergen', className: styles.primaryCell },
  { key: 'reaction', header: 'Reaction' },
  {
    key: 'severity',
    header: 'Severity',
    render: (a) => (
      <span className={`${styles.severityBadge} ${getSeverityClass(a.severity)}`}>
        {a.severity}
      </span>
    ),
  },
  { key: 'onsetDate', header: 'Onset Date', render: (a) => a.onsetDate ? new Date(a.onsetDate).toLocaleDateString() : '-' },
];

export function AllergySection({ memberEid }: AllergySectionProps) {
  const { data, isLoading, error } = useAllergies(memberEid);

  return (
    <DataSection<Allergy>
      data={data}
      isLoading={isLoading}
      error={error as Error | null}
      columns={columns}
      loadingMessage="Loading allergy records..."
      emptyMessage="No allergy records found"
      errorPrefix="Failed to load allergies"
      getKey={(a) => a.id}
    />
  );
}
