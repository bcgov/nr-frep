import { createContext, type ReactNode } from 'react';

import type { FamLoginUser, LoginProvider } from './types';

export type AuthContextType = {
  user: FamLoginUser | undefined;
  isLoggedIn: boolean;
  isLoading: boolean;
  login: (provider: LoginProvider) => void;
  logout: () => void;
  /** Checks the access token expiry and refreshes via the refresh token if
   *  needed. Returns the current access token string, or undefined if the
   *  session has expired (user will be signed out automatically). */
  ensureFreshToken: () => Promise<string | undefined>;
  /** Unconditionally refreshes the session (rotates the refresh token, sliding
   *  the session). Throws if the refresh token has expired — the caller should
   *  treat that as a real expiry and sign out. Used by "Stay logged in". */
  forceRefreshSession: () => Promise<void>;
};

export type AuthProviderProps = {
  children: ReactNode;
};

export const AuthContext = createContext<AuthContextType | undefined>(undefined);
