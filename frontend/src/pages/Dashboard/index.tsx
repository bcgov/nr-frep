import { Column, Grid, SkeletonText } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';

import { buildApiUrl } from '@/config/api/baseUrl';
import { useNotification } from '@/context/notification/useNotification';
import './dashboard.scss';

const DashboardPage: FC = () => {
  const { display } = useNotification();
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [hasError, setHasError] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setHasError(false);

    fetch(buildApiUrl('/hello'), {
      signal: controller.signal,
      credentials: 'include',
    })
      .then((response) => {
        if (!response.ok) {
          throw new Error(`Unable to load greeting (${response.status})`);
        }
        return response.text();
      })
      .then((text) => {
        if (controller.signal.aborted) return;
        setMessage(text);
        setLoading(false);
      })
      .catch((err: Error) => {
        if (err.name === 'AbortError' || controller.signal.aborted) return;
        display({
          kind: 'error',
          title: "We couldn't load the greeting",
          subtitle: err.message,
          timeout: 9000,
        });
        setHasError(true);
        setLoading(false);
      });

    return () => controller.abort();
  }, [display]);

  return (
    <Grid fullWidth className="default-grid dashboard-grid">
      <Column sm={4} md={8} lg={16}>
        <div className="dashboard__header">
          <h1>Welcome to FREP</h1>
        </div>
        {loading && (
          <div aria-busy>
            <SkeletonText width="40%" />
          </div>
        )}
        {!loading && hasError && <p>We couldn&apos;t load the greeting from the backend.</p>}
        {!loading && !hasError && message && (
          <p data-testid="hello-world-message">{message}</p>
        )}
      </Column>
    </Grid>
  );
};

export default DashboardPage;
