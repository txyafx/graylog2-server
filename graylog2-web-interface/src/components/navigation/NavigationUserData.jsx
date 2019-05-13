import PropTypes from 'prop-types';
import React from 'react';
import { Badge, Nav, NavItem, NavDropdown } from 'react-bootstrap';
import { LinkContainer } from 'react-router-bootstrap';

import Routes from 'routing/Routes';
import AppConfig from 'util/AppConfig';
import URLUtils from 'util/URLUtils';

import GlobalThroughput from 'components/throughput/GlobalThroughput';
import UserMenu from 'components/navigation/UserMenu';
import HelpMenu from 'components/navigation/HelpMenu';
import badgeStyles from 'components/bootstrap/Badge.css';

import InactiveNavItem from './InactiveNavItem';

const _isActive = (requestPath, prefix) => {
  return requestPath.indexOf(URLUtils.appPrefixed(prefix)) === 0;
};

const UserNavContents = ({ fullName, pathname, loginName, screen }) => {
  return (
    <React.Fragment>
      <LinkContainer to={Routes.SYSTEM.NODES.LIST}
                     className={`nav-usercontent-${screen}`}>
        <InactiveNavItem>
          <GlobalThroughput />
        </InactiveNavItem>
      </LinkContainer>

      <HelpMenu active={_isActive(pathname, Routes.GETTING_STARTED)}
                screen={screen} />
      <UserMenu fullName={fullName} loginName={loginName} screen={screen} />
    </React.Fragment>
  );
};

const NavigationUserData = (props) => {
  return (
    <Nav navbar pullRight>
      <NavItem>
        {AppConfig.gl2DevMode() && (
          <Badge className={`${badgeStyles.badgeDanger} ${badgeStyles.badgeDev}`}>
            DEV
          </Badge>
        )}
      </NavItem>

      <NavDropdown title={<i className="fa fa-cog fa-lg" />}
                   id="user-menu-dropdown">
        <UserNavContents {...props} screen="md" />
      </NavDropdown>

      <UserNavContents {...props} screen="sm" />
    </Nav>
  );
};

NavigationUserData.propTypes = {
  pathname: PropTypes.string.isRequired,
  loginName: PropTypes.string.isRequired,
  fullName: PropTypes.string.isRequired,
};

UserNavContents.propTypes = NavigationUserData.propTypes;

export default NavigationUserData;
