import { ArrowLeft, Edit } from '@carbon/icons-react';
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
import { useEffect, useMemo, useState, type FC } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import RipAdministrationView from './RipAdministrationView';
import RipAttachmentsView from './RipAttachmentsView';
import RipChecklistGridEdit from './RipChecklistGridEdit';
import RipFieldDataView from './RipFieldDataView';
import RipFinalCommentsView from './RipFinalCommentsView';
import RipNotesView from './RipNotesView';
import RipSpecificImpactsView from './RipSpecificImpactsView';
import RipStreamOpeningView from './RipStreamOpeningView';

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

// Maps a section id (as emitted by the backend) to its edit-route segment, per protocol. Sections
// without an entry (e.g. biodiversity "plots", water "summary") have no editor yet and render no
// edit icon.
const SECTION_EDIT_ROUTES: Record<string, Record<string, string>> = {
  biodiversity: {
    opening: 'edit',
    stratum: 'strata',
  },
  riparian: {
    'stream': 'stream-opening',
    'field-data': 'field-data',
    'questions': 'questions',
    'specific-impacts': 'specific-impacts',
    'final-cmts': 'final-comments',
  },
  water: {
    'sample-area': 'sample-area',
    'site-control': 'sample-site',
    'assessment': 'assessment',
    'range': 'range',
  },
};

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

// Coded view fields whose stored value should display as a description. Keyed by the field label
// the backend emits. Fixed enumerations are resolved here; data-driven codes (stream class,
// checklist answers) come from fetched code lists, applied by label below.
const STATIC_CODE_LABELS: Record<string, Record<string, string>> = {
  'Channel morphology': { RC: 'Riffle/pool or Cascade/pool', SP: 'Step/pool', NA: 'Non-alluvial' },
  'Reach location u/s-d/s ind': { U: 'U/S', D: 'D/S' },
  'UTM at reference': { US: 'U/S', DS: 'D/S' },
  'Planned riparian N/A ind': { Y: 'Yes', N: 'No' },
  'UTM signal': { N: 'No signal available', Y: 'Signal available' },
};
const STREAM_CLASS_LABELS = new Set([
  'Planned riparian stream RMA class',
  'Actual riparian stream RMA class',
]);
const ANSWER_LABELS = new Set(['Invasive plant']);

// Tombstone fields the legacy screen shows in the page header band rather than in a section. The
// backend returns these inside the section reads; we promote them to the header (in legacy order)
// and hide them from the section field list to mirror the legacy layout.
const HEADER_EXTRA_LABELS = [
  'Org unit',
  'Client',
  'Client name',
  'Opening ID',
  'Licence',
  'Cutting permit',
  'Cut block',
  'Sample #',
] as const;
const HEADER_EXTRA_LABEL_SET = new Set<string>(HEADER_EXTRA_LABELS);

