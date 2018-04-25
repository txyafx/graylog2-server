import Immutable from 'immutable';

import QueriesActions from 'enterprise/actions/QueriesActions';
import SearchActions from 'enterprise/actions/SearchActions';
import WidgetActions from 'enterprise/actions/WidgetActions';
import CurrentViewActions from 'enterprise/actions/CurrentViewActions';
import ViewsActions from 'enterprise/actions/ViewsActions';
import SelectedFieldsActions from 'enterprise/actions/SelectedFieldsActions';
import TitlesActions from 'enterprise/actions/TitlesActions';
import DashboardWidgetsActions from 'enterprise/actions/DashboardWidgetsActions';
import WidgetFilterActions from '../../actions/WidgetFilterActions';
import QueryFiltersActions from '../../actions/QueryFiltersActions';
import AggregationWidget from '../aggregationbuilder/AggregationWidget';
import MessagesWidget from '../aggregationbuilder/MessagesWidget';
import Widget from '../aggregationbuilder/Widget';

const deserializeWidget = (value) => {
  switch (value.type) {
    case 'aggregation':
      return AggregationWidget.fromJSON(value);
    case 'messages':
      return MessagesWidget.fromJSON(value);
    default:
      return Widget.fromJSON(value);
  }
};

export default class ViewDeserializer {
  static deserializeFrom(viewResponse) {
    const positions = {};
    Object.keys(viewResponse.state).forEach((queryId) => {
      positions[queryId] = viewResponse.state[queryId].positions;
    });
    const { id, title, description, summary } = viewResponse;
    const view = {
      id,
      positions,
      title,
      description,
      summary,
      dashboardPositions: viewResponse.dashboard_state.positions,
    };
    return Promise.all([
      ViewsActions.update(view.id, view),
      DashboardWidgetsActions.load(view.id, new Immutable.Map(viewResponse.dashboard_state.widgets)),
    ]).then(() => SearchActions.get(viewResponse.search_id))
      .then(search => Object.assign({}, { view: viewResponse }, { search }))
      .then((state) => { // restore each query in search
        const queries = {};
        state.search.queries.forEach((query) => {
          const rangeParams = {};
          Object.keys(query.timerange) // massaging time range
            .filter(key => key !== 'type')
            .forEach((key) => { rangeParams[key] = query.timerange[key]; });
          queries[query.id] = new Immutable.Map({
            id: query.id,
            query: query.query.query_string,
            rangeType: query.timerange.type,
            rangeParams: new Immutable.Map(rangeParams),
          });
          if (query.filter) {
            const selectedStreams = query.filter.filters.filter(f => f.type === 'stream').map(f => f.id);
            QueryFiltersActions.streams(view.id, query.id, selectedStreams);
          }
          SelectedFieldsActions.set(query.id, viewResponse.state[query.id].selected_fields);
        });
        QueriesActions.load(view.id, new Immutable.Map(queries));
        return state;
      })
      .then((state) => { // restore search parameters
        let parameters = Immutable.Map();
        state.search.parameters.forEach((parameter) => {
          parameters = parameters.set(parameter.name, Immutable.fromJS(parameter));
        });
        SearchParameterActions.declare(view.id, parameters); // Even declare empty state to trigger parameters
        return state;
      })
      .then((state) => { // clear execution state
        SearchExecutionStateActions.clear();
        return state;
      })
      .then((state) => { // restore each widget in view
        const viewState = viewResponse.state;
        Object.keys(viewState).forEach((queryId) => {
          const widgets = {};
          viewState[queryId].widgets.forEach((widget) => {
            widgets[widget.id] = deserializeWidget(widget);
            if (widget.filter) {
              WidgetFilterActions.change(widget.id, widget.filter);
            }
          });
          WidgetActions.load(view.id, queryId, new Immutable.Map(widgets));
          TitlesActions.load(queryId, viewState[queryId].titles || {});
        });
        return state;
      })
      .then((state) => { // activate view, query and widget mapping
        const queryId = state.search.queries[0].id;
        return Promise.all([
          CurrentViewActions.selectView(view.id),
          CurrentViewActions.selectQuery(queryId),
          CurrentViewActions.currentWidgetMapping(new Immutable.Map(viewResponse.state[queryId].widget_mapping)),
        ]).then(() => state);
      });
  }
}
