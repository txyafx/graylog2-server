import PropTypes from 'prop-types';
import React from 'react';
import { NavDropdown, MenuItem } from 'react-bootstrap';
import { LinkContainer } from 'react-router-bootstrap';
import { ExternalLink } from 'components/common';

import DocsHelper from 'util/DocsHelper';
import Routes from 'routing/Routes';

const HelpMenu = ({ active, screen }) => {
  return (
    <NavDropdown title="Help"
                 id="help-menu-dropdown"
                 active={active}
                 className={
                  screen === 'sm'
                    ? 'nav-usercontent-sm'
                    : 'dropdown-submenu left-submenu nav-usercontent-md'
                }>
      <LinkContainer to={Routes.getting_started(true)}>
        <MenuItem>Getting Started</MenuItem>
      </LinkContainer>

      <MenuItem href={DocsHelper.versionedDocsHomePage()} target="_blank">
        <ExternalLink>Documentation</ExternalLink>
      </MenuItem>
    </NavDropdown>
  );
};

HelpMenu.propTypes = {
  active: PropTypes.bool.isRequired,
  screen: PropTypes.string,
};

HelpMenu.defaultProps = {
  screen: 'md',
};

export default HelpMenu;
