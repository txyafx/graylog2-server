import PropTypes from 'prop-types';
import React from 'react';
import { NavDropdown, MenuItem } from 'react-bootstrap';
import { LinkContainer } from 'react-router-bootstrap';
import { ExternalLink } from 'components/common';

import DocsHelper from 'util/DocsHelper';
import Routes from 'routing/Routes';

const HelpMenu = ({ active }) => {
  return (
    <NavDropdown title="Help"
                 id="help-menu-dropdown"
                 active={active}
                 className="dropdown-submenu left-submenu">
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
};

export default HelpMenu;
