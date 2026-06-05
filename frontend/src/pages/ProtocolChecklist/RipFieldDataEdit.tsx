import RipGridEditor from './RipGridEditor';

import type { RiparianFieldData } from '@/types/protocolChecklist';
import type { FC } from 'react';

import API from '@/services/APIs';

const RipFieldDataEditPage: FC = () => (
  <RipGridEditor<RiparianFieldData>
    title="Riparian field data"
    load={(id) => API.protocolChecklist.getRipFieldData(id)}
    save={(id, data) => API.protocolChecklist.saveRipFieldData(id, data)}
    scalarFields={[{ key: 'fieldDataStreamReachDry', label: 'Field data stream reach dry' }]}
    lists={[
      {
        key: 'points',
        legend: 'Point indicators',
        columns: [
          { key: 'pointIndType', label: 'Type' },
          { key: 'measure1', label: 'Measure 1' },
          { key: 'measure2', label: 'Measure 2' },
          { key: 'measure3', label: 'Measure 3' },
          { key: 'measure4', label: 'Measure 4' },
          { key: 'measure5', label: 'Measure 5' },
          { key: 'measure6', label: 'Measure 6' },
          { key: 'mean', label: 'Mean' },
        ],
      },
      {
        key: 'continuous',
        legend: 'Continuous indicators',
        columns: [
          { key: 'continuousIndType', label: 'Type' },
          { key: 'total', label: 'Total' },
          { key: 'comments', label: 'Comments' },
        ],
      },
    ]}
  />
);

export default RipFieldDataEditPage;
