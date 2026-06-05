import { ArrowLeft } from '@carbon/icons-react';
import {
  Button,
  Column,
  Grid,
  InlineNotification,
  SkeletonText,
  Tab,
  TabList,
  TabPanel,
  TabPanels,
  Tabs,
  Tag,
  Tile,
} from '@carbon/react';
import { useEffect, useState, type FC } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import type {
  ProtocolChecklist,
  ProtocolChecklistField,
  ProtocolType,
} from '@/types/protocolChecklist';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import API from '@/services/APIs';
import { PROTOCOL_TYPE_LABEL, PROTOCOL_TYPE_TO_BACKEND } from '@/types/protocolChecklist';

import './protocolChecklist.scss';

const extractValidationErrors = (err: unknown): string[] | null => {
  const body = (err as { body?: { validationErrors?: string[] } })?.body;
  return Array.isArray(body?.validationErrors) ? body.validationErrors : null;
};

function isProtocolType(value: string | undefined): value is ProtocolType {
  return value === 'biodiversity' || value === 'riparian' || value === 'water';
}

function renderFieldValue(field: ProtocolChecklistField): React.ReactNode {
  if (field.kind === 'YES_NO') {
    const yes = field.value?.toUpperCase() === 'Y';
    return (
      <Tag type={yes ? 'green' : 'gray'} size="sm">
        {yes ? 'Yes' : 'No'}
      </Tag>
    );
  }
  if (field.kind === 'MULTILINE') {
    return <p className="protocol-checklist__multiline">{field.value || '—'}</p>;
  }
  return field.value || '—';
}

