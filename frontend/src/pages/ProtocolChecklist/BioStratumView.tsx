import { Add, TrashCan } from '@carbon/icons-react';
import { Button, Checkbox, Select, SelectItem, SkeletonText, TextInput } from '@carbon/react';
import { useCallback, useEffect, useState, type FC, type ReactNode } from 'react';

import type { BioStratum, BioStratumRow, BioWindthrowTreatment } from '@/types/protocolChecklist';

import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';

/**
 * Biodiversity Stratum Summary section (FREP211) — edited inline in place (no separate page).
 * A master-detail editor: pick (or add) a stratum from the rail, then edit its scalar fields,
 * indicator flags, and windthrow treatments. Save round-trips the full DTO (including unsurfaced
 * columns + revision count). Plots for the stratum live on the separate Plots tab, as in legacy.
 */

type Props = {
  checklistId: string;
  canEdit: boolean;
  submitted: boolean;
};

type FieldDef = { key: string; label: string };

const TEXT_GROUPS: { title: string; fields: FieldDef[] }[] = [
  {
    title: 'Summary',
    fields: [
      { key: 'stratumNumber', label: 'Stratum number' },
      { key: 'strataTypeCode', label: 'Strata type code' },
      { key: 'summaryDate', label: 'Summary date (YYYY-MM-DD)' },
      { key: 'assessorName', label: 'Assessor name' },
      { key: 'plotCount', label: 'Plot count' },
      { key: 'size', label: 'Stratum size' },
      { key: 'estimatedSize', label: 'Estimated size' },
    ],
  },
  {
    title: 'Patch',
    fields: [
      { key: 'patchLocationCode', label: 'Patch location code' },
      { key: 'patchEstimatedOldestTreeAge', label: 'Estimated oldest tree age' },
      { key: 'patchGeneralComment', label: 'Patch general comment' },
      { key: 'patchWindthrowPct', label: 'Patch windthrow %' },
    ],
  },
  {
    title: 'Constraints',
    fields: [
      { key: 'wetlandPct', label: 'Wetland %' },
      { key: 'harvestAreaCode', label: 'Harvest area code' },
      { key: 'riparianManagementZonePct', label: 'Riparian mgmt zone %' },
      { key: 'riparianReserveZonePct', label: 'Riparian reserve zone %' },
      { key: 'rockOutcropPct', label: 'Rock outcrop %' },
      { key: 'nonCommercialBrushPct', label: 'Non-commercial brush %' },
      { key: 'nonMerchTimberPct', label: 'Non-merch timber %' },
      { key: 'sensitiveSoilPct', label: 'Sensitive soil %' },
      { key: 'ungHoofAnimalWinteringPct', label: 'Ungulate wintering %' },
      { key: 'wildlifeHabitatAreaPct', label: 'Wildlife habitat area %' },
      { key: 'oldGrowthManagementAreaPct', label: 'OGMA %' },
      { key: 'visualsPct', label: 'Visuals %' },
      { key: 'culturalHeritageFeaturePct', label: 'Cultural heritage feature %' },
      { key: 'recreationFeaturePct', label: 'Recreation feature %' },
      { key: 'otherConstraint', label: 'Other constraint' },
      { key: 'otherConstraintPct', label: 'Other constraint %' },
    ],
  },
  {
    title: 'Eco anchors',
    fields: [
      { key: 'bearDenCnt', label: 'Bear den count' },
      { key: 'hibernaculumCnt', label: 'Hibernaculum count' },
      { key: 'vetTreeCnt', label: 'Veteran tree count' },
      { key: 'mineralLickCnt', label: 'Mineral lick count' },
      { key: 'largeStickNestCnt', label: 'Large stick nest count' },
      { key: 'cavityNestCnt', label: 'Cavity nest count' },
      { key: 'largeHallowTreeCnt', label: 'Large hollow tree count' },
      { key: 'largeWitchesBroomCnt', label: "Large witches' broom count" },
      { key: 'otherEcoAnchorCnt', label: 'Other eco anchor count' },
      { key: 'otherEcoAnchorDesc', label: 'Other eco anchor description' },
    ],
  },
  {
    title: 'BEC',
    fields: [
      { key: 'bgcZoneCode', label: 'BGC zone' },
      { key: 'bgcSubzoneCode', label: 'BGC subzone' },
      { key: 'bgcVariant', label: 'BGC variant' },
      { key: 'bgcPhase', label: 'BGC phase' },
      { key: 'becSiteSeriesCd', label: 'Site series' },
      { key: 'siteSeriesPhaseCd', label: 'Site series phase' },
      { key: 'seral', label: 'Seral' },
    ],
  },
  {
    title: 'Windthrow',
    fields: [
      { key: 'windthrowDistributionCode', label: 'Windthrow distribution code' },
      { key: 'otherWindthrowTreatment', label: 'Other windthrow treatment' },
    ],
  },
];

