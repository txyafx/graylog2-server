package org.graylog.plugins.enterprise.search.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.searchbox.client.http.JestHttpClient;
import io.searchbox.core.SearchResult;
import org.graylog.plugins.enterprise.search.Parameter;
import org.graylog.plugins.enterprise.search.Query;
import org.graylog.plugins.enterprise.search.Search;
import org.graylog.plugins.enterprise.search.SearchJob;
import org.graylog.plugins.enterprise.search.SearchType;
import org.graylog.plugins.enterprise.search.elasticsearch.searchtypes.ESDateHistogram;
import org.graylog.plugins.enterprise.search.elasticsearch.searchtypes.ESMessageList;
import org.graylog.plugins.enterprise.search.elasticsearch.searchtypes.ESSearchTypeHandler;
import org.graylog.plugins.enterprise.search.filter.AndFilter;
import org.graylog.plugins.enterprise.search.filter.StreamFilter;
import org.graylog.plugins.enterprise.search.params.QueryReferenceBinding;
import org.graylog.plugins.enterprise.search.params.ValueBinding;
import org.graylog.plugins.enterprise.search.searchtypes.DateHistogram;
import org.graylog.plugins.enterprise.search.searchtypes.MessageList;
import org.graylog2.indexer.ranges.IndexRange;
import org.graylog2.indexer.ranges.IndexRangeService;
import org.graylog2.plugin.indexer.searches.timeranges.RelativeRange;
import org.graylog2.plugin.indexer.searches.timeranges.TimeRange;
import org.graylog2.plugin.streams.Stream;
import org.graylog2.streams.StreamService;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import javax.inject.Provider;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ElasticsearchBackendUsingCorrectIndicesTest {
    private static Map<String, Provider<ESSearchTypeHandler<? extends SearchType>>> handlers = ImmutableMap.of(
            MessageList.NAME, ESMessageList::new,
            DateHistogram.NAME, ESDateHistogram::new
    );
    private static Map<String, Provider<Parameter.BindingHandler>> bindingHandlers = ImmutableMap.of(
            ValueBinding.NAME, ValueBinding.Handler::new,
            QueryReferenceBinding.NAME, () -> new QueryReferenceBinding.Handler(new ObjectMapper())
    );
    private static final QueryStringParser queryStringParser = new QueryStringParser();

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private IndexRangeService indexRangeService;

    @Mock
    private StreamService streamService;

    @Mock
    private SearchResult jestResult;

    @Mock
    private JestHttpClient jestClient;

    @Captor
    private ArgumentCaptor<io.searchbox.core.Search> clientRequestCaptor;

    private SearchJob job;
    private Query query;

    private ElasticsearchBackend backend;

    @Before
    public void setupSUT() throws Exception {
        when(jestClient.execute(any(), any())).thenReturn(jestResult);
        when(jestResult.isSucceeded()).thenReturn(true);

        this.backend = new ElasticsearchBackend(handlers, bindingHandlers, queryStringParser, jestClient, indexRangeService, streamService);
    }

    @Before
    public void before() throws Exception {
        this.query = Query.builder()
                .id("query1")
                .timerange(RelativeRange.create(600))
                .query(ElasticsearchQueryString.builder().queryString("*").build())
                .searchTypes(ImmutableSet.of(MessageList.builder().id("1").build()))
                .build();
        final Search search = Search.builder()
                .id("search1")
                .queries(ImmutableSet.of(query))
                .build();
        this.job = new SearchJob("job1", search);
    }

    @Test
    public void queryDoesNotFallBackToUsingAllIndicesWhenNoIndexRangesAreReturned() throws Exception {
        when(indexRangeService.find(any(DateTime.class), any(DateTime.class))).thenReturn(new TreeSet<>());

        final ESGeneratedQueryContext context = backend.generate(job, query, Collections.emptySet());
        backend.doRun(job, query, context, Collections.emptySet());

        verify(jestClient, times(1)).execute(clientRequestCaptor.capture(), any());

        final io.searchbox.core.Search clientRequest = clientRequestCaptor.getValue();
        assertThat(clientRequest).isNotNull();
        assertThat(clientRequest.getIndex()).isEqualTo("");
    }

    @Test
    public void queryUsesCorrectTimerangeWhenDeterminingIndexRanges() throws Exception {
        when(indexRangeService.find(any(DateTime.class), any(DateTime.class))).thenReturn(new TreeSet<>());

        final long datetimeFixture = 1530194810;
        DateTimeUtils.setCurrentMillisFixed(datetimeFixture);

        final ESGeneratedQueryContext context = backend.generate(job, query, Collections.emptySet());
        backend.doRun(job, query, context, Collections.emptySet());

        final ArgumentCaptor<DateTime> fromCapture = ArgumentCaptor.forClass(DateTime.class);
        final ArgumentCaptor<DateTime> toCapture = ArgumentCaptor.forClass(DateTime.class);
        verify(indexRangeService, times(1)).find(fromCapture.capture(), toCapture.capture());

        assertThat(fromCapture.getValue().isEqual(new DateTime(datetimeFixture).minusSeconds(600))).isTrue();
        assertThat(toCapture.getValue().isEqual(new DateTime(datetimeFixture))).isTrue();
    }

    private SortedSet<IndexRange> sortedSetOf(IndexRange... indexRanges) {
        final Comparator<IndexRange> indexRangeComparator = Comparator.comparing(IndexRange::indexName);

        final TreeSet<IndexRange> indexRangeSets = new TreeSet<>(indexRangeComparator);

        indexRangeSets.addAll(Arrays.asList(indexRanges));

        return indexRangeSets;
    }

    private Query dummyQuery(TimeRange timeRange) {
        return Query.builder()
                .id("query1")
                .timerange(timeRange)
                .query(ElasticsearchQueryString.builder().queryString("*").build())
                .searchTypes(ImmutableSet.of(MessageList.builder().id("1").build()))
                .build();
    }

    private Search dummySearch(Query... queries) {
        return Search.builder()
                .id("search1")
                .queries(ImmutableSet.copyOf(queries))
                .build();
    }
    @Test
    public void queryUsesOnlyIndicesIncludingTimerangeAndStream() throws Exception {
        final String streamId = "streamId";
        final Stream stream = mock(Stream.class, RETURNS_DEEP_STUBS);
        when(stream.getId()).thenReturn(streamId);
        when(streamService.load(streamId)).thenReturn(stream);

        final IndexRange indexRange1 = mock(IndexRange.class);
        when(indexRange1.indexName()).thenReturn("index1");
        when(indexRange1.streamIds()).thenReturn(Collections.singletonList(streamId));
        final IndexRange indexRange2 = mock(IndexRange.class);
        when(indexRange2.indexName()).thenReturn("index2");
        when(indexRange2.streamIds()).thenReturn(Collections.singletonList(streamId));
        final IndexRange indexRange3 = mock(IndexRange.class);
        when(indexRange3.indexName()).thenReturn("index3");
        when(indexRange3.streamIds()).thenReturn(Collections.emptyList());

        final SortedSet<IndexRange> indexRanges = sortedSetOf(indexRange1, indexRange2, indexRange3);
        when(indexRangeService.find(any(DateTime.class), any(DateTime.class))).thenReturn(indexRanges);

        final Query query = dummyQuery(RelativeRange.create(600))
                .toBuilder()
                .filter(StreamFilter.ofId(streamId))
                .build();
        final Search search = dummySearch(query);
        final SearchJob job = new SearchJob("job1", search);
        final ESGeneratedQueryContext context = backend.generate(job, query, Collections.emptySet());

        backend.doRun(job, query, context, Collections.emptySet());

        verify(jestClient, times(1)).execute(clientRequestCaptor.capture(), any());

        final io.searchbox.core.Search clientRequest = clientRequestCaptor.getValue();
        assertThat(clientRequest).isNotNull();
        assertThat(clientRequest.getIndex()).isEqualTo("index1,index2");
    }

    @Test
    public void queryDoesNotFallBackToAllIndices() throws Exception {
        final Search search = dummySearch(dummyQuery(RelativeRange.create(600)));
        final SearchJob job = new SearchJob("job1", search);
        final ESGeneratedQueryContext context = backend.generate(job, query, Collections.emptySet());

        backend.doRun(job, query, context, Collections.emptySet());

        verify(jestClient, times(1)).execute(clientRequestCaptor.capture(), any());

        final io.searchbox.core.Search clientRequest = clientRequestCaptor.getValue();
        assertThat(clientRequest).isNotNull();
        assertThat(clientRequest.getIndex()).isNotEqualTo("_all");
    }
    
    @Test
    public void queryUsesOnlyIndicesBelongingToStream() throws Exception {
        final String stream1id = "stream1id";
        final Stream stream1 = mock(Stream.class, RETURNS_DEEP_STUBS);
        when(stream1.getId()).thenReturn(stream1id);

        final String stream2id = "stream2id";
        final Stream stream2 = mock(Stream.class, RETURNS_DEEP_STUBS);
        when(stream2.getId()).thenReturn(stream2id);

        final String stream3id = "stream3id";
        final Stream stream3 = mock(Stream.class, RETURNS_DEEP_STUBS);
        when(stream3.getId()).thenReturn(stream3id);

        final String stream4id = "stream4id";
        final Stream stream4 = mock(Stream.class, RETURNS_DEEP_STUBS);
        when(stream4.getId()).thenReturn(stream4id);

        when(stream4.getIndexSet().isManagedIndex(eq("index2"))).thenReturn(true);

        final IndexRange indexRange1 = mock(IndexRange.class);
        when(indexRange1.indexName()).thenReturn("index1");
        when(indexRange1.streamIds()).thenReturn(Collections.singletonList(stream1id));
        final IndexRange indexRange2 = mock(IndexRange.class);
        when(indexRange2.indexName()).thenReturn("index2");
        when(indexRange2.streamIds()).thenReturn(null);

        final SortedSet<IndexRange> indexRanges = sortedSetOf(indexRange1, indexRange2);
        when(indexRangeService.find(any(DateTime.class), any(DateTime.class))).thenReturn(indexRanges);

        when(streamService.load(eq(stream1id))).thenReturn(stream1);
        when(streamService.load(eq(stream2id))).thenReturn(stream2);
        when(streamService.load(eq(stream3id))).thenReturn(stream3);
        when(streamService.load(eq(stream4id))).thenReturn(stream4);

        final Query query = dummyQuery(RelativeRange.create(600)).toBuilder()
                .filter(AndFilter.and(StreamFilter.ofId(stream1id), StreamFilter.ofId(stream2id), StreamFilter.ofId(stream3id), StreamFilter.ofId(stream4id)))
                .build();
        final Search search = dummySearch(query);
        final SearchJob job = new SearchJob("job1", search);
        final ESGeneratedQueryContext context = backend.generate(job, query, Collections.emptySet());

        backend.doRun(job, query, context, Collections.emptySet());

        verify(jestClient, times(1)).execute(clientRequestCaptor.capture(), any());

        final io.searchbox.core.Search clientRequest = clientRequestCaptor.getValue();
        assertThat(clientRequest).isNotNull();
        assertThat(clientRequest.getIndex()).isEqualTo("index1,index2");
    }
}