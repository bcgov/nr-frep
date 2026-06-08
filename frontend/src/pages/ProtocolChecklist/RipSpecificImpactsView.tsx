import { Edit } from '@carbon/icons-react';
import { Button, TextInput } from '@carbon/react';
import {
  Fragment,
  useCallback,
  useEffect,
  useState,
  type Dispatch,
  type FC,
  type SetStateAction,
} from 'react';

import type {
  ProtocolChecklistField,
  ProtocolChecklistSection,
  RipOpenSpecImpactRow,
  RipOtherSpecImpactRow,
} from '@/types/protocolChecklist';

import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';

/**
 * Riparian Specific Impacts section (FREP234) — read-only grid with inline editing, mirroring the
 * legacy "Checklist of Specific Impacts for All No Answers Combined" form:
 *   - predefined impacts grouped under category headers, each marked Within / Above stream reach;
 *   - free-text "Other impacts" rows, also marked Within / Above.
 *
 * `spec_impact_ind` encodes the two checkboxes: W = within only, A = above only, B = both, blank =
 * neither. Drives off the section display fields (the proc returns the full reference list + text).
 */

type Props = {
  section: ProtocolChecklistSection;
  checklistId: string;
  canEdit: boolean;
  submitted: boolean;
};

type Row = Record<string, string>;

// Category sub-headers for the predefined impacts, inserted before these row indices (legacy
// frep234RIPSpecImpact.jsp uses the same fixed positions).
const OPEN_IMPACT_HEADERS: Record<number, string> = {
  0: 'Logging Related Impacts',
  9: 'Roads, Crossings',
  26: 'Animal Disturbance',
  33: 'Natural Impacts',
};

function parseRows(fields: ProtocolChecklistField[], prefix: string): Row[] {
  const pattern = new RegExp(`^${prefix} (\\d+) - (.+)$`);
  const byIndex = new Map<number, Row>();
  for (const field of fields) {
    const match = field.label.match(pattern);
    if (!match) continue;
    const index = Number(match[1]);
    if (!byIndex.has(index)) byIndex.set(index, {});
    byIndex.get(index)![match[2]] = field.value ?? '';
  }
  return [...byIndex.keys()].sort((a, b) => a - b).map((key) => byIndex.get(key)!);
}

const blank = (value: string | undefined) => (value && value !== '' ? value : undefined);
const within = (ind: string) => ind === 'W' || ind === 'B';
const above = (ind: string) => ind === 'A' || ind === 'B';
const combine = (w: boolean, a: boolean) => (w && a ? 'B' : w ? 'W' : a ? 'A' : '');

