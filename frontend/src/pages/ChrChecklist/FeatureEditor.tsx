import { Checkbox, Tab, TabList, TabPanel, TabPanels, Tabs } from '@carbon/react';

import {
  CodeSelect,
  IndicatorCheckbox,
  TextAreaField,
  TextField,
} from '@/pages/ChrChecklist/fields';

import type { Feature, Indicator } from '@/types/chrChecklist';
import type { FC } from 'react';

import {
  FEATURE_CLASS_CODES,
  INFORMATION_SOURCE_CODES,
  RATING_CODES,
  RESERVE_TYPE_CODES,
} from '@/pages/ChrChecklist/codeLists';

type PatchFn = (patch: Partial<Feature>) => void;

/** A management-strategy row rendered as FN / AIA / SP checkboxes. */
const PLANNING_STRATEGIES: Array<{
  label: string;
  fn: string;
  aia: string;
  sp: string;
}> = [
  {
    label: 'Modify block boundary',
    fn: 'modifyBlockBoundaryFN',
    aia: 'modifyBlockBoundaryAIA',
    sp: 'modifyBlockBoundarySP',
  },
  { label: 'Retain buffer', fn: 'retainBufferFN', aia: 'retainBufferAIA', sp: 'retainBufferSP' },
  {
    label: 'Retain in harvest area (no buffer)',
    fn: 'retaininHarvestAreaFN',
    aia: 'retaininHarvestAreaAIA',
    sp: 'retaininHarvestAreaSP',
  },
  {
    label: 'Crown/stand modification',
    fn: 'crownorstandmodificationFN',
    aia: 'crownorstandmodificationAIA',
    sp: 'crownorstandmodificationSP',
  },
  {
    label: 'Conserve in rotational reserve',
    fn: 'conserveinRotationalReserveFN',
    aia: 'conserveinRotationalReserveAIA',
    sp: 'conserveinRotationalReserveSP',
  },
  {
    label: 'Permanent / temporary reserve',
    fn: 'permanentReserveFN',
    aia: 'permanentReserveAIA',
    sp: 'permanentReserveSP',
  },
  {
    label: 'Date the feature',
    fn: 'datetheFeatureFN',
    aia: 'datetheFeatureAIA',
    sp: 'datetheFeatureSP',
  },
  {
    label: 'Stub CMTs above scar',
    fn: 'stubCMTsabovescarFN',
    aia: 'stubCMTsabovescarAIA',
    sp: 'stubCMTsabovescarSP',
  },
  { label: 'Stub non-CMTs', fn: 'stubnonCMTsFN', aia: 'stubnonCMTsAIA', sp: 'stubnonCMTsSP' },
  {
    label: 'Leave standing',
    fn: 'leaveStandingFN',
    aia: 'leaveStandingAIA',
    sp: 'leaveStandingSP',
  },
  {
    label: 'Avoid silviculture — planting',
    fn: 'avoidSilvAvoidPlantingFN',
    aia: 'avoidSilvAvoidPlantingAIA',
    sp: 'avoidSilvAvoidPlantingSP',
  },
  {
    label: 'Avoid silviculture — site prep',
    fn: 'avoidSilvAvoidSitePrepFN',
    aia: 'avoidSilvAvoidSitePrepAIA',
    sp: 'avoidSilvAvoidSitePrepSP',
  },
  {
    label: 'Machine-free zone',
    fn: 'machineFreeZoneFN',
    aia: 'machineFreeZoneAIA',
    sp: 'machineFreeZoneSP',
  },
  {
    label: 'Harvest under SAP',
    fn: 'harvestUnderSapFN',
    aia: 'harvestUnderSapAIA',
    sp: 'harvestUnderSapSP',
  },
  {
    label: 'Winter harvest / frozen ground',
    fn: 'winterHarvestFrozenGroundFN',
    aia: 'winterHarvestFrozenGroundAIA',
    sp: 'winterHarvestFrozenGroundSP',
  },
];

