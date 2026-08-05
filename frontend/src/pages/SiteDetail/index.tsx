import { ArrowLeft, Locked } from '@carbon/icons-react';
import {
  Button,
  Column,
  Grid,
  InlineNotification,
  Select,
  SelectItem,
  SkeletonText,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  Tag,
  TextInput,
  Tooltip,
} from '@carbon/react';
import { useEffect, useMemo, useState, type FC } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';

import type { RejectionReason } from '@/types/configuration';
import type { SiteDetail, SiteResource } from '@/types/siteDetail';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import API from '@/services/APIs';
import { apiErrorMessage } from '@/utils/apiError';
import { formatShortDate } from '@/utils/date';

import './siteDetail.scss';

const RESOURCE_HEADERS = [
  { key: 'resourceName', header: 'Resource value' },
  { key: 'statusCode', header: 'Status' },
  { key: 'rejectionReasonCode', header: 'Rejection reason' },
  { key: 'rationale', header: 'Rationale' },
  { key: 'otherComments', header: 'Other comments' },
] as const;

const STATUS_LABEL: Record<string, string> = {
  ACC: 'Accepted',
  REJ: 'Rejected',
  TAR: 'Targeted',
};

const STATUS_TAG_TYPE: Record<string, 'green' | 'red' | 'blue' | 'gray'> = {
  ACC: 'green',
  REJ: 'red',
  TAR: 'blue',
};

const SUBMITTED = 'SUB';
const OTHER_REASON = 'OTH';
const MAX_RATIONALE_LENGTH = 50;
const MAX_COMMENTS_LENGTH = 2000;

const isSubmitted = (resource: SiteResource): boolean => resource.checklistStatusCode === SUBMITTED;

function renderResourceCell(key: string, resource: SiteResource): React.ReactNode {
  const value = resource[key as keyof SiteResource];

  if (key === 'resourceName') {
    return (
      <span className="site-detail__resource-name">
        {isSubmitted(resource) && (
          <Tooltip label="Submitted" align="right">
            <button type="button" className="site-detail__lock" aria-label="Submitted">
              <Locked />
            </button>
          </Tooltip>
        )}
        {resource.resourceName || '—'}
      </span>
    );
  }

  if (key === 'statusCode') {
    const statusCode = (resource.statusCode ?? '').trim();
    if (!statusCode) return '—';
    return (
      <Tag type={STATUS_TAG_TYPE[statusCode] ?? 'gray'} size="sm">
        {STATUS_LABEL[statusCode] ?? statusCode}
      </Tag>
    );
  }

  return value ?? '—';
}

function renderEditableCell(
  key: string,
  resource: SiteResource,
  index: number,
  patchRow: (index: number, patch: Partial<SiteResource>) => void,
  rejectionReasons: RejectionReason[],
  errors: RowErrors | undefined,
): React.ReactNode {
  if (key === 'statusCode') {
    return (
      <Select
        id={`status-${index}`}
        labelText=""
        hideLabel
        size="sm"
        value={resource.statusCode ?? ''}
        onChange={(e) => patchRow(index, { statusCode: e.target.value })}
      >
        <SelectItem value="" text="" />
        <SelectItem value="ACC" text="Accepted" />
        <SelectItem value="REJ" text="Rejected" />
        <SelectItem value="TAR" text="Targeted" />
      </Select>
    );
  }
  if (key === 'rejectionReasonCode') {
    const error = errors?.rejectionReasonCode;
    return (
      <div className="site-detail__editable-cell">
        <Select
          id={`rejectionReasonCode-${index}`}
          labelText=""
          hideLabel
          size="sm"
          value={resource.rejectionReasonCode ?? ''}
          invalid={!!error}
          onChange={(e) => patchRow(index, { rejectionReasonCode: e.target.value || null })}
        >
          <SelectItem value="" text="" />
          {rejectionReasons.map((reason) => (
            <SelectItem key={reason.code} value={reason.code} text={reason.description} />
          ))}
        </Select>
        {error && <span className="site-detail__cell-error">{error}</span>}
      </div>
    );
  }
  if (key === 'rationale' || key === 'otherComments') {
    const error = errors?.[key];
    return (
      <div className="site-detail__editable-cell">
        <TextInput
          id={`${key}-${index}`}
          labelText=""
          hideLabel
          size="sm"
          value={(resource[key as keyof SiteResource] as string | null) ?? ''}
          invalid={!!error}
          onChange={(e) => patchRow(index, { [key]: e.target.value })}
        />
        {error && <span className="site-detail__cell-error">{error}</span>}
      </div>
    );
  }
  // resourceName stays read-only
  return renderResourceCell(key, resource);
}