// Harvest area code is a fixed "tick one of" choice (legacy FREP211 radio HNR/HDR/PCH).
const HARVEST_AREA_OPTIONS = [
  { code: 'HNR', label: 'Harvest area with no retention' },
  { code: 'HDR', label: 'Harvest area with dispersed retention' },
  { code: 'PCH', label: 'Patch reserve' },
];

// FREP_211_BIOSTRATUM.SAVE_STRATUM validate() rejects a save unless these are set
// (sil.error.usr.isrequired:...). Mirror the legacy Frep211ValidationManager required checks here so
// the user gets a clear message instead of a raw Oracle error.
const REQUIRED_FIELDS: { key: string; label: string }[] = [
  { key: 'plotCount', label: 'Plot count' },
  { key: 'harvestAreaCode', label: 'Harvest area' },
  { key: 'bgcZoneCode', label: 'BGC zone' },
];
const REQUIRED_KEYS = new Set(REQUIRED_FIELDS.map((f) => f.key));

// Legacy validate_stratum_number mask: at most 2 digits and at most 3 letters (else
// frep.web.usr.database.record.badStratumFormat).
const stratumNumberValid = (value: string): boolean => {
  const v = value.trim();
  if (!v) return true;
  const digits = (v.match(/[0-9]/g) ?? []).length;
  const letters = (v.match(/[^0-9]/g) ?? []).length;
  return digits <= 2 && letters <= 3;
};

const IND_FIELDS: FieldDef[] = [
  { key: 'consistentMapInd', label: 'Consistent with map' },
  { key: 'constraintIndicator', label: 'Constraints present' },
  { key: 'ecoIndicator', label: 'Eco anchors present' },
  { key: 'karstFeatureInd', label: 'Karst feature' },
  { key: 'largestTreeInd', label: 'Largest tree' },
  { key: 'cwdHeavyConcentrationInd', label: 'Heavy CWD concentration' },
  { key: 'activeWildlifeTrailsInd', label: 'Active wildlife trails' },
  { key: 'activeWltCwdFeedingInd', label: 'Active WLT/CWD feeding' },
  { key: 'uncommonTreeSpeciesInd', label: 'Uncommon tree species' },
];

