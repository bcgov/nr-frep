import { Add, TrashCan } from '@carbon/icons-react';
import {
  Button,
  Column,
  Grid,
  InlineNotification,
  SkeletonText,
  Stack,
  TextInput,
  Tile,
} from '@carbon/react';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import API from '@/services/APIs';

import './protocolChecklist.scss';

export type ColumnDef = { key: string; label: string };
export type ListDef = {
  key: string;
  legend: string;
  columns: ColumnDef[];
  newRow?: () => Record<string, unknown>;
};

type Bag = Record<string, unknown>;

type Props<T extends Bag> = {
  title: string;
  load: (checklistId: string) => Promise<T>;
  save: (checklistId: string, data: T) => Promise<T>;
  scalarFields?: ColumnDef[];
  lists: ListDef[];
};

/**
 * Generic riparian grid editor: renders optional top-level scalar fields plus one or more
 * editable row grids (add/remove/edit), round-tripping every field so save never nulls data.
 * Read-only when the checklist is submitted or the user cannot edit.
 */
function RipGridEditor<T extends Bag>({ title, load, save, scalarFields = [], lists }: Props<T>) {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { display } = useNotification();
  const { canEdit } = useAuthorization();

  const [current, setCurrent] = useState<T | null>(null);
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  const reportError = useCallback(
    (heading: string, err: unknown) =>
      display({
        kind: 'error',
        title: heading,
        subtitle: err instanceof Error ? err.message : 'Unknown error',
        timeout: 9000,
      }),
    [display],
  );

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([
      load(id),
      API.protocolChecklist.getChecklist('rip', id).then(
        (c) => c.statusCode,
        () => undefined,
      ),
    ])
      .then(([data, statusCode]) => {
        if (cancelled) return;
        setCurrent(data);
        setStatus(statusCode);
      })
      .catch((err: unknown) => {
        if (!cancelled) reportError("We couldn't load this section", err);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [id, load, reportError]);

  const readOnly = !canEdit || status === 'SUB';

  const setScalar = (key: string, value: string) =>
    setCurrent((prev) => (prev ? ({ ...prev, [key]: value } as T) : prev));

  const rowsOf = (listKey: string): Bag[] => (current?.[listKey] as Bag[] | undefined) ?? [];

  const setCell = (listKey: string, index: number, key: string, value: string) =>
    setCurrent((prev) =>
      prev
        ? ({
            ...prev,
            [listKey]: rowsOf(listKey).map((r, i) => (i === index ? { ...r, [key]: value } : r)),
          } as T)
        : prev,
    );
  const addRow = (listKey: string, newRow?: () => Bag) =>
    setCurrent((prev) =>
      prev ? ({ ...prev, [listKey]: [...rowsOf(listKey), newRow ? newRow() : {}] } as T) : prev,
    );
  const removeRow = (listKey: string, index: number) =>
    setCurrent((prev) =>
      prev ? ({ ...prev, [listKey]: rowsOf(listKey).filter((_, i) => i !== index) } as T) : prev,
    );

  const handleSave = async () => {
    if (!current) return;
    setBusy(true);
    try {
      const saved = await save(id, current);
      setCurrent(saved);
      display({ kind: 'success', title: 'Saved', timeout: 4000 });
    } catch (err) {
      reportError('Save failed', err);
    } finally {
      setBusy(false);
    }
  };

  if (loading) {
    return (
      <Grid fullWidth className="default-grid">
        <Column sm={4} md={8} lg={16}>
          <SkeletonText paragraph lineCount={8} />
        </Column>
      </Grid>
    );
  }

  return (
    <Grid fullWidth className="default-grid">
      <Column sm={4} md={8} lg={16}>
        <h1>
          {title} — checklist {id}
        </h1>
      </Column>
      {readOnly && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="info"
            title={status === 'SUB' ? 'Submitted — read only' : 'View only'}
            hideCloseButton
            lowContrast
          />
        </Column>
      )}
      <Column sm={4} md={8} lg={12}>
        {!current ? (
          <p>Nothing to edit.</p>
        ) : (
          <Tile>
            <Stack gap={6}>
              {scalarFields.length > 0 && (
                <fieldset className="chr-checklist__fieldset">
                  <legend>Details</legend>
                  {scalarFields.map((f) => (
                    <TextInput
                      key={f.key}
                      id={`rip-${f.key}`}
                      labelText={f.label}
                      value={(current[f.key] as string | undefined) ?? ''}
                      disabled={readOnly}
                      onChange={(e) => setScalar(f.key, e.target.value)}
                    />
                  ))}
                </fieldset>
              )}

              {lists.map((list) => (
                <fieldset key={list.key} className="chr-checklist__fieldset">
                  <legend>{list.legend}</legend>
                  {rowsOf(list.key).map((row, index) => (
                    <div key={`${list.key}-${index}`} className="chr-checklist__form">
                      {list.columns.map((c) => (
                        <TextInput
                          key={c.key}
                          id={`${list.key}-${index}-${c.key}`}
                          labelText={c.label}
                          value={(row[c.key] as string | undefined) ?? ''}
                          disabled={readOnly}
                          onChange={(e) => setCell(list.key, index, c.key, e.target.value)}
                        />
                      ))}
                      {!readOnly && (
                        <Button
                          kind="danger--tertiary"
                          size="sm"
                          renderIcon={TrashCan}
                          onClick={() => removeRow(list.key, index)}
                        >
                          Remove
                        </Button>
                      )}
                    </div>
                  ))}
                  {!readOnly && (
                    <Button
                      kind="ghost"
                      size="sm"
                      renderIcon={Add}
                      onClick={() => addRow(list.key, list.newRow)}
                    >
                      Add row
                    </Button>
                  )}
                </fieldset>
              ))}

              <div className="protocol-checklist__actions">
                {!readOnly && (
                  <Button onClick={() => void handleSave()} disabled={busy}>
                    Save
                  </Button>
                )}
                <Button kind="ghost" onClick={() => navigate(-1)}>
                  Back
                </Button>
              </div>
            </Stack>
          </Tile>
        )}
      </Column>
    </Grid>
  );
}

export default RipGridEditor;