/** Editable fields that can carry an inline validation message. */
type ResourceFieldKey = 'rejectionReasonCode' | 'rationale' | 'otherComments';
type RowErrors = Partial<Record<ResourceFieldKey, string>>;

/**
 * Per-resource save rules ported from legacy {@code Frep110ValidationManager}, returned as per-field
 * messages so the table can render them inline next to the offending input. The result is parallel to
 * {@code rows}; submitted (locked) and empty-status rows produce no errors (they aren't saved).
 */
function validateResources(rows: SiteResource[]): RowErrors[] {
  return rows.map((r) => {
    const errors: RowErrors = {};
    if (isSubmitted(r)) return errors;
    const status = (r.statusCode ?? '').trim();
    if (!status) return errors; // empty status is allowed — the row simply isn't saved
    const reason = (r.rejectionReasonCode ?? '').trim();
    const rationale = (r.rationale ?? '').trim();
    const comments = (r.otherComments ?? '').trim();
    const tooLong = `Must be ${MAX_RATIONALE_LENGTH} characters or fewer.`;

    if (status === 'TAR') {
      if (reason) errors.rejectionReasonCode = 'Must be blank for targeted resources.';
      if (!rationale) errors.rationale = 'Rationale is required for targeted resources.';
      else if (rationale.length > MAX_RATIONALE_LENGTH) errors.rationale = tooLong;
    } else if (status === 'REJ') {
      if (!reason) {
        errors.rejectionReasonCode = 'Rejection reason is required.';
      } else if (reason === OTHER_REASON && !rationale) {
        errors.rationale = 'Rationale is required when the reason is Other.';
      } else if (rationale.length > MAX_RATIONALE_LENGTH) {
        errors.rationale = tooLong;
      }
    } else {
      if (reason) errors.rejectionReasonCode = 'Must be blank for accepted resources.';
      if (rationale) errors.rationale = 'Must be blank for accepted resources.';
    }

    if (comments.length > MAX_COMMENTS_LENGTH)
      errors.otherComments = `Must be ${MAX_COMMENTS_LENGTH} characters or fewer.`;
    return errors;
  });
}

const HeaderRow: FC<{ label: string; value: string | null | undefined }> = ({ label, value }) => (
  <div className="site-detail__field">
    <span className="site-detail__field-label">{label}</span>
    <span className="site-detail__field-value">{value ?? '—'}</span>
  </div>
);