const ProtocolChecklistPage: FC = () => {
  const { type, id = '' } = useParams<{ type: string; id: string }>();
  const navigate = useNavigate();
  const { display } = useNotification();
  const { canEdit } = useAuthorization();

  const [checklist, setChecklist] = useState<ProtocolChecklist | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [hasError, setHasError] = useState(false);
  const [busy, setBusy] = useState(false);
  const [validationErrors, setValidationErrors] = useState<string[]>([]);
  const [reloadKey, setReloadKey] = useState(0);

  const protocolType: ProtocolType | null = isProtocolType(type) ? type : null;
  const backendCode = protocolType ? PROTOCOL_TYPE_TO_BACKEND[protocolType] : null;

  useEffect(() => {
    if (!protocolType || !id) {
      setLoading(false);
      setNotFound(true);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setNotFound(false);
    setHasError(false);

    API.protocolChecklist
      .getChecklist(PROTOCOL_TYPE_TO_BACKEND[protocolType], id)
      .then((data) => {
        if (cancelled) return;
        setChecklist(data);
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
          title: "We couldn't load the checklist",
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
  }, [display, id, protocolType, reloadKey]);

  const handleSubmit = async () => {
    if (!backendCode) return;
    setBusy(true);
    setValidationErrors([]);
    try {
      await API.protocolChecklist.submit(backendCode, id);
      display({ kind: 'success', title: 'Checklist submitted', timeout: 5000 });
      setReloadKey((k) => k + 1);
    } catch (err) {
      const validation = extractValidationErrors(err);
      if (validation) {
        setValidationErrors(validation);
        display({ kind: 'warning', title: 'Submit blocked by validation', timeout: 6000 });
      } else {
        display({
          kind: 'error',
          title: 'Submit failed',
          subtitle: err instanceof Error ? err.message : 'Unknown error',
          timeout: 9000,
        });
      }
    } finally {
      setBusy(false);
    }
  };

  const handleUnsubmit = async () => {
    if (!backendCode) return;
    setBusy(true);
    try {
      await API.protocolChecklist.unsubmit(backendCode, id);
      display({ kind: 'success', title: 'Checklist reopened', timeout: 5000 });
      setReloadKey((k) => k + 1);
    } catch (err) {
      display({
        kind: 'error',
        title: 'Unsubmit failed',
        subtitle: err instanceof Error ? err.message : 'Unknown error',
        timeout: 9000,
      });
    } finally {
      setBusy(false);
    }
  };

  const submitted = checklist?.statusCode === 'SUB';

  return (
    <Grid fullWidth className="default-grid protocol-checklist-grid">
      <Column sm={4} md={8} lg={16}>
        <div className="protocol-checklist__header">
          <button
            type="button"
            className="protocol-checklist__back"
            onClick={() => navigate(-1)}
            aria-label="Back"
          >
            <ArrowLeft /> Back
          </button>
          <h1>{protocolType ? PROTOCOL_TYPE_LABEL[protocolType] : 'Protocol checklist'}</h1>
        </div>
      </Column>

      {loading && (
        <Column sm={4} md={8} lg={16}>
          <SkeletonText paragraph lineCount={6} data-testid="protocol-checklist-loading" />
        </Column>
      )}

      {!loading && notFound && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="warning"
            title="Checklist not found"
            subtitle={`No ${protocolType ?? 'protocol'} checklist exists for id ${id}.`}
            hideCloseButton
            lowContrast
          />
        </Column>
      )}

      {!loading && hasError && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="error"
            title="Unable to load checklist"
            subtitle="Please try again later."
            hideCloseButton
            lowContrast
          />
        </Column>
      )}

      {!loading && !notFound && !hasError && checklist && (
        <>
          <Column sm={4} md={8} lg={16}>
            <Tile className="protocol-checklist__summary">
              <div className="protocol-checklist__summary-grid">
                <div>
                  <span className="protocol-checklist__label">Checklist</span>
                  <span>{checklist.checklistId}</span>
                </div>
                <div>
                  <span className="protocol-checklist__label">Opening</span>
                  <span>{checklist.openingNumber}</span>
                </div>
                <div>
                  <span className="protocol-checklist__label">Master list year</span>
                  <span>{checklist.effectiveYear}</span>
                </div>
                <div>
                  <span className="protocol-checklist__label">Status</span>
                  <Tag type={checklist.statusCode === 'SUB' ? 'green' : 'blue'} size="sm">
                    {checklist.statusLabel}
                  </Tag>
                </div>
                <div>
                  <span className="protocol-checklist__label">Evaluator</span>
                  <span>{checklist.evaluatorUserid}</span>
                </div>
                <div>
                  <span className="protocol-checklist__label">Evaluation date</span>
                  <span>{checklist.evaluationDate}</span>
                </div>
              </div>
            </Tile>
          </Column>

          {canEdit && (
            <Column sm={4} md={8} lg={16}>
              <div className="protocol-checklist__actions">
                {protocolType === 'biodiversity' && !submitted && (
                  <Button
                    kind="tertiary"
                    onClick={() => navigate(`/protocol-checklists/biodiversity/${id}/edit`)}
                  >
                    Edit opening
                  </Button>
                )}
                {protocolType === 'biodiversity' && !submitted && (
                  <Button
                    kind="tertiary"
                    onClick={() => navigate(`/protocol-checklists/biodiversity/${id}/strata`)}
                  >
                    Edit strata
                  </Button>
                )}
                {protocolType === 'riparian' && !submitted && (
                  <Button
                    kind="tertiary"
                    onClick={() => navigate(`/protocol-checklists/riparian/${id}/stream-opening`)}
                  >
                    Edit stream opening
                  </Button>
                )}
                {protocolType === 'riparian' && !submitted && (
                  <Button
                    kind="tertiary"
                    onClick={() => navigate(`/protocol-checklists/riparian/${id}/field-data`)}
                  >
                    Edit field data
                  </Button>
                )}
                {protocolType === 'riparian' && !submitted && (
                  <Button
                    kind="tertiary"
                    onClick={() => navigate(`/protocol-checklists/riparian/${id}/other-indicators`)}
                  >
                    Edit other indicators
                  </Button>
                )}
                {protocolType === 'riparian' && !submitted && (
                  <Button
                    kind="tertiary"
                    onClick={() => navigate(`/protocol-checklists/riparian/${id}/questions`)}
                  >
                    Edit questions
                  </Button>
                )}
                {protocolType === 'riparian' && !submitted && (
                  <Button
                    kind="tertiary"
                    onClick={() => navigate(`/protocol-checklists/riparian/${id}/specific-impacts`)}
                  >
                    Edit specific impacts
                  </Button>
                )}
                {protocolType === 'riparian' && !submitted && (
                  <Button
                    kind="tertiary"
                    onClick={() => navigate(`/protocol-checklists/riparian/${id}/final-comments`)}
                  >
                    Edit final comments
                  </Button>
                )}
                {protocolType === 'water' && !submitted && (
                  <Button
                    kind="tertiary"
                    onClick={() => navigate(`/protocol-checklists/water/${id}/sample-area`)}
                  >
                    Edit sample area
                  </Button>
                )}
                {protocolType === 'water' && !submitted && (
                  <Button
                    kind="tertiary"
                    onClick={() => navigate(`/protocol-checklists/water/${id}/sample-site`)}
                  >
                    Edit sample site
                  </Button>
                )}
                {protocolType === 'water' && !submitted && (
                  <Button
                    kind="tertiary"
                    onClick={() => navigate(`/protocol-checklists/water/${id}/assessment`)}
                  >
                    Edit assessment
                  </Button>
                )}
                {protocolType === 'water' && !submitted && (
                  <Button
                    kind="tertiary"
                    onClick={() => navigate(`/protocol-checklists/water/${id}/range`)}
                  >
                    Edit range
                  </Button>
                )}
                {!submitted && (
                  <Button onClick={() => void handleSubmit()} disabled={busy}>
                    Submit
                  </Button>
                )}
                {submitted && (
                  <Button kind="tertiary" onClick={() => void handleUnsubmit()} disabled={busy}>
                    Unsubmit
                  </Button>
                )}
              </div>
            </Column>
          )}

          {validationErrors.length > 0 && (
            <Column sm={4} md={8} lg={16}>
              <div className="protocol-checklist__errors">
                {validationErrors.map((message) => (
                  <InlineNotification
                    key={message}
                    kind="error"
                    title="Validation"
                    subtitle={message}
                    hideCloseButton
                    lowContrast
                  />
                ))}
              </div>
            </Column>
          )}

          <Column sm={4} md={8} lg={16}>
            <Tabs>
              <TabList aria-label="Checklist sections">
                {checklist.sections.map((section) => (
                  <Tab key={section.id}>{section.title}</Tab>
                ))}
              </TabList>
              <TabPanels>
                {checklist.sections.map((section) => (
                  <TabPanel key={section.id}>
                    <div className="protocol-checklist__fields">
                      {section.fields.map((field) => (
                        <div className="protocol-checklist__field" key={field.label}>
                          <span className="protocol-checklist__label">{field.label}</span>
                          <span className="protocol-checklist__value">
                            {renderFieldValue(field)}
                          </span>
                        </div>
                      ))}
                    </div>
                  </TabPanel>
                ))}
              </TabPanels>
            </Tabs>
          </Column>
        </>
      )}
    </Grid>
  );
};

export default ProtocolChecklistPage;
