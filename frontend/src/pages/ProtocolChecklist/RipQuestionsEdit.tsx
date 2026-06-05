import RipGridEditor from './RipGridEditor';

import type { RiparianQuestions } from '@/types/protocolChecklist';
import type { FC } from 'react';

import API from '@/services/APIs';

const RipQuestionsEditPage: FC = () => (
  <RipGridEditor<RiparianQuestions>
    title="Riparian questions"
    load={(id) => API.protocolChecklist.getRipQuestions(id)}
    save={(id, data) => API.protocolChecklist.saveRipQuestions(id, data)}
    lists={[
      {
        key: 'questions',
        legend: 'Question answers',
        columns: [
          { key: 'checklistQuestionId', label: 'Question id' },
          { key: 'answerCode', label: 'Answer code' },
        ],
      },
      {
        key: 'noAnswers',
        legend: 'No-answer impacts',
        columns: [
          { key: 'checklistQuestionId', label: 'Question id' },
          { key: 'answerImpactType', label: 'Impact type' },
          { key: 'answerInd', label: 'Answer (Y/N)' },
        ],
      },
    ]}
  />
);

export default RipQuestionsEditPage;
