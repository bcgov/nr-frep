import RipGridEditor from './RipGridEditor';

import type { RiparianOtherIndicators } from '@/types/protocolChecklist';
import type { FC } from 'react';

import API from '@/services/APIs';

const RipOtherIndicatorsEditPage: FC = () => (
  <RipGridEditor<RiparianOtherIndicators>
    title="Riparian other indicators"
    load={(id) => API.protocolChecklist.getRipOtherIndicators(id)}
    save={(id, data) => API.protocolChecklist.saveRipOtherIndicators(id, data)}
    lists={[
      {
        key: 'indicators',
        legend: 'Other indicators',
        columns: [
          { key: 'otherIndTypeId', label: 'Type id' },
          { key: 'otherAnswerInd', label: 'Answer (Y/N)' },
        ],
      },
    ]}
  />
);

export default RipOtherIndicatorsEditPage;