const SiteDetailPage: FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { display } = useNotification();
  const { canEdit } = useAuthorization();

  // "Add Target Site" create mode: no selected site yet, loaded by opening (FREP200 → SIL56 picker).
  // On save the proc creates the site + checklists, then we redirect to the real /site-detail/:id.
  const createOpeningId = searchParams.get('openingId') ?? '';
  const createOrgUnit = searchParams.get('orgUnit') ?? '';
  const createYear = searchParams.get('year') ?? '';
  const isCreate = createOpeningId !== '';

  const [detail, setDetail] = useState<SiteDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [hasError, setHasError] = useState(false);
  const [draft, setDraft] = useState<SiteResource[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [rejectionReasons, setRejectionReasons] = useState<RejectionReason[]>([]);

  // Inline validation runs live off the draft: a field's error shows as soon as the row is invalid
  // (e.g. status set to Rejected with no reason) and clears the moment it's fixed. Empty-status and
  // submitted rows produce no errors, so a freshly loaded form isn't a wall of red.
  const rowErrors = useMemo<RowErrors[]>(() => (draft ? validateResources(draft) : []), [draft]);
  const hasErrors = rowErrors.some((e) => Object.keys(e).length > 0);

  // The resource form is editable by default for authorized users — `draft` is
  // initialized from the loaded detail (see effect below), so there is no edit toggle.
  const editing = canEdit && draft !== null;

  // Legacy FREP110 disables Save when every resource is already submitted (nothing editable).
  const allSubmitted =
    !!detail && detail.resources.length > 0 && detail.resources.every(isSubmitted);

  const patchRow = (index: number, patch: Partial<SiteResource>) =>
    setDraft((prev) => (prev ? prev.map((r, i) => (i === index ? { ...r, ...patch } : r)) : prev));

  const handleSave = async () => {
    if (!draft) return;
    // Errors are already shown inline; just block the save while any remain.
    if (hasErrors) return;
    // Only save rows the user picked a status for; empty-status rows are left untouched,
    // and submitted (locked) rows are not re-sent.
    const toSave = draft.filter((r) => !isSubmitted(r) && (r.statusCode ?? '').trim() !== '');
    if (toSave.length === 0) {
      display({
        kind: 'info',
        title: 'Nothing to save',
        subtitle: 'Select a status (Accept, Reject, or Target) for at least one resource.',
        timeout: 6000,
      });
      return;
    }
    const payload = toSave.map((r) => ({
      resourceValueId: r.resourceValueId,
      resourceType: r.resourceType,
      statusCode: r.statusCode,
      rejectionReasonCode: r.rejectionReasonCode,
      rationale: r.rationale,
      otherComments: r.otherComments,
      revisionCount: r.revisionCount,
    }));
    setBusy(true);
    try {
      if (isCreate) {
        // Create the targeted site (spawns the checklists), then land on the real site-detail page.
        const created = await API.siteDetail.createTargetedSite({
          openingId: createOpeningId,
          orgUnit: createOrgUnit,
          effectiveYear: createYear,
          resources: payload,
        });
        display({ kind: 'success', title: 'Targeted site created', timeout: 4000 });
        navigate(`/site-detail/${created.frepSelectedSiteId}`, { replace: true });
        return;
      }
      const saved = await API.siteDetail.saveResources(id, payload);
      setDetail(saved);
      display({ kind: 'success', title: 'Site resources saved', timeout: 4000 });
    } catch (err) {
      display({
        kind: 'error',
        title: 'Save failed',
        subtitle: apiErrorMessage(err),
        timeout: 9000,
      });
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    if (!id && !isCreate) return;
    let cancelled = false;
    setLoading(true);
    setNotFound(false);
    setHasError(false);

    const request = isCreate
      ? API.siteDetail.getSiteDetailByOpening(createOpeningId, createYear)
      : API.siteDetail.getSiteDetail(id);

    request
      .then((data) => {
        if (cancelled) return;
        setDetail(data);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        const status = (err as { status?: number })?.status;
        if (status === 404) {
          setNotFound(true);
          return;
        }
        const message = apiErrorMessage(err);
        display({
          kind: 'error',
          title: "We couldn't load the site detail",
          subtitle: message,
          timeout: 9000,
        });
        setHasError(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [display, id, isCreate, createOpeningId, createYear]);

  // Keep the editable draft in sync with the loaded detail (and re-seed it after a
  // save). Authorized users edit in place; everyone else sees a read-only table.
  useEffect(() => {
    if (detail && canEdit) {
      setDraft(detail.resources.map((r) => ({ ...r })));
    } else {
      setDraft(null);
    }
  }, [detail, canEdit]);

  // Rejection-reason dropdown options (legacy FREP_CODE_LISTS.get_site_resource_reason_code).
  useEffect(() => {
    let cancelled = false;
    API.configuration
      .getRejectionReasons()
      .then((reasons) => {
        if (!cancelled) setRejectionReasons(reasons);
      })
      .catch(() => {
        // Non-fatal: the dropdown just renders empty if reasons can't be loaded.
        if (!cancelled) setRejectionReasons([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <Grid fullWidth className="default-grid site-detail-grid">
      <Column sm={4} md={8} lg={16}>
        <div className="site-detail__header">
          <button
            type="button"
            className="site-detail__back"
            onClick={() => navigate(-1)}
            aria-label="Back"
          >
            <ArrowLeft /> Back
          </button>
          <h1>{isCreate ? 'New Targeted Site' : 'Site Details'}</h1>
        </div>
      </Column>

      {loading && (
        <Column sm={4} md={8} lg={16}>
          <SkeletonText paragraph lineCount={6} data-testid="site-detail-loading" />
        </Column>
      )}

      {!loading && notFound && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="warning"
            title="Site not found"
            subtitle={`No site exists for id ${id}.`}
            hideCloseButton
            lowContrast
          />
        </Column>
      )}

      {!loading && hasError && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="error"
            title="Unable to load site"
            subtitle="Please try again later."
            hideCloseButton
            lowContrast
          />
        </Column>
      )}

      {!loading && !notFound && !hasError && detail && (
        <>
          <Column sm={4} md={8} lg={16}>
            <section className="site-detail__header-fields">
              <div className="site-detail__grid">
                <HeaderRow label="Master list" value={detail.masterList} />
                <HeaderRow label="Org unit" value={detail.orgUnit} />
                <HeaderRow label="Client" value={detail.client} />
                <HeaderRow label="Client name" value={detail.clientName} />
                <HeaderRow label="Opening" value={detail.opening} />
                <HeaderRow label="Opening ID" value={detail.openingId} />
                <HeaderRow label="Licence" value={detail.licenceNo} />
                <HeaderRow label="Cutting Permit" value={detail.cuttingPermitId} />
                <HeaderRow label="Cut block" value={detail.cutBlockId} />
                <HeaderRow label="FSP" value={detail.fspLink} />
                <HeaderRow label="Harvest year" value={formatShortDate(detail.harvestYear)} />
              </div>
            </section>
          </Column>

          <Column sm={4} md={8} lg={16}>
            <section className="site-detail__resources">
              {detail.resources.length === 0 ? (
                <p>No resource values have been evaluated for this site.</p>
              ) : (
                <>
                  {editing && (
                    <div className="site-detail__resource-actions">
                      <Button
                        size="md"
                        disabled={busy || allSubmitted}
                        onClick={() => void handleSave()}
                      >
                        Save
                      </Button>
                    </div>
                  )}
                  <TableContainer>
                    <Table>
                      <TableHead>
                        <TableRow>
                          {RESOURCE_HEADERS.map((header) => (
                            <TableHeader key={header.key}>{header.header}</TableHeader>
                          ))}
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {(editing && draft ? draft : detail.resources).map((resource, index) => (
                          <TableRow
                            // resourceType is unique per row on FREP110 (one row per resource
                            // value type) and always present, so it's a stable, collision-free key.
                            // (resourceValueId is "" for un-evaluated rows — `??` wouldn't catch
                            // that, leaving duplicate keys that ghost-duplicate rows after a save.)
                            key={
                              resource.resourceType || resource.resourceValueId || `row-${index}`
                            }
                          >
                            {RESOURCE_HEADERS.map((header) => (
                              <TableCell key={header.key}>
                                {editing && !isSubmitted(resource)
                                  ? renderEditableCell(
                                      header.key,
                                      resource,
                                      index,
                                      patchRow,
                                      rejectionReasons,
                                      rowErrors[index],
                                    )
                                  : renderResourceCell(header.key, resource)}
                              </TableCell>
                            ))}
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </>
              )}
            </section>
          </Column>
        </>
      )}
    </Grid>
  );
};

export default SiteDetailPage;
