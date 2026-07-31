import { env } from '@/env';

const redirectUri = window.location.origin + (env.VITE_BASE_PATH || '/').replace(/\/$/, '');

/**
 * Sign-out redirect lands on the deployed environment's landing page (FAM/Cognito
 * requires an absolute URL registered on the User Pool client). Derived from the
 * current origin (+ base path) so it's correct in every zone without extra config;
 * used by Amplify's fallback signOut() — the primary federated logout chain builds
 * its own return URL. Local dev works after sign-out for the same reason.
 */
export const redirectSignOut = `${redirectUri}/`;

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