const BioStratumView: FC<Props> = ({ checklistId, canEdit, submitted }) => {
  const { display } = useNotification();
  const [rows, setRows] = useState<BioStratumRow[]>([]);
  const [current, setCurrent] = useState<BioStratum | null>(null);
  const [loading, setLoading] = useState(true);
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

  const loadList = useCallback(async () => {
    const list = await API.protocolChecklist.listBioStrata(checklistId);
    setRows(list);
    return list;
  }, [checklistId]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    API.protocolChecklist
      .listBioStrata(checklistId)
      .then((list) => {
        if (!cancelled) setRows(list);
      })
      .catch((err: unknown) => {
        if (!cancelled) reportError("We couldn't load the strata", err);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [checklistId, reportError]);

  const readOnly = !canEdit || submitted;

  const get = (key: string): string =>
    ((current as Record<string, unknown>)?.[key] as string | undefined) ?? '';
  const set = (key: string, value: string) =>
    setCurrent((prev) => (prev ? ({ ...prev, [key]: value } as BioStratum) : prev));

  const select = async (stratumId: string) => {
    setBusy(true);
    try {
      setCurrent(await API.protocolChecklist.getBioStratum(stratumId));
    } catch (err) {
      reportError('Could not load the stratum', err);
    } finally {
      setBusy(false);
    }
  };

  const addStratum = async () => {
    setBusy(true);
    try {
      const { stratumNumber } = await API.protocolChecklist.nextStratumNumber();
      setCurrent({ checklistId, stratumNumber, windthrowTreatments: [] });
    } catch (err) {
      reportError('Could not start a new stratum', err);
    } finally {
      setBusy(false);
    }
  };

  const handleSave = async () => {
    if (!current) return;
    // Pre-empt the proc's validate() rejections with clear, field-level messages.
    const missing = REQUIRED_FIELDS.filter((f) => !get(f.key).trim()).map((f) => f.label);
    if (missing.length > 0) {
      reportError('Required fields missing', new Error(`Please enter: ${missing.join(', ')}.`));
      return;
    }
    if (!stratumNumberValid(get('stratumNumber'))) {
      reportError(
        'Invalid stratum number',
        new Error('Stratum number allows at most 2 digits and 3 letters.'),
      );
      return;
    }
    setBusy(true);
    try {
      const saved = await API.protocolChecklist.saveBioStratum(checklistId, current);
      setCurrent(saved);
      await loadList();
      display({ kind: 'success', title: 'Stratum saved', timeout: 4000 });
    } catch (err) {
      reportError('Save failed', err);
    } finally {
      setBusy(false);
    }
  };

  const handleDelete = async () => {
    if (!current?.stratumId) {
      setCurrent(null);
      return;
    }
    setBusy(true);
    try {
      await API.protocolChecklist.deleteBioStratum(current.stratumId, current.revisionCount ?? '');
      setCurrent(null);
      await loadList();
      display({ kind: 'success', title: 'Stratum deleted', timeout: 4000 });
    } catch (err) {
      reportError('Delete failed', err);
    } finally {
      setBusy(false);
    }
  };

  const setTreatment = (index: number, patch: Partial<BioWindthrowTreatment>) =>
    setCurrent((prev) =>
      prev
        ? {
            ...prev,
            windthrowTreatments: (prev.windthrowTreatments ?? []).map((t, i) =>
              i === index ? { ...t, ...patch } : t,
            ),
          }
        : prev,
    );
  const addTreatment = () =>
    setCurrent((prev) =>
      prev
        ? {
            ...prev,
            windthrowTreatments: [...(prev.windthrowTreatments ?? []), { code: '', checkInd: 'Y' }],
          }
        : prev,
    );
  const removeTreatment = (index: number) =>
    setCurrent((prev) =>
      prev
        ? {
            ...prev,
            windthrowTreatments: (prev.windthrowTreatments ?? []).filter((_, i) => i !== index),
          }
        : prev,
    );

  const field = (key: string, label: string): ReactNode => {
    const lbl = REQUIRED_KEYS.has(key) ? `${label} (required)` : label;

    // Harvest area is a coded "tick one of" choice — render it as a dropdown.
    if (key === 'harvestAreaCode') {
      if (readOnly) {
        const text = HARVEST_AREA_OPTIONS.find((o) => o.code === get(key))?.label ?? get(key);
        return (
          <div className="protocol-checklist__field" key={key}>
            <span className="protocol-checklist__label">{lbl}</span>
            <span className="protocol-checklist__value">{text || '—'}</span>
          </div>
        );
      }
      return (
        <Select
          key={key}
          id={`stratum-${key}`}
          labelText={lbl}
          value={get(key)}
          onChange={(e) => set(key, e.target.value)}
        >
          <SelectItem value="" text="—" />
          {HARVEST_AREA_OPTIONS.map((o) => (
            <SelectItem key={o.code} value={o.code} text={o.label} />
          ))}
        </Select>
      );
    }

    return readOnly ? (
      <div className="protocol-checklist__field" key={key}>
        <span className="protocol-checklist__label">{lbl}</span>
        <span className="protocol-checklist__value">{get(key) || '—'}</span>
      </div>
    ) : (
      <TextInput
        key={key}
        id={`stratum-${key}`}
        labelText={lbl}
        value={get(key)}
        onChange={(e) => set(key, e.target.value)}
      />
    );
  };

  if (loading) {
    return <SkeletonText paragraph lineCount={8} />;
  }

  const treatments = current?.windthrowTreatments ?? [];

  return (
    <div className="rip-form">
      <div className="bio-master">
        <div className="bio-master__list">
          {rows.length === 0 && <p>No strata yet.</p>}
          {rows.map((row) => (
            <Button
              key={row.stratumId}
              kind={current?.stratumId === row.stratumId ? 'primary' : 'ghost'}
              size="sm"
              disabled={busy}
              onClick={() => void select(row.stratumId ?? '')}
            >
              {row.stratumNumber || row.stratumId}
            </Button>
          ))}
          {!readOnly && (
            <Button
              kind="tertiary"
              size="sm"
              renderIcon={Add}
              disabled={busy}
              onClick={() => void addStratum()}
            >
              Add stratum
            </Button>
          )}
        </div>

        <div className="bio-master__detail">
          {!current ? (
            <p>Select or add a stratum to edit.</p>
          ) : (
            <>
              {TEXT_GROUPS.map((group) => (
                <fieldset key={group.title} className="rip-form__group">
                  <legend>{group.title}</legend>
                  <div className="rip-form__grid">
                    {group.fields.map((f) => field(f.key, f.label))}
                  </div>
                </fieldset>
              ))}

              <fieldset className="rip-form__group">
                <legend>Indicators</legend>
                <div className="rip-form__grid">
                  {IND_FIELDS.map((f) =>
                    readOnly ? (
                      <div className="protocol-checklist__field" key={f.key}>
                        <span className="protocol-checklist__label">{f.label}</span>
                        <span className="protocol-checklist__value">
                          {get(f.key) === 'Y' ? 'Yes' : 'No'}
                        </span>
                      </div>
                    ) : (
                      <Checkbox
                        key={f.key}
                        id={`stratum-${f.key}`}
                        labelText={f.label}
                        checked={get(f.key) === 'Y'}
                        onChange={(_e, { checked }) => set(f.key, checked ? 'Y' : 'N')}
                      />
                    ),
                  )}
                </div>
              </fieldset>

              <fieldset className="rip-form__group">
                <legend>Windthrow treatments</legend>
                <table className="rip-field-grid">
                  <thead>
                    <tr>
                      <th scope="col">Treatment code</th>
                      <th scope="col">Applied</th>
                      {!readOnly && <th aria-label="Actions" />}
                    </tr>
                  </thead>
                  <tbody>
                    {treatments.length === 0 && (
                      <tr>
                        <td colSpan={readOnly ? 2 : 3}>No treatments.</td>
                      </tr>
                    )}
                    {treatments.map((t, index) => (
                      <tr key={t.windthrowTreatmentId ?? `wt-${index}`}>
                        <td>
                          {readOnly ? (
                            t.code || '—'
                          ) : (
                            <TextInput
                              id={`wt-code-${index}`}
                              labelText="Treatment code"
                              hideLabel
                              size="sm"
                              value={t.code ?? ''}
                              onChange={(e) => setTreatment(index, { code: e.target.value })}
                            />
                          )}
                        </td>
                        <td className="rip-grid__choice">
                          {readOnly ? (
                            t.checkInd === 'Y' ? (
                              'Yes'
                            ) : (
                              'No'
                            )
                          ) : (
                            <Checkbox
                              id={`wt-checked-${index}`}
                              labelText="Applied"
                              hideLabel
                              checked={t.checkInd === 'Y'}
                              onChange={(_e, { checked }) =>
                                setTreatment(index, { checkInd: checked ? 'Y' : 'N' })
                              }
                            />
                          )}
                        </td>
                        {!readOnly && (
                          <td>
                            <Button
                              kind="danger--tertiary"
                              size="sm"
                              hasIconOnly
                              renderIcon={TrashCan}
                              iconDescription="Remove treatment"
                              onClick={() => removeTreatment(index)}
                            />
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
                {!readOnly && (
                  <Button kind="ghost" size="sm" renderIcon={Add} onClick={addTreatment}>
                    Add treatment
                  </Button>
                )}
              </fieldset>

              {!readOnly && (
                <div className="protocol-checklist__actions">
                  <Button disabled={busy} onClick={() => void handleSave()}>
                    Save stratum
                  </Button>
                  {current.stratumId && (
                    <Button
                      kind="danger--tertiary"
                      disabled={busy}
                      onClick={() => void handleDelete()}
                    >
                      Delete stratum
                    </Button>
                  )}
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default BioStratumView;
