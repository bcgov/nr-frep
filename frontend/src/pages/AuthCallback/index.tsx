import { Loading } from '@carbon/react';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { useAuth } from '@/context/auth/useAuth';

/**
 * Completes the OAuth authorization-code exchange.
 *
 * <p>Amplify did this implicitly inside `configure()`, so there was nothing to mount. With
 * `oidc-client-ts` the exchange is an explicit call, and it needs a route of its own — registered in
 * the **public** table (the session does not exist until this page creates it) and ordered **above
 * the `*` catch-all**, which would otherwise swallow the callback, query string and all, before the
 * code could be spent.
 */
const AuthCallbackPage = () => {
  const { completeSignIn } = useAuth();
  const navigate = useNavigate();
  const [failed, setFailed] = useState(false);

  /**
   * The authorization code is single-use, and React StrictMode mounts every component twice in dev.
   * Without this guard the second mount spends an already-redeemed code and the exchange fails —
   * in development only, which is a memorable way to lose an afternoon.
   */
  const exchangeStarted = useRef(false);

  useEffect(() => {
    if (exchangeStarted.current) return;
    exchangeStarted.current = true;

    void completeSignIn()
      .then(() => {
        // replace, not push: the callback URL still carries ?code=&state= and must not be
        // reachable with the Back button, where the spent code would fail the exchange.
        void navigate('/', { replace: true });
      })
      .catch(() => setFailed(true));
  }, [completeSignIn, navigate]);

  if (failed) {
    return (
      <div className="auth-callback__error" role="alert">
        <h1>Sign-in could not be completed</h1>
        <p>Please return to the home page and try signing in again.</p>
        <a href="/">Return to FREP</a>
      </div>
    );
  }

  return (
    <Loading data-testid="auth-callback-loading" withOverlay={true} description="Signing in" />
  );
};

export default AuthCallbackPage;
