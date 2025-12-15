import { useState } from 'react';
import { useProfile, useUpdateProfile } from '@mono-repo/shared-state';
import type { MfeProps, ProfileUpdatePayload } from '@mono-repo/shared-state';
import styles from './app.module.css';

export type ProfileAppProps = MfeProps;

export function ProfileApp({ memberId, persona, operatorId, operatorName }: ProfileAppProps) {
  const { data: profile, isLoading, error } = useProfile(memberId);
  const updateProfile = useUpdateProfile(memberId);
  const [isEditing, setIsEditing] = useState(false);
  const [editForm, setEditForm] = useState<ProfileUpdatePayload>({});

  if (isLoading) {
    return (
      <div className={styles.container}>
        <div className={styles.loading}>Loading profile...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className={styles.container}>
        <div className={styles.error}>
          Failed to load profile: {(error as Error).message}
        </div>
      </div>
    );
  }

  if (!profile) {
    return (
      <div className={styles.container}>
        <div className={styles.empty}>No profile data available</div>
      </div>
    );
  }

  const handleEdit = () => {
    setEditForm({
      phone: profile.phone,
      address: profile.address,
    });
    setIsEditing(true);
  };

  const handleSave = async () => {
    await updateProfile.mutateAsync(editForm);
    setIsEditing(false);
  };

  const handleCancel = () => {
    setIsEditing(false);
    setEditForm({});
  };

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <h2 className={styles.title}>Member Profile</h2>
        {operatorName && (
          <span className={styles.operator}>
            Viewing as: {operatorName} ({persona})
          </span>
        )}
      </header>

      <div className={styles.profileCard}>
        <div className={styles.profileHeader}>
          <div className={styles.avatar}>
            {profile.firstName[0]}{profile.lastName[0]}
          </div>
          <div className={styles.nameSection}>
            <h3 className={styles.name}>
              {profile.firstName} {profile.lastName}
            </h3>
            <span className={styles.email}>{profile.email}</span>
          </div>
          {!isEditing && (
            <button className={styles.editButton} onClick={handleEdit}>
              Edit
            </button>
          )}
        </div>

        <div className={styles.fields}>
          <div className={styles.field}>
            <label className={styles.fieldLabel}>Phone</label>
            {isEditing ? (
              <input
                type="tel"
                className={styles.input}
                value={editForm.phone || ''}
                onChange={(e) => setEditForm({ ...editForm, phone: e.target.value })}
              />
            ) : (
              <span className={styles.fieldValue}>{profile.phone || '-'}</span>
            )}
          </div>

          {profile.address && (
            <div className={styles.field}>
              <label className={styles.fieldLabel}>Address</label>
              {isEditing ? (
                <div className={styles.addressFields}>
                  <input
                    type="text"
                    className={styles.input}
                    placeholder="Street"
                    value={editForm.address?.street || ''}
                    onChange={(e) =>
                      setEditForm({
                        ...editForm,
                        address: { ...editForm.address!, street: e.target.value },
                      })
                    }
                  />
                  <div className={styles.addressRow}>
                    <input
                      type="text"
                      className={styles.input}
                      placeholder="City"
                      value={editForm.address?.city || ''}
                      onChange={(e) =>
                        setEditForm({
                          ...editForm,
                          address: { ...editForm.address!, city: e.target.value },
                        })
                      }
                    />
                    <input
                      type="text"
                      className={styles.input}
                      placeholder="State"
                      value={editForm.address?.state || ''}
                      onChange={(e) =>
                        setEditForm({
                          ...editForm,
                          address: { ...editForm.address!, state: e.target.value },
                        })
                      }
                    />
                    <input
                      type="text"
                      className={styles.input}
                      placeholder="ZIP"
                      value={editForm.address?.zip || ''}
                      onChange={(e) =>
                        setEditForm({
                          ...editForm,
                          address: { ...editForm.address!, zip: e.target.value },
                        })
                      }
                    />
                  </div>
                </div>
              ) : (
                <span className={styles.fieldValue}>
                  {profile.address.street}, {profile.address.city}, {profile.address.state} {profile.address.zip}
                </span>
              )}
            </div>
          )}
        </div>

        {isEditing && (
          <div className={styles.actions}>
            <button
              className={styles.cancelButton}
              onClick={handleCancel}
              disabled={updateProfile.isPending}
            >
              Cancel
            </button>
            <button
              className={styles.saveButton}
              onClick={handleSave}
              disabled={updateProfile.isPending}
            >
              {updateProfile.isPending ? 'Saving...' : 'Save'}
            </button>
          </div>
        )}

        {updateProfile.isError && (
          <div className={styles.error}>
            Failed to save: {(updateProfile.error as Error).message}
          </div>
        )}
      </div>
    </div>
  );
}

export default ProfileApp;
