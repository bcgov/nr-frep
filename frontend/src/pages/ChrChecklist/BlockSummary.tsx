import { Column, Grid, Tag, Tile } from '@carbon/react';

import { CodeSelect, IndicatorCheckbox, TextAreaField } from '@/pages/ChrChecklist/fields';

import type { CheckList } from '@/types/chrChecklist';
import type { FC } from 'react';

import { RATING_CODES, calculateMrvaRatingCode } from '@/pages/ChrChecklist/codeLists';

/** Section — block summary: Q8/Q9/Q10, block rating + rationale, computed MRVA, comments. */
const BlockSummary: FC<{
  value: CheckList;
  onPatch: (patch: Partial<CheckList>) => void;
  readOnly: boolean;
}> = ({ value, onPatch, readOnly }) => {
  const mrva = calculateMrvaRatingCode(value.rating, value.features);
  return (
    <Grid fullWidth className="chr-checklist__section">
      <Column sm={4} md={8} lg={16}>
        <Tile>
          <h3>Block summary</h3>
          <div className="chr-checklist__form">
            <IndicatorCheckbox
              id="chr-q8"
              labelText="Q8 — Were there operational factors that limited CHR management options on this block?"
              value={value.q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock}
              disabled={readOnly}
              onToggle={(v) =>
                onPatch({
                  q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock: v,
                })
              }
            />
            <TextAreaField
              id="chr-q8-comments"
              labelText="Q8 comments"
              value={value.q8Comments}
              disabled={readOnly}
              onChange={(v) => onPatch({ q8Comments: v })}
            />
            <IndicatorCheckbox
              id="chr-q9"
              labelText="Q9 — Were management strategies/practices used on this block particularly effective?"
              value={
                value.q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues
              }
              disabled={readOnly}
              onToggle={(v) =>
                onPatch({
                  q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues:
                    v,
                })
              }
            />
            <TextAreaField
              id="chr-q9-comments"
              labelText="Q9 comments"
              value={value.q9Comments}
              disabled={readOnly}
              onChange={(v) => onPatch({ q9Comments: v })}
            />
            <IndicatorCheckbox
              id="chr-q10"
              labelText="Q10 — Were there strategies/practices that could have reduced impacts on CHR values on this block?"
              value={
                value.q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock
              }
              disabled={readOnly}
              onToggle={(v) =>
                onPatch({
                  q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock:
                    v,
                })
              }
            />
            <TextAreaField
              id="chr-q10-comments"
              labelText="Q10 comments"
              value={value.q10Comments}
              disabled={readOnly}
              onChange={(v) => onPatch({ q10Comments: v })}
            />
            <CodeSelect
              id="chr-block-rating"
              labelText="Block rating"
              value={value.rating}
              options={RATING_CODES}
              includeBlank
              disabled={readOnly}
              onChange={(v) => onPatch({ rating: v })}
            />
            <TextAreaField
              id="chr-rating-rationale"
              labelText="Rating rationale"
              value={value.ratingRationale}
              disabled={readOnly}
              onChange={(v) => onPatch({ ratingRationale: v })}
            />
            <p className="chr-checklist__ro-field">
              <span className="chr-checklist__ro-label">MRVA rating (computed)</span>
              <Tag type="blue" size="sm">
                {mrva || '—'}
              </Tag>
            </p>
            <TextAreaField
              id="chr-block-comments"
              labelText="Additional comments"
              value={value.commentaires}
              disabled={readOnly}
              onChange={(v) => onPatch({ commentaires: v })}
            />
          </div>
        </Tile>
      </Column>
    </Grid>
  );
};

export default BlockSummary;
