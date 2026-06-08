import { Edit } from '@carbon/icons-react';
import { Button, SkeletonText, TextArea } from '@carbon/react';
import { useCallback, useEffect, useState, type FC } from 'react';

import type { RiparianFinalComments } from '@/types/protocolChecklist';

import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';

/**
 * Riparian Final Comments section (FREP235) — read-only form with inline editing, mirroring the
 * legacy six free-text prompts. Drives off the typed DTO (complete scalar read) and round-trips it
 * (with revision count) on save.
 */

type Props = {
  checklistId: string;
  canEdit: boolean;
  submitted: boolean;
};

const COMMENT_FIELDS: { key: keyof RiparianFinalComments; label: string }[] = [
  {
    key: 'conclusionComment',
    label:
      'Does the conclusion on the functioning condition generally agree with your assessment of the riparian indicators?',
  },
  {
    key: 'specificImpactComment',
    label: 'Describe more specifically what the reasons were for the "No" answers.',
  },
  {
    key: 'assessmentProblemsComment',
    label:
      'All "No" answers are weighed equally. Were any specific problems identified that were of greater concern?',
  },
  {
    key: 'mapLegibilityComment',
    label: 'Have you marked the stream reach assessed on a map in a way that will be legible?',
  },
  {
    key: 'leaveStripAssessmentComment',
    label: 'Does the leave strip appear as indicated in plans or on plan maps?',
  },
  {
    key: 'checklistRecommComment',
    label:
      'Do you have any recommendations for improving the Riparian Effectiveness Routine Evaluation Checklist or Protocol?',
  },
];

const RipFinalCommentsView: FC<Props> = ({ checklistId, canEdit, submitted }) => {
  const { display } = useNotification();
  const [data, setData] = useState<RiparianFinalComments | null>(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);

  const reportError = useCallback(
    (title: string, err: unknown) =>
      display({
        kind: 'error',
        title,
        subtitle: err instanceof Error ? err.message : 'Unknown error',
        timeout: 9000,
      }),
    [display],
  );

  const loadData = useCallback(
    (signal?: { cancelled: boolean }) => {
      setLoading(true);
      API.protocolChecklist
        .getRipFinalComments(checklistId)
        .then((d) => {
          if (!signal?.cancelled) setData(d);
        })
        .catch((err: unknown) => {
          if (!signal?.cancelled) reportError("We couldn't load the final comments", err);
        })
        .finally(() => {
          if (!signal?.cancelled) setLoading(false);
        });
    },
    [checklistId, reportError],
  );

  useEffect(() => {
    const signal = { cancelled: false };
    loadData(signal);
    return () => {
      signal.cancelled = true;
    };
  }, [loadData]);

  const get = (key: keyof RiparianFinalComments): string =>
    ((data as Record<string, unknown>)?.[key] as string | undefined) ?? '';
  const set = (key: keyof RiparianFinalComments, value: string) =>
    setData((prev) => (prev ? { ...prev, [key]: value } : prev));

  const handleSave = async () => {
    if (!data) return;
    setBusy(true);
    try {
      const saved = await API.protocolChecklist.saveRipFinalComments(checklistId, data);
      setData(saved);
      setEditing(false);
      display({ kind: 'success', title: 'Final comments saved', timeout: 4000 });
    } catch (err) {
      reportError('Save failed', err);
    } finally {
      setBusy(false);
    }
  };

  const cancel = () => {
    loadData();
    setEditing(false);
  };

  if (loading) {
    return <SkeletonText paragraph lineCount={8} />;
  }
  if (!data) {
    return <p>No final comments found.</p>;
  }

  const showEditControls = canEdit && !submitted;

  return (
    <div className="rip-form">
      <div className="protocol-checklist__section-actions">
        {!editing && showEditControls && (
          <Button kind="tertiary" size="lg" onClick={() => setEditing(true)}>
            <span className="protocol-checklist__edit-label">
              <Edit /> Edit
            </span>
          </Button>
        )}
        {editing && (
          <>
            <Button kind="ghost" size="lg" disabled={busy} onClick={cancel}>
              Cancel
            </Button>
            <Button size="lg" disabled={busy} onClick={() => void handleSave()}>
              Save
            </Button>
          </>
        )}
      </div>

      <div className="rip-form__comments">
        {COMMENT_FIELDS.map((field) =>
          editing ? (
            <TextArea
              key={field.key}
              id={`rip-${field.key}`}
              labelText={field.label}
              rows={4}
              value={get(field.key)}
              onChange={(e) => set(field.key, e.target.value)}
            />
          ) : (
            <div className="protocol-checklist__field" key={field.key}>
              <span className="protocol-checklist__label">{field.label}</span>
              <span className="protocol-checklist__value protocol-checklist__multiline">
                {get(field.key) || '—'}
              </span>
            </div>
          ),
        )}
      </div>
    </div>
  );
};

export default RipFinalCommentsView;
