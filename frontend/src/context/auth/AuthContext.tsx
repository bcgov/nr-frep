import { createContext, type ReactNode } from 'react';

import type { FamLoginUser, LoginProvider } from './types';

export type AuthContextType = {
  user: FamLoginUser | undefined;
  isLoggedIn: boolean;
  isLoading: boolean;
  login: (provider: LoginProvider) => void;
  logout: () => void;
  /** Completes the authorization-code exchange on the /authCallback route. Rejects if the
   *  exchange fails, so the callback page can show an error instead of a permanent spinner. */
  completeSignIn: () => Promise<void>;
  /** Checks the access token expiry and renews via the refresh token if
   *  needed. Returns the current access token string, or undefined if the
   *  session has expired (user will be signed out automatically). */
  ensureFreshToken: () => Promise<string | undefined>;
  /** Unconditionally renews the session (rotates the refresh token, sliding
   *  the session). Throws if the refresh token has expired — the caller should
   *  treat that as a real expiry and sign out. Used by "Stay logged in". */
  forceRefreshSession: () => Promise<void>;
};

export type AuthProviderProps = {
  children: ReactNode;
};

export const AuthContext = createContext<AuthContextType | undefined>(undefined);
