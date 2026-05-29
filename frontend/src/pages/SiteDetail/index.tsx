import { ArrowLeft } from '@carbon/icons-react';
import {
  Column,
  Grid,
  InlineNotification,
  SkeletonText,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  Tag,
} from '@carbon/react';
import { useEffect, useState, type FC } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';

import type { SiteDetail, SiteResource } from '@/types/siteDetail';

import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';

import './siteDetail.scss';

const PROTOCOL_TO_PATH: Record<string, 'biodiversity' | 'riparian' | 'water' | undefined> = {
  BIO: 'biodiversity',
  RIP: 'riparian',
  WAT: 'water',
};

const RESOURCE_HEADERS = [
  { key: 'resourceName', header: 'Resource value' },
  { key: 'statusCode', header: 'Status' },
  { key: 'rejectionReasonCode', header: 'Rejection reason' },
  { key: 'rationale', header: 'Rationale' },
  { key: 'otherComments', header: 'Other comments' },
  { key: 'checklistStatusCode', header: 'Checklist' },
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

function renderResourceCell(key: string, resource: SiteResource): React.ReactNode {
  const value = resource[key as keyof SiteResource];

  if (key === 'statusCode') {
    const statusCode = resource.statusCode;
    return (
      <Tag type={STATUS_TAG_TYPE[statusCode] ?? 'gray'} size="sm">
        {STATUS_LABEL[statusCode] ?? statusCode}
      </Tag>
    );
  }

  if (key === 'checklistStatusCode') {
    if (!resource.checklistId) return '—';
    const protocolPath = PROTOCOL_TO_PATH[resource.resourceType];
    const label = `${resource.checklistStatusCode ?? ''} (#${resource.checklistId})`;
    return protocolPath ? (
      <RouterLink to={`/protocol-checklists/${protocolPath}/${resource.checklistId}`}>
        {label}
      </RouterLink>
    ) : (
      label
    );
  }

  return value ?? '—';
}

const HeaderRow: FC<{ label: string; value: string | null | undefined }> = ({ label, value }) => (
  <div className="site-detail__field">
    <span className="site-detail__field-label">{label}</span>
    <span className="site-detail__field-value">{value ?? '—'}</span>
  </div>
);

const SiteDetailPage: FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { display } = useNotification();

  const [detail, setDetail] = useState<SiteDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [hasError, setHasError] = useState(false);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    setLoading(true);
    setNotFound(false);
    setHasError(false);

    API.siteDetail
      .getSiteDetail(id)
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
        const message = err instanceof Error ? err.message : 'Unknown error';
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
  }, [display, id]);

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
          <h1>Site Details</h1>
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
            <section className="site-detail__panel">
              <h2 className="site-detail__panel-title">Site header</h2>
              <div className="site-detail__grid">
                <HeaderRow label="Master list" value={detail.masterList} />
                <HeaderRow label="Org unit" value={detail.orgUnit} />
                <HeaderRow label="Client" value={detail.client} />
                <HeaderRow label="Client name" value={detail.clientName} />
                <HeaderRow label="Opening" value={detail.opening} />
                <HeaderRow label="Opening ID" value={detail.openingId} />
                <HeaderRow label="Licence" value={detail.licenceNo} />
                <HeaderRow label="CP" value={detail.cuttingPermitId} />
                <HeaderRow label="Cut block" value={detail.cutBlockId} />
                <HeaderRow label="FSP" value={detail.fspLink} />
                <HeaderRow label="Harvest year" value={detail.harvestYear} />
              </div>
            </section>
          </Column>

          <Column sm={4} md={8} lg={16}>
            <section className="site-detail__panel">
              <h2 className="site-detail__panel-title">Resource values</h2>
              {detail.resources.length === 0 ? (
                <p>No resource values have been evaluated for this site.</p>
              ) : (
                <TableContainer
                  title="Resource evaluations"
                  description="Accept / reject / target decisions for each protocol"
                >
                  <Table>
                    <TableHead>
                      <TableRow>
                        {RESOURCE_HEADERS.map((header) => (
                          <TableHeader key={header.key}>{header.header}</TableHeader>
                        ))}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {detail.resources.map((resource) => (
                        <TableRow key={resource.resourceType}>
                          {RESOURCE_HEADERS.map((header) => (
                            <TableCell key={header.key}>
                              {renderResourceCell(header.key, resource)}
                            </TableCell>
                          ))}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </section>
          </Column>
        </>
      )}
    </Grid>
  );
};

export default SiteDetailPage;
