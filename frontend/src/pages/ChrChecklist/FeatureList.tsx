import { Add, TrashCan } from '@carbon/icons-react';
import { Button, Column, Grid, Tile } from '@carbon/react';
import { useState, type FC } from 'react';

import FeatureEditor from '@/pages/ChrChecklist/FeatureEditor';

import type { Feature } from '@/types/chrChecklist';

/** Section 3 — feature list + the per-feature multi-tab editor. */
const FeatureList: FC<{
  features: Feature[];
  onChange: (features: Feature[]) => void;
  readOnly: boolean;
}> = ({ features, onChange, readOnly }) => {
  const [selected, setSelected] = useState(0);

  const nextLabel = (): string => {
    const numbers = features.map((f) => Number(f.featureLabel)).filter((n) => Number.isFinite(n));
    const max = numbers.length > 0 ? Math.max(...numbers) : 0;
    return String(max + 1);
  };

  const add = () => {
    const feature: Feature = { featureLabel: nextLabel(), compositeFeatureInd: 'false' };
    onChange([...features, feature]);
    setSelected(features.length);
  };

  const removeAt = (index: number) => {
    onChange(features.filter((_, i) => i !== index));
    setSelected((prev) => (prev >= index && prev > 0 ? prev - 1 : prev));
  };

  const patchSelected = (patch: Partial<Feature>) =>
    onChange(features.map((f, i) => (i === selected ? { ...f, ...patch } : f)));

  const current = features[selected];

  return (
    <Grid fullWidth className="chr-checklist__section">
      <Column sm={4} md={2} lg={4}>
        <div className="chr-checklist__feature-list">
          {features.length === 0 && <p>No features yet.</p>}
          {features.map((feature, index) => (
            <Button
              key={feature.id ?? `feature-${index}`}
              kind={index === selected ? 'primary' : 'ghost'}
              size="sm"
              className="chr-checklist__feature-item"
              onClick={() => setSelected(index)}
            >
              {feature.featureLabel || `Feature ${index + 1}`}
            </Button>
          ))}
          {!readOnly && (
            <Button kind="tertiary" size="sm" renderIcon={Add} onClick={add}>
              Add feature
            </Button>
          )}
        </div>
      </Column>
      <Column sm={4} md={6} lg={12}>
        {current ? (
          <Tile className="chr-checklist__feature-editor">
            {!readOnly && (
              <Button
                kind="danger--tertiary"
                size="sm"
                renderIcon={TrashCan}
                className="chr-checklist__feature-delete"
                onClick={() => removeAt(selected)}
              >
                Delete feature
              </Button>
            )}
            <FeatureEditor feature={current} onPatch={patchSelected} readOnly={readOnly} />
          </Tile>
        ) : (
          <p>Select or add a feature to edit its details.</p>
        )}
      </Column>
    </Grid>
  );
};

export default FeatureList;
