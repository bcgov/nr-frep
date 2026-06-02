import { Add, TrashCan } from '@carbon/icons-react';
import {
  Button,
  Checkbox,
  Column,
  Grid,
  InlineNotification,
  SkeletonText,
  Stack,
  TextInput,
  Tile,
} from '@carbon/react';
import { useCallback, useEffect, useState, type FC } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import type { BioStratum, BioStratumRow, BioWindthrowTreatment } from '@/types/protocolChecklist';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import API from '@/services/APIs';

import './protocolChecklist.scss';

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

const BioStrataEditPage: FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { display } = useNotification();
  const { canEdit } = useAuthorization();

  const [rows, setRows] = useState<BioStratumRow[]>([]);
  const [current, setCurrent] = useState<BioStratum | null>(null);
  const [status, setStatus] = useState<string | undefined>(undefined);
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
    const list = await API.protocolChecklist.listBioStrata(id);
    setRows(list);
    return list;
  }, [id]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([
      API.protocolChecklist.listBioStrata(id),
      API.protocolChecklist.getChecklist('bio', id).then(
        (c) => c.statusCode,
        () => undefined,
      ),
    ])
      .then(([list, statusCode]) => {
        if (cancelled) return;
        setRows(list);
        setStatus(statusCode);
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
  }, [id, reportError]);

  const readOnly = !canEdit || status === 'SUB';

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
      setCurrent({ checklistId: id, stratumNumber, windthrowTreatments: [] });
    } catch (err) {
      reportError('Could not start a new stratum', err);
    } finally {
      setBusy(false);
    }
  };

  const handleSave = async () => {
    if (!current) return;
    setBusy(true);
    try {
      const saved = await API.protocolChecklist.saveBioStratum(id, current);
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
        <h1>Biodiversity strata — checklist {id}</h1>
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

      <Column sm={4} md={2} lg={4}>
        <div className="chr-checklist__feature-list">
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
      </Column>

      <Column sm={4} md={6} lg={12}>
        {!current ? (
          <p>Select or add a stratum to edit.</p>
        ) : (
          <Tile>
            <Stack gap={6}>
              {TEXT_GROUPS.map((group) => (
                <fieldset key={group.title} className="chr-checklist__fieldset">
                  <legend>{group.title}</legend>
                  {group.fields.map((f) => (
                    <TextInput
                      key={f.key}
                      id={`stratum-${f.key}`}
                      labelText={f.label}
                      value={get(f.key)}
                      disabled={readOnly}
                      onChange={(e) => set(f.key, e.target.value)}
                    />
                  ))}
                </fieldset>
              ))}
              <fieldset className="chr-checklist__fieldset">
                <legend>Indicators</legend>
                {IND_FIELDS.map((f) => (
                  <Checkbox
                    key={f.key}
                    id={`stratum-${f.key}`}
                    labelText={f.label}
                    checked={get(f.key) === 'Y'}
                    disabled={readOnly}
                    onChange={(_e, { checked }) => set(f.key, checked ? 'Y' : 'N')}
                  />
                ))}
              </fieldset>
              <fieldset className="chr-checklist__fieldset">
                <legend>Windthrow treatments</legend>
                {(current.windthrowTreatments ?? []).map((t, index) => (
                  <div
                    key={t.windthrowTreatmentId ?? `wt-${index}`}
                    className="chr-checklist__form"
                  >
                    <TextInput
                      id={`wt-code-${index}`}
                      labelText="Treatment code"
                      value={t.code ?? ''}
                      disabled={readOnly}
                      onChange={(e) => setTreatment(index, { code: e.target.value })}
                    />
                    <Checkbox
                      id={`wt-checked-${index}`}
                      labelText="Applied"
                      checked={t.checkInd === 'Y'}
                      disabled={readOnly}
                      onChange={(_e, { checked }) =>
                        setTreatment(index, { checkInd: checked ? 'Y' : 'N' })
                      }
                    />
                    {!readOnly && (
                      <Button
                        kind="danger--tertiary"
                        size="sm"
                        renderIcon={TrashCan}
                        onClick={() => removeTreatment(index)}
                      >
                        Remove
                      </Button>
                    )}
                  </div>
                ))}
                {!readOnly && (
                  <Button kind="ghost" size="sm" renderIcon={Add} onClick={addTreatment}>
                    Add treatment
                  </Button>
                )}
              </fieldset>

              <div className="protocol-checklist__actions">
                {!readOnly && (
                  <Button onClick={() => void handleSave()} disabled={busy}>
                    Save stratum
                  </Button>
                )}
                {!readOnly && current.stratumId && (
                  <Button
                    kind="danger--tertiary"
                    onClick={() => void handleDelete()}
                    disabled={busy}
                  >
                    Delete stratum
                  </Button>
                )}
                {current.stratumId && (
                  <Button
                    kind="tertiary"
                    onClick={() =>
                      navigate(
                        `/protocol-checklists/biodiversity/${id}/strata/${current.stratumId}/plots`,
                      )
                    }
                  >
                    Edit plots
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
};

export default BioStrataEditPage;
