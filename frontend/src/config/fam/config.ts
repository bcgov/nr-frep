import { env } from '@/env';

const redirectUri = window.location.origin + (env.VITE_BASE_PATH || '/').replace(/\/$/, '');

/**
 * Sign-out redirect: where Cognito returns the browser once the session is cleared. Used by
 * Amplify's fallback signOut(); the primary federated logout chain builds its own return URL from
 * {@code window.location.origin}, which resolves to the same string.
 *
 * <p><b>NO trailing slash.</b> Cognito matches "Allowed sign-out URLs" by exact string, and the
 * registered values are the bare origins — {@code http://localhost:3000} and
 * {@code https://nr-frep-test.apps.gold.devops.gov.bc.ca}. This used to append one, so the fallback
 * path sent a URL Cognito had never seen and was refused with {@code Required parameters missing} —
 * the error Cognito returns for an unregistered logout_uri, which names neither the parameter nor
 * the real problem. Local dev never hit it because the federated chain is configured there and the
 * fallback never runs.
 *
 * <p>Derived from the current origin (+ base path) so it is correct in every zone without extra
 * config. If a zone is ever deployed under a base path, that exact URL has to be registered too.
 */
export const redirectSignOut = redirectUri;

/** Cognito hosted-UI domain — also the host of the /logout endpoint in the federated logout chain. */
export const COGNITO_HOSTED_UI_DOMAIN =
  env.VITE_COGNITO_DOMAIN?.trim() ||
  'lza-prod-fam-user-pool-domain.auth.ca-central-1.amazoncognito.com';

const verificationMethod: 'code' | 'token' = 'code';

const amplifyconfig = {
  Auth: {
    Cognito: {
      userPoolId: env.VITE_USER_POOLS_ID,
      userPoolClientId: env.VITE_USER_POOLS_WEB_CLIENT_ID,
      signUpVerificationMethod: verificationMethod,
      loginWith: {
        oauth: {
          domain: COGNITO_HOSTED_UI_DOMAIN,
          scopes: ['openid', 'profile', 'email'],
          redirectSignIn: [`${redirectUri}/auth/callback`],
          redirectSignOut: [redirectSignOut],
          responseType: verificationMethod,
        },
      },
    },
  },
};

export default amplifyconfig;