const FEATURE_TYPES: Array<{ label: string; field: string }> = [
  { label: 'Cultural trail — designated', field: 'culturalTraildesignated' },
  { label: 'Cultural trail — undesignated', field: 'culturalTrailundesignated' },
  { label: 'Burial site', field: 'burialSite' },
  { label: 'Nest', field: 'nest' },
  { label: 'Ceremonial site', field: 'ceremonialSite' },
  { label: 'Cremation site', field: 'cremationSite' },
  { label: 'Cave or other karst', field: 'caveorotherKarst' },
  { label: 'Den', field: 'den' },
  { label: 'Traditional use site', field: 'traditionalUseSite' },
  { label: 'Cedar bark strip area', field: 'cedarBarkStripArea' },
  { label: 'Rock outcrop', field: 'rockOutcrop' },
  { label: 'Spiritual site', field: 'spiritualSite' },
  { label: 'Cultural depression', field: 'culturalDepression' },
  { label: 'Lithics', field: 'lithics' },
];

const DAMAGE_AGENTS: Array<{ label: string; field: string }> = [
  { label: 'Harvesting', field: 'harvestingQ2Wheredamagehasoccurredwhatisthemostlikelycause' },
  { label: 'Safety', field: 'safetyQ2Wheredamagehasoccurredwhatisthemostlikelycause' },
  { label: 'Silviculture', field: 'silvicultureQ2Wheredamagehasoccurredwhatisthemostlikelycause' },
  { label: 'Recreation', field: 'recreationQ2Wheredamagehasoccurredwhatisthemostlikelycause' },
  { label: 'Fire', field: 'fireQ2Wheredamagehasoccurredwhatisthemostlikelycause' },
  {
    label: 'Industrial use',
    field: 'industrialUseQ2Wheredamagehasoccurredwhatisthemostlikelycause',
  },
  { label: 'Road', field: 'roadQ2Wheredamagehasoccurredwhatisthemostlikelycause' },
  { label: 'Livestock', field: 'livestockQ2Wheredamagehasoccurredwhatisthemostlikelycause' },
  { label: 'Windthrow', field: 'windthrowQ2Wheredamagehasoccurredwhatisthemostlikelycause' },
];

const WINDTHROW_TECHNIQUES: Array<{ label: string; field: string }> = [
  { label: 'None', field: 'windthrowTechniqueNone' },
  { label: 'Retention buffer', field: 'windthrowTechniqueRetentionBuffer' },
  { label: 'Pruning', field: 'windthrowTechniquePruning' },
  { label: 'Feathering', field: 'windthrowTechniqueFeathering' },
  { label: 'Topping', field: 'windthrowTechniqueTopping' },
];

