import { Column, Grid, Tile } from '@carbon/react';

import { IndicatorCheckbox, TextAreaField, TextField } from '@/pages/ChrChecklist/fields';

import type { CheckList } from '@/types/chrChecklist';
import type { FC } from 'react';

const ReadOnlyField: FC<{ label: string; value?: string }> = ({ label, value }) => (
  <p className="chr-checklist__ro-field">
    <span className="chr-checklist__ro-label">{label}</span>
    <span>{value || '—'}</span>
  </p>
);

/** Section 1 — opening information: read-only site context + editable header fields. */
const OpeningInformation: FC<{
  value: CheckList;
  onPatch: (patch: Partial<CheckList>) => void;
  readOnly: boolean;
}> = ({ value, onPatch, readOnly }) => (
  <Grid fullWidth className="chr-checklist__section">
    <Column sm={4} md={8} lg={8}>
      <Tile>
        <h3>Site</h3>
        <ReadOnlyField label="District" value={value.district ?? value.orgUnitName} />
        <ReadOnlyField label="Opening ID" value={value.openingID} />
        <ReadOnlyField label="Licensee" value={value.licensee} />
        <ReadOnlyField label="Cutting permit" value={value.cuttingPermit} />
        <ReadOnlyField label="Block" value={value.block} />
        <ReadOnlyField label="Client" value={value.client} />
        <ReadOnlyField label="Year of harvest" value={value.yearOfHarvest} />
      </Tile>
    </Column>
    <Column sm={4} md={8} lg={8}>
      <Tile>
        <h3>Evaluation</h3>
        <div className="chr-checklist__form">
          <TextField
            id="chr-evaluation-date"
            labelText="Evaluation date"
            placeholder="YYYY-MM-DD"
            value={value.evaluationDate}
            disabled={readOnly}
            onChange={(v) => onPatch({ evaluationDate: v })}
          />
          <TextField
            id="chr-first-nation"
            labelText="First Nation place name"
            value={value.firstNationName}
            disabled={readOnly}
            onChange={(v) => onPatch({ firstNationName: v })}
          />
          <TextAreaField
            id="chr-general-location"
            labelText="General location"
            value={value.generalLocation}
            disabled={readOnly}
            onChange={(v) => onPatch({ generalLocation: v })}
          />
          <IndicatorCheckbox
            id="chr-targeted"
            labelText="Targeted site"
            value={value.targeted}
            disabled={readOnly}
            onToggle={(v) => onPatch({ targeted: v })}
          />
        </div>
      </Tile>
    </Column>
  </Grid>
);

export default OpeningInformation;
