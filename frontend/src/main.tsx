import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import '@/styles/index.scss';
import App from '@/App.tsx';
import { AuthProvider } from '@/context/auth/AuthProvider';
import { ConfirmProvider } from '@/context/confirm/ConfirmProvider';
import NotificationProvider from '@/context/notification/NotificationProvider';
import PageTitleProvider from '@/context/pageTitle/PageTitleProvider';
import { PreferenceProvider } from '@/context/preference/PreferenceProvider.tsx';
import ThemeProvider from '@/context/theme/ThemeProvider.tsx';

import { queryClientConfig } from '@/config/react-query/config';

const queryClient = new QueryClient(queryClientConfig);

// No auth bootstrapping here on purpose. Amplify needed its token storage installed *before*
// configure(), so that whole block had to run ahead of the app. `oidc-client-ts` has no such
// ordering constraint: the UserManager is built lazily on first use (services/keycloak.ts), from a
// single issuer URI it discovers everything else from.

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider>
      <QueryClientProvider client={queryClient}>
        <PreferenceProvider>
          <ThemeProvider>
            <NotificationProvider>
              <ConfirmProvider>
                <PageTitleProvider>
                  <App />
                </PageTitleProvider>
              </ConfirmProvider>
            </NotificationProvider>
          </ThemeProvider>
        </PreferenceProvider>
      </QueryClientProvider>
    </AuthProvider>
  </StrictMode>,
);
