import { Asleep, Light, Logout } from '@carbon/icons-react';
import { SideNavLink } from '@carbon/react';
import { type FC } from 'react';

import AvatarImage from '@/components/Layout/AvatarImage';

import { useAuth } from '@/context/auth/useAuth';
import { useTheme } from '@/context/theme/useTheme';
import { useOnlineStatus } from '@/hooks/useOnlineStatus';

import './index.scss';

/** Friendly label for the user's identity provider (falls back to IDIR). */
const PROVIDER_LABELS: Record<string, string> = {
  IDIR: 'IDIR',
  BCEIDBUSINESS: 'Business BCeID',
};

const HeaderPanelProfile: FC = () => {
  const { theme, toggleTheme } = useTheme();
  const { logout, user } = useAuth();
  const online = useOnlineStatus();

  const providerLabel = PROVIDER_LABELS[user?.idpProvider ?? ''] ?? 'IDIR';

  // Offline the Cognito session can't be refreshed, so a reload leaves no user to describe — the
  // panel used to render the literal "undefined undefined". Nothing here is needed on-device (an
  // offline copy is already checked out to whoever took it), so the identity block is simply left
  // out rather than shown empty. Change theme and Logout stay.
  const showIdentity = online && user != null;

  return (
    <div className="my-profile-container">
      {showIdentity && (
        <>
          <div className="user-info-section">
            <div className="user-image">
              <AvatarImage userName={`${user.firstName} ${user.lastName}`} size="large" />
            </div>
            <div className="user-data">
              <p className="user-name">{`${user.firstName} ${user.lastName}`}</p>
              <p>{`${providerLabel}: ${user.userName ?? ''}`}</p>
              <p>{`Email: ${user.email ?? ''}`}</p>
            </div>
          </div>
          <hr className="divisory" />
        </>
      )}
      <nav className="account-nav">
        <ul>
          <SideNavLink
            className="cursor-pointer"
            renderIcon={theme === 'g100' ? Light : Asleep}
            onClick={toggleTheme}
          >
            Change theme
          </SideNavLink>
          <SideNavLink className="cursor-pointer" renderIcon={Logout} onClick={logout}>
            Log out
          </SideNavLink>
        </ul>
      </nav>
    </div>
  );
};

export default HeaderPanelProfile;