const FeatureEditor: FC<{
  feature: Feature;
  onPatch: PatchFn;
  readOnly: boolean;
  siblingLabels?: string[];
  onToggleAssociated?: (siblingLabel: string) => void;
}> = ({ feature, onPatch, readOnly, siblingLabels = [], onToggleAssociated }) => {
  const ind = (field: string): Indicator | undefined => feature[field] as Indicator | undefined;
  const str = (field: string): string | undefined => feature[field] as string | undefined;
  const chk = (field: string, label: string) => (
    <IndicatorCheckbox
      id={`feat-${field}`}
      labelText={label}
      value={ind(field)}
      disabled={readOnly}
      onToggle={(v) => onPatch({ [field]: v })}
    />
  );

  return (
    <Tabs>
      <TabList aria-label="Feature sections" contained>
        <Tab>Description</Tab>
        <Tab>Location</Tab>
        <Tab>Age</Tab>
        <Tab>Planning</Tab>
        <Tab>Effectiveness</Tab>
        <Tab>Damage</Tab>
        <Tab>Windthrow</Tab>
        <Tab>Summary</Tab>
      </TabList>
      <TabPanels>
        {/* Description */}
        <TabPanel>
          <div className="chr-checklist__form">
            <TextField
              id="feat-label"
              labelText="Feature label"
              value={str('featureLabel')}
              disabled={readOnly}
              onChange={(v) => onPatch({ featureLabel: v })}
            />
            <CodeSelect
              id="feat-class"
              labelText="Feature class"
              value={str('featureDescriptionCode')}
              options={FEATURE_CLASS_CODES}
              includeBlank
              disabled={readOnly}
              onChange={(v) => onPatch({ featureDescriptionCode: v })}
            />
            <CodeSelect
              id="feat-source"
              labelText="Information source"
              value={str('featureInfoSourceCode')}
              options={INFORMATION_SOURCE_CODES}
              includeBlank
              disabled={readOnly}
              onChange={(v) => onPatch({ featureInfoSourceCode: v })}
            />
            {chk('compositeFeatureInd', 'Composite feature')}
            <TextField
              id="feat-composite"
              labelText="Composite of (feature label)"
              value={str('compositeFeature')}
              disabled={readOnly}
              onChange={(v) => onPatch({ compositeFeature: v })}
            />
            {siblingLabels.length > 0 && (
              <fieldset className="chr-checklist__fieldset">
                <legend>Associated features</legend>
                {siblingLabels.map((label) => (
                  <Checkbox
                    key={`assoc-${label}`}
                    id={`feat-assoc-${label}`}
                    labelText={`Feature ${label}`}
                    checked={(feature.associatedFeatures ?? []).includes(label)}
                    disabled={readOnly}
                    onChange={() => onToggleAssociated?.(label)}
                  />
                ))}
              </fieldset>
            )}
            {chk('chrRegisteredSite', 'Registered archaeological site')}
            <TextField
              id="feat-borden"
              labelText="Borden number"
              value={str('borden')}
              disabled={readOnly}
              onChange={(v) => onPatch({ borden: v })}
            />
            <TextAreaField
              id="feat-desc"
              labelText="Feature description"
              value={str('featureDescription')}
              disabled={readOnly}
              onChange={(v) => onPatch({ featureDescription: v })}
            />
            <TextField
              id="feat-width"
              labelText="Width (m)"
              value={str('widthofFeature')}
              disabled={readOnly}
              onChange={(v) => onPatch({ widthofFeature: v })}
            />
            <TextField
              id="feat-length"
              labelText="Length (m)"
              value={str('lengthofFeature')}
              disabled={readOnly}
              onChange={(v) => onPatch({ lengthofFeature: v })}
            />
            <TextField
              id="feat-area"
              labelText="Area (ha)"
              value={str('areaofFeature')}
              disabled={readOnly}
              onChange={(v) => onPatch({ areaofFeature: v })}
            />
            <fieldset className="chr-checklist__fieldset">
              <legend>Type of feature(s)</legend>
              {FEATURE_TYPES.map((t) => chk(t.field, t.label))}
              {chk('ofCMTs', 'CMTs')}
              <TextField
                id="feat-cmt-num"
                labelText="Number of CMTs"
                value={str('ofCMTsNumber')}
                disabled={readOnly}
                onChange={(v) => onPatch({ ofCMTsNumber: v })}
              />
              {chk('ofMonumentalCedars', 'Monumental cedars')}
              <TextField
                id="feat-mon-num"
                labelText="Stand of monumental cedar"
                value={str('standofMonumentalCedar')}
                disabled={readOnly}
                onChange={(v) => onPatch({ standofMonumentalCedar: v })}
              />
              {chk('other', 'Other')}
              <TextField
                id="feat-other-desc"
                labelText="Other description"
                value={str('otherDescription')}
                disabled={readOnly}
                onChange={(v) => onPatch({ otherDescription: v })}
              />
            </fieldset>
          </div>
        </TabPanel>

        {/* Location */}
        <TabPanel>
          <div className="chr-checklist__form">
            {chk('inharvestedarea', 'In harvested area')}
            {chk('adjacenttoblock', 'Adjacent to block')}
            {chk('adjacenttowater', 'Adjacent to water')}
            {chk('entirecutblock', 'Entire cut block')}
            {chk('locationOther', 'Other location')}
            <TextField
              id="feat-loc-other"
              labelText="Other location description"
              value={str('locationOtherDescription')}
              disabled={readOnly}
              onChange={(v) => onPatch({ locationOtherDescription: v })}
            />
            {chk('inReserve', 'In reserve')}
            <CodeSelect
              id="feat-reserve"
              labelText="Reserve type"
              value={str('locationReservetype')}
              options={RESERVE_TYPE_CODES}
              disabled={readOnly}
              onChange={(v) => onPatch({ locationReservetype: v })}
            />
          </div>
        </TabPanel>

        {/* Age */}
        <TabPanel>
          <div className="chr-checklist__form">
            {chk('pre1846', 'Pre-1846')}
            {chk('post1846', 'Post-1846')}
            {chk('ageUnknown', 'Age unknown')}
            {chk('historicalUse', 'Historical use')}
          </div>
        </TabPanel>

        {/* Planning */}
        <TabPanel>
          <div className="chr-checklist__form">
            {chk('managementStrategyFN', 'FN management recommendations provided')}
            {chk('managementStrategySP', 'Site plan strategies noted')}
            {chk('sitePermitIssued', 'AIA / site-alteration permit issued')}
            <TextField
              id="feat-permit"
              labelText="Permit number"
              value={str('permit')}
              disabled={readOnly}
              onChange={(v) => onPatch({ permit: v })}
            />
          </div>
          <table className="chr-checklist__planning">
            <thead>
              <tr>
                <th>Strategy</th>
                <th>FN</th>
                <th>AIA/SAP</th>
                <th>Site plan</th>
              </tr>
            </thead>
            <tbody>
              {PLANNING_STRATEGIES.map((s) => (
                <tr key={s.label}>
                  <td>{s.label}</td>
                  <td>{chk(s.fn, '')}</td>
                  <td>{chk(s.aia, '')}</td>
                  <td>{chk(s.sp, '')}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="chr-checklist__form">
            <TextField
              id="feat-buffer-fn"
              labelText="Buffer length — FN (m)"
              value={str('bufferLengthFN')}
              disabled={readOnly}
              onChange={(v) => onPatch({ bufferLengthFN: v })}
            />
            <TextField
              id="feat-buffer-aia"
              labelText="Buffer length — AIA (m)"
              value={str('bufferLengthAIA')}
              disabled={readOnly}
              onChange={(v) => onPatch({ bufferLengthAIA: v })}
            />
            <TextField
              id="feat-buffer-sp"
              labelText="Buffer length — SP (m)"
              value={str('bufferLengthSP')}
              disabled={readOnly}
              onChange={(v) => onPatch({ bufferLengthSP: v })}
            />
            <CodeSelect
              id="feat-rotres-fn"
              labelText="Rotational reserve type — FN"
              value={str('conserveRotationalReserveTypeFN')}
              options={RESERVE_TYPE_CODES}
              disabled={readOnly}
              onChange={(v) => onPatch({ conserveRotationalReserveTypeFN: v })}
            />
            <CodeSelect
              id="feat-rotres-aia"
              labelText="Rotational reserve type — AIA"
              value={str('conserveRotationalReserveTypeAIA')}
              options={RESERVE_TYPE_CODES}
              disabled={readOnly}
              onChange={(v) => onPatch({ conserveRotationalReserveTypeAIA: v })}
            />
            <CodeSelect
              id="feat-rotres-sp"
              labelText="Rotational reserve type — SP"
              value={str('conserveRotationalReserveTypeSP')}
              options={RESERVE_TYPE_CODES}
              disabled={readOnly}
              onChange={(v) => onPatch({ conserveRotationalReserveTypeSP: v })}
            />
            <CodeSelect
              id="feat-tempres-fn"
              labelText="Reserve type — FN"
              value={str('temporaryRetentionTypeFN')}
              options={RESERVE_TYPE_CODES}
              disabled={readOnly}
              onChange={(v) => onPatch({ temporaryRetentionTypeFN: v })}
            />
            <CodeSelect
              id="feat-tempres-aia"
              labelText="Reserve type — AIA"
              value={str('temporaryRetentionTypeAIA')}
              options={RESERVE_TYPE_CODES}
              disabled={readOnly}
              onChange={(v) => onPatch({ temporaryRetentionTypeAIA: v })}
            />
            <CodeSelect
              id="feat-tempres-sp"
              labelText="Reserve type — SP"
              value={str('temporaryRetentionTypeSP')}
              options={RESERVE_TYPE_CODES}
              disabled={readOnly}
              onChange={(v) => onPatch({ temporaryRetentionTypeSP: v })}
            />
          </div>
        </TabPanel>

        {/* Effectiveness */}
        <TabPanel>
          <div className="chr-checklist__form">
            {chk('forCompositeFeaturesInd', 'Same strategy for all features (composite)')}
            {chk('unabletoLocate', 'Unable to locate feature')}
            {chk('noManagement', 'No management applied')}
            {chk('partiallytemporaryreserve', 'Partially conserved in temporary reserve')}
            <CodeSelect
              id="feat-eff-parttemp"
              labelText="Partial temporary reserve type"
              value={str('partiallytemporaryreservetype')}
              options={RESERVE_TYPE_CODES}
              disabled={readOnly}
              onChange={(v) => onPatch({ partiallytemporaryreservetype: v })}
            />
            {chk('fullyconservedintemporaryreserve', 'Fully conserved in temporary reserve')}
            <CodeSelect
              id="feat-eff-fulltemp"
              labelText="Full temporary reserve type"
              value={str('fullytemporaryreserve')}
              options={RESERVE_TYPE_CODES}
              disabled={readOnly}
              onChange={(v) => onPatch({ fullytemporaryreserve: v })}
            />
            {chk(
              'partiallyconservedinpermanentreserve',
              'Partially conserved in permanent reserve',
            )}
            <CodeSelect
              id="feat-eff-partperm"
              labelText="Partial permanent reserve type"
              value={str('partiallyconservedinpermanentreserveType')}
              options={RESERVE_TYPE_CODES}
              disabled={readOnly}
              onChange={(v) => onPatch({ partiallyconservedinpermanentreserveType: v })}
            />
            {chk('fullyconservedinpermanentreserve', 'Fully conserved in permanent reserve')}
            <CodeSelect
              id="feat-eff-fullperm"
              labelText="Full permanent reserve type"
              value={str('fullyconservedinpermanentreserveType')}
              options={RESERVE_TYPE_CODES}
              disabled={readOnly}
              onChange={(v) => onPatch({ fullyconservedinpermanentreserveType: v })}
            />
            {chk('modifiedblockboundary', 'Modified block boundary')}
            {chk('retainabuffer', 'Retained a buffer')}
            <TextField
              id="feat-eff-buffer"
              labelText="Buffer width (m)"
              value={str('bufferWidthMeter')}
              disabled={readOnly}
              onChange={(v) => onPatch({ bufferWidthMeter: v })}
            />
            {chk('compledCrownorstandmodification', 'Completed crown/stand modification')}
            {chk('datedthefeature', 'Dated the feature')}
            {chk('retainedinharvestareanobuffer', 'Retained in harvest area (no buffer)')}
            {chk('leftStanding', 'Left standing')}
            {chk('stubbed', 'Stubbed (CMT)')}
            {chk('stubbedNon', 'Stubbed (non-CMT)')}
            {chk('avoidSilvAvoidPlanting', 'Avoided planting')}
            {chk('avoidSilvAvoidSitePrep', 'Avoided site prep')}
            {chk('machineFreeZone', 'Machine-free zone')}
            {chk('harvestUnderSap', 'Harvest under SAP')}
            {chk('winterHarvestFrozenGround', 'Winter harvest / frozen ground')}
            <TextField
              id="feat-eff-other"
              labelText="Other activities"
              value={str('otherActivities')}
              disabled={readOnly}
              onChange={(v) => onPatch({ otherActivities: v })}
            />
          </div>
        </TabPanel>

        {/* Damage */}
        <TabPanel>
          <div className="chr-checklist__form">
            {chk(
              'q1Isthereevidenceofdamagetothesiteorfeature',
              'Q1 — Evidence of damage to the site/feature',
            )}
            <fieldset className="chr-checklist__fieldset">
              <legend>Q2 — Most likely cause of damage</legend>
              {DAMAGE_AGENTS.map((d) => chk(d.field, d.label))}
              {chk('otherQ2Wheredamagehasoccurredwhatisthemostlikelycause', 'Other')}
              <TextField
                id="feat-damage-other"
                labelText="Other cause description"
                value={str(
                  'ifotherpleasedescribeOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause',
                )}
                disabled={readOnly}
                onChange={(v) =>
                  onPatch({
                    ifotherpleasedescribeOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause: v,
                  })
                }
              />
            </fieldset>
            <TextField
              id="feat-q3"
              labelText="Q3 — Irreversibly damaged? (Y/N)"
              value={str('q3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse')}
              disabled={readOnly}
              onChange={(v) =>
                onPatch({
                  q3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse: v,
                })
              }
            />
            <TextAreaField
              id="feat-damage-desc"
              labelText="Description of damage"
              value={str('descriptionofdamage')}
              disabled={readOnly}
              onChange={(v) => onPatch({ descriptionofdamage: v })}
            />
          </div>
        </TabPanel>

        {/* Windthrow */}
        <TabPanel>
          <div className="chr-checklist__form">
            {chk('windthrowManagement', 'Windthrow management applicable')}
            {chk('windthrow', 'Area windfirm')}
            <TextField
              id="feat-est-windthrow"
              labelText="Estimated windthrow (%)"
              value={str('estwindthrow')}
              disabled={readOnly}
              onChange={(v) => onPatch({ estwindthrow: v })}
            />
            <fieldset className="chr-checklist__fieldset">
              <legend>Windthrow treatment</legend>
              {WINDTHROW_TECHNIQUES.map((w) => chk(w.field, w.label))}
              {chk('otherTechnique', 'Other technique')}
              <TextField
                id="feat-windthrow-other"
                labelText="Other technique description"
                value={str('ifotherpleasedescribe')}
                disabled={readOnly}
                onChange={(v) => onPatch({ ifotherpleasedescribe: v })}
              />
            </fieldset>
            <fieldset className="chr-checklist__fieldset">
              <legend>Trail features</legend>
              {chk('trailfeatures', 'Trail features applicable')}
              {chk('canthetrailstillbelocated', 'Trail still locatable')}
              {chk('hasthetrailbeenmadelesspassble', 'Trail made less passable')}
              {chk('isthereevidenceofdamage', 'Evidence of damage to trail area')}
              <TextField
                id="feat-trail-len"
                labelText="Estimated trail damage (%)"
                value={str('trailLength')}
                disabled={readOnly}
                onChange={(v) => onPatch({ trailLength: v })}
              />
            </fieldset>
          </div>
        </TabPanel>

        {/* Summary */}
        <TabPanel>
          <div className="chr-checklist__form">
            {chk(
              'q4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature',
              'Q4 — Operational factors limited CHR management options?',
            )}
            <TextAreaField
              id="feat-q4-desc"
              labelText="Q4 description"
              value={str('q4Description')}
              disabled={readOnly}
              onChange={(v) => onPatch({ q4Description: v })}
            />
            {chk(
              'q5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective',
              'Q5 — Strategies/practices particularly effective?',
            )}
            <TextAreaField
              id="feat-q5-desc"
              labelText="Q5 description"
              value={str('q5Description')}
              disabled={readOnly}
              onChange={(v) => onPatch({ q5Description: v })}
            />
            {chk(
              'q6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature',
              'Q6 — Strategies that could have reduced impact?',
            )}
            <TextAreaField
              id="feat-q6-desc"
              labelText="Q6 description"
              value={str('q6Description')}
              disabled={readOnly}
              onChange={(v) => onPatch({ q6Description: v })}
            />
            <CodeSelect
              id="feat-rating"
              labelText="Feature rating"
              value={str('featureRating')}
              options={RATING_CODES}
              includeBlank
              disabled={readOnly}
              onChange={(v) => onPatch({ featureRating: v })}
            />
            <TextAreaField
              id="feat-rating-rationale"
              labelText="Feature rating rationale"
              value={str('featureRatingRationale')}
              disabled={readOnly}
              onChange={(v) => onPatch({ featureRatingRationale: v })}
            />
            <TextAreaField
              id="feat-comment"
              labelText="Feature comments"
              value={str('featureComment')}
              disabled={readOnly}
              onChange={(v) => onPatch({ featureComment: v })}
            />
          </div>
        </TabPanel>
      </TabPanels>
    </Tabs>
  );
};

export default FeatureEditor;