// Section ids rendered as grids (collections) rather than flat field lists.
const GRID_SECTION_IDS = new Set(['questions', 'specific-impacts']);
// A repeated-collection field, e.g. "Question 3 - Answer code" — these are rendered by the grid,
// so they are excluded from the flat scalar field list.
const ARRAY_ROW_LABEL =
  /^(Point indicator|Continuous indicator|Other indicator|Question|No answer|Specific impact|Other specific impact) \d+ - /;

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
  const [streamClassMap, setStreamClassMap] = useState<Record<string, string>>({});
  const [answerMap, setAnswerMap] = useState<Record<string, string>>({});

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

  // Code lists for resolving coded view values (stream class, checklist answers) to descriptions.
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      API.configuration.getStreamClasses().catch(() => []),
      API.configuration.getChecklistAnswers().catch(() => []),
    ]).then(([classes, answers]) => {
      if (cancelled) return;
      setStreamClassMap(Object.fromEntries(classes.map((o) => [o.code, o.description])));
      setAnswerMap(Object.fromEntries(answers.map((o) => [o.code, o.description])));
    });
    return () => {
      cancelled = true;
    };
  }, []);

  // Resolve a coded field value to its human-readable description (falls back to the raw value).
  const resolveDisplay = (label: string, value: string): string => {
    if (!value) return value;
    if (STREAM_CLASS_LABELS.has(label)) return streamClassMap[value] ?? value;
    if (ANSWER_LABELS.has(label)) return answerMap[value] ?? value;
    return STATIC_CODE_LABELS[label]?.[value] ?? value;
  };

  // Tombstone fields the backend returns inside section reads, lifted to the page header band.
  const headerExtras = useMemo(() => {
    const map: Record<string, string> = {};
    checklist?.sections.forEach((section) =>
      section.fields.forEach((field) => {
        if (HEADER_EXTRA_LABEL_SET.has(field.label) && !map[field.label] && field.value) {
          map[field.label] = field.value;
        }
      }),
    );
    return map;
  }, [checklist]);

  // Render a header cell; core fields are always shown, promoted extras only when they have a value.
  const headerCell = (label: string, value: string | undefined, always = false) =>
    always || value ? (
      <div key={label}>
        <span className="protocol-checklist__label">{label}</span>
        <span>{value ?? ''}</span>
      </div>
    ) : null;

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
                {/* Tombstone header laid out like the legacy screen. */}
                {headerCell('Master list year', checklist.effectiveYear, true)}
                {headerCell('Org unit', headerExtras['Org unit'])}
                {headerCell('Checklist', checklist.checklistId, true)}
                {headerCell('Client', headerExtras['Client'])}
                {headerCell('Client name', headerExtras['Client name'])}
                {headerCell('Opening', checklist.openingNumber, true)}
                {headerCell('Opening ID', headerExtras['Opening ID'])}
                {headerCell('Licence', headerExtras['Licence'])}
                {headerCell('Cutting permit', headerExtras['Cutting permit'])}
                {headerCell('Cut block', headerExtras['Cut block'])}
                <div>
                  <span className="protocol-checklist__label">Status</span>
                  <Tag type={checklist.statusCode === 'SUB' ? 'green' : 'blue'} size="sm">
                    {checklist.statusLabel}
                  </Tag>
                </div>
                {headerCell('Evaluator', checklist.evaluatorUserid, true)}
                {headerCell('Evaluation date', checklist.evaluationDate, true)}
                {headerCell('Sample #', headerExtras['Sample #'])}
              </div>
            </Tile>
          </Column>

          {canEdit && (
            <Column sm={4} md={8} lg={16}>
              <div className="protocol-checklist__actions">
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
              <TabList aria-label="Checklist sections" contained>
                {checklist.sections.map((section) => (
                  <Tab key={section.id}>{section.title}</Tab>
                ))}
              </TabList>
              <TabPanels>
                {checklist.sections.map((section) => {
                  const editSegment = protocolType
                    ? SECTION_EDIT_ROUTES[protocolType]?.[section.id]
                    : undefined;
                  // These sections edit inline (their own Edit/Save); the rest navigate to an
                  // edit page.
                  const inlineSection = [
                    'administration',
                    'stream',
                    'field-data',
                    'questions',
                    'specific-impacts',
                    'final-cmts',
                    'notes',
                    'attachments',
                  ].includes(section.id);
                  const showEdit = canEdit && !submitted && Boolean(editSegment) && !inlineSection;
                  return (
                    <TabPanel key={section.id}>
                      {showEdit && (
                        <div className="protocol-checklist__section-actions">
                          <Button
                            kind="tertiary"
                            size="lg"
                            onClick={() =>
                              navigate(`/protocol-checklists/${protocolType}/${id}/${editSegment}`)
                            }
                          >
                            <span className="protocol-checklist__edit-label">
                              <Edit /> Edit
                            </span>
                          </Button>
                        </div>
                      )}
                      {section.id === 'administration' ? (
                        <RipAdministrationView
                          checklistId={id}
                          canEdit={canEdit}
                          submitted={submitted}
                        />
                      ) : section.id === 'notes' ? (
                        <RipNotesView checklistId={id} canEdit={canEdit} submitted={submitted} />
                      ) : section.id === 'attachments' ? (
                        <RipAttachmentsView
                          checklistId={id}
                          canEdit={canEdit}
                          submitted={submitted}
                        />
                      ) : section.id === 'stream' ? (
                        <RipStreamOpeningView
                          checklistId={id}
                          canEdit={canEdit}
                          submitted={submitted}
                        />
                      ) : section.id === 'field-data' ? (
                        <RipFieldDataView
                          fields={section.fields}
                          checklistId={id}
                          canEdit={canEdit}
                          submitted={submitted}
                        />
                      ) : section.id === 'final-cmts' ? (
                        <RipFinalCommentsView
                          checklistId={id}
                          canEdit={canEdit}
                          submitted={submitted}
                        />
                      ) : (
                        <>
                          {(() => {
                            const isGrid = GRID_SECTION_IDS.has(section.id);
                            // Flat scalar fields: drop the promoted header fields, and (for grid
                            // sections) the collection rows the grid renders.
                            const scalars = section.fields.filter(
                              (field) =>
                                !HEADER_EXTRA_LABEL_SET.has(field.label) &&
                                !(isGrid && ARRAY_ROW_LABEL.test(field.label)),
                            );
                            return scalars.length > 0 ? (
                              <div className="protocol-checklist__fields">
                                {scalars.map((field) => {
                                  // Resolve coded scalar values to descriptions; leave YES_NO /
                                  // MULTILINE fields to their dedicated renderers.
                                  const display =
                                    field.kind === 'YES_NO' || field.kind === 'MULTILINE'
                                      ? field
                                      : {
                                          ...field,
                                          value: resolveDisplay(field.label, field.value),
                                        };
                                  return (
                                    <div className="protocol-checklist__field" key={field.label}>
                                      <span className="protocol-checklist__label">
                                        {field.label}
                                      </span>
                                      <span className="protocol-checklist__value">
                                        {renderFieldValue(display)}
                                      </span>
                                    </div>
                                  );
                                })}
                              </div>
                            ) : null;
                          })()}
                          {section.id === 'questions' ? (
                            <RipChecklistGridEdit
                              section={section}
                              checklistId={id}
                              canEdit={canEdit}
                              submitted={submitted}
                            />
                          ) : section.id === 'specific-impacts' ? (
                            <RipSpecificImpactsView
                              section={section}
                              checklistId={id}
                              canEdit={canEdit}
                              submitted={submitted}
                            />
                          ) : null}
                        </>
                      )}
                    </TabPanel>
                  );
                })}
              </TabPanels>
            </Tabs>
          </Column>
        </>
      )}
    </Grid>
  );
};

export default ProtocolChecklistPage;
