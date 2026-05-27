import { env } from '@/env';

const redirectUri = window.location.origin + (env.VITE_BASE_PATH || '/').replace(/\/$/, '');

/**
 * Sign-out redirect lands on the deployed environment's landing page (FAM/Cognito
 * requires an absolute URL registered on the User Pool client). Default to the
 * current origin so local dev still works after sign-out.
 */
export const redirectSignOut = env.VITE_REDIRECT_SIGN_OUT?.trim() || `${redirectUri}/`;

const cognitoDomain =
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
          domain: cognitoDomain,
          scopes: ['openid', 'profile'],
          redirectSignIn: [`${redirectUri}/welcome`],
          redirectSignOut: [redirectSignOut],
          responseType: verificationMethod,
        },
      },
    },
  },
};

export default amplifyconfig;
