import { env } from '@/env';

const redirectUri = window.location.origin + (env.VITE_BASE_PATH || '/').replace(/\/$/, '');

// LOCAL DEV: Cognito / FAM auth config disabled. Uncomment before deploying.
export const redirectSignOut = env.VITE_REDIRECT_SIGN_OUT?.trim() ?? '';

/*
 * --- AWS Amplify Auth configuration for Cognito (FAM / IDIR) ---
 *
 * const verificationMethods: 'code' | 'token' = 'code';
 *
 * const amplifyconfig = {
 *   Auth: {
 *     Cognito: {
 *       userPoolId: env.VITE_USER_POOLS_ID,
 *       userPoolClientId: env.VITE_USER_POOLS_WEB_CLIENT_ID,
 *       signUpVerificationMethod: verificationMethods,
 *       loginWith: {
 *         oauth: {
 *           domain: 'lza-prod-fam-user-pool-domain.auth.ca-central-1.amazoncognito.com',
 *           scopes: ['openid', 'profile'],
 *           redirectSignIn: [`${redirectUri}/dashboard`],
 *           redirectSignOut: [redirectSignOut],
 *           responseType: verificationMethods,
 *         },
 *       },
 *     },
 *   },
 * };
 *
 * export default amplifyconfig;
 */

void redirectUri;

const amplifyconfig = {};

export default amplifyconfig;
