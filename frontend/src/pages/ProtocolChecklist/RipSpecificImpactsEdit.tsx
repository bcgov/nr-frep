import RipGridEditor from './RipGridEditor';

import type { RiparianSpecificImpacts } from '@/types/protocolChecklist';
import type { FC } from 'react';

import API from '@/services/APIs';

const RipSpecificImpactsEditPage: FC = () => (
  <RipGridEditor<RiparianSpecificImpacts>
    title="Riparian specific impacts"
    load={(id) => API.protocolChecklist.getRipSpecificImpacts(id)}
    save={(id, data) => API.protocolChecklist.saveRipSpecificImpacts(id, data)}
    lists={[
      {
        key: 'openImpacts',
        legend: 'Opening specific impacts',
        columns: [
          { key: 'openingSpecificImpactType', label: 'Impact type' },
          { key: 'specImpactInd', label: 'Present (Y/N)' },
        ],
      },
      {
        key: 'otherImpacts',
        legend: 'Other specific impacts',
        columns: [
          { key: 'description', label: 'Description' },
          { key: 'specImpactInd', label: 'Present (Y/N)' },
        ],
      },
    ]}
  />
);

export default RipSpecificImpactsEditPage;