const RipSpecificImpactsView: FC<Props> = ({ section, checklistId, canEdit, submitted }) => {
  const { display } = useNotification();
  const [fields, setFields] = useState<ProtocolChecklistField[]>(section.fields);
  const [openImpacts, setOpenImpacts] = useState<Row[]>([]);
  const [otherImpacts, setOtherImpacts] = useState<Row[]>([]);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);

  const hydrate = useCallback((source: ProtocolChecklistField[]) => {
    setOpenImpacts(parseRows(source, 'Specific impact'));
    setOtherImpacts(parseRows(source, 'Other specific impact'));
  }, []);

  useEffect(() => {
    setFields(section.fields);
    hydrate(section.fields);
  }, [section.fields, hydrate]);

  const setInd = (setter: Dispatch<SetStateAction<Row[]>>, index: number, w: boolean, a: boolean) =>
    setter((prev) =>
      prev.map((r, i) => (i === index ? { ...r, SPEC_IMPACT_IND: combine(w, a) } : r)),
    );
  const setOtherDesc = (index: number, value: string) =>
    setOtherImpacts((prev) => prev.map((r, i) => (i === index ? { ...r, DESCRIPTION: value } : r)));

  const buildOpen = (): RipOpenSpecImpactRow[] =>
    openImpacts.map((r) => ({
      openingSpecificImpactId: blank(r.OPENING_SPECIFIC_IMPACT_ID),
      openingSpecificImpactType: blank(r.OPENING_SPECIFIC_IMPACT_TYPE),
      specImpactInd: blank(r.SPEC_IMPACT_IND),
      revisionCount: blank(r.REVISION_COUNT),
    }));
  const buildOther = (): RipOtherSpecImpactRow[] =>
    otherImpacts.map((r) => ({
      otherRiparianSpecImpactId: blank(r.OTHER_OPENING_SPEC_IMPACT_ID),
      description: blank(r.DESCRIPTION),
      specImpactInd: blank(r.SPEC_IMPACT_IND),
      revisionCount: blank(r.REVISION_COUNT),
    }));

  const handleSave = async () => {
    setBusy(true);
    try {
      await API.protocolChecklist.saveRipSpecificImpacts(checklistId, {
        checklistId,
        openImpacts: buildOpen(),
        otherImpacts: buildOther(),
      });
      const refreshed = await API.protocolChecklist.getChecklist('rip', checklistId);
      const fresh = refreshed.sections.find((s) => s.id === section.id);
      if (fresh) {
        setFields(fresh.fields);
        hydrate(fresh.fields);
      }
      setEditing(false);
      display({ kind: 'success', title: 'Specific impacts saved', timeout: 4000 });
    } catch (err) {
      display({
        kind: 'error',
        title: 'Save failed',
        subtitle: err instanceof Error ? err.message : 'Unknown error',
        timeout: 9000,
      });
    } finally {
      setBusy(false);
    }
  };

  const cancel = () => {
    hydrate(fields);
    setEditing(false);
  };

  const showEditControls = canEdit && !submitted;

  const choiceCell = (
    setter: Dispatch<SetStateAction<Row[]>>,
    index: number,
    ind: string,
    isWithin: boolean,
  ) => (
    <input
      type="checkbox"
      checked={isWithin ? within(ind) : above(ind)}
      disabled={!editing}
      aria-label={isWithin ? 'Within stream reach' : 'Above stream reach'}
      onChange={(e) =>
        setInd(
          setter,
          index,
          isWithin ? e.target.checked : within(ind),
          isWithin ? above(ind) : e.target.checked,
        )
      }
    />
  );

  return (
    <div className="rip-field-data">
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

      <table className="rip-field-grid">
        <caption>Specific impacts for all &quot;No&quot; answers combined</caption>
        <thead>
          <tr>
            <th scope="col">Impact description</th>
            <th scope="col">Within stream reach</th>
            <th scope="col">Above stream reach</th>
          </tr>
        </thead>
        <tbody>
          {openImpacts.map((row, index) => {
            const ind = row.SPEC_IMPACT_IND ?? '';
            return (
              <Fragment key={`oi-${index}`}>
                {OPEN_IMPACT_HEADERS[index] && (
                  <tr className="rip-grid__section">
                    <th colSpan={3} scope="colgroup">
                      {OPEN_IMPACT_HEADERS[index]}
                    </th>
                  </tr>
                )}
                <tr>
                  <td>{row.DESCRIPTION || '—'}</td>
                  <td className="rip-grid__choice">
                    {choiceCell(setOpenImpacts, index, ind, true)}
                  </td>
                  <td className="rip-grid__choice">
                    {choiceCell(setOpenImpacts, index, ind, false)}
                  </td>
                </tr>
              </Fragment>
            );
          })}
        </tbody>
      </table>

      <table className="rip-field-grid">
        <caption>Other impacts</caption>
        <thead>
          <tr>
            <th scope="col">Impact description</th>
            <th scope="col">Within stream reach</th>
            <th scope="col">Above stream reach</th>
          </tr>
        </thead>
        <tbody>
          {otherImpacts.length === 0 ? (
            <tr>
              <td colSpan={3}>No other impacts.</td>
            </tr>
          ) : (
            otherImpacts.map((row, index) => {
              const ind = row.SPEC_IMPACT_IND ?? '';
              return (
                <tr key={`other-${index}`}>
                  <td>
                    {editing ? (
                      <TextInput
                        id={`other-desc-${index}`}
                        labelText="Impact description"
                        hideLabel
                        size="sm"
                        maxLength={50}
                        value={row.DESCRIPTION ?? ''}
                        onChange={(e) => setOtherDesc(index, e.target.value)}
                      />
                    ) : (
                      row.DESCRIPTION || '—'
                    )}
                  </td>
                  <td className="rip-grid__choice">
                    {choiceCell(setOtherImpacts, index, ind, true)}
                  </td>
                  <td className="rip-grid__choice">
                    {choiceCell(setOtherImpacts, index, ind, false)}
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>
    </div>
  );
};

export default RipSpecificImpactsView;
