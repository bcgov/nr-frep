import { ArrowLeft } from '@carbon/icons-react';
import {
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
import API from '@/services/APIs';
import { PROTOCOL_TYPE_LABEL, PROTOCOL_TYPE_TO_BACKEND } from '@/types/protocolChecklist';

import './protocolChecklist.scss';

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

  const [checklist, setChecklist] = useState<ProtocolChecklist | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [hasError, setHasError] = useState(false);

  const protocolType: ProtocolType | null = isProtocolType(type) ? type : null;

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
  }, [display, id, protocolType]);

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
          <h1>
            {protocolType ? PROTOCOL_TYPE_LABEL[protocolType] : 'Protocol checklist'}
          </h1>
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
