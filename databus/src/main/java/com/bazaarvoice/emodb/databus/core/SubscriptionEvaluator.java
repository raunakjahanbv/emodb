package com.bazaarvoice.emodb.databus.core;

import com.bazaarvoice.emodb.common.dropwizard.log.RateLimitedLog;
import com.bazaarvoice.emodb.common.dropwizard.log.RateLimitedLogFactory;
import com.bazaarvoice.emodb.common.uuid.TimeUUIDs;
import com.bazaarvoice.emodb.databus.auth.DatabusAuthorizer;
import com.bazaarvoice.emodb.databus.model.OwnedSubscription;
import com.bazaarvoice.emodb.sor.api.UnknownTableException;
import com.bazaarvoice.emodb.sor.condition.eval.ConditionEvaluator;
import com.bazaarvoice.emodb.sor.core.DataProvider;
import com.bazaarvoice.emodb.sor.core.UpdateRef;
import com.bazaarvoice.emodb.table.db.Table;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

public class SubscriptionEvaluator {
    private static final Logger _log = LoggerFactory.getLogger(SubscriptionEvaluator.class);

    private final DataProvider _dataProvider;
    private final RateLimitedLog _rateLimitedLog;
    private final DatabusAuthorizer _databusAuthorizer;

    @Inject
    public SubscriptionEvaluator(DataProvider dataProvider,
                                 DatabusAuthorizer databusAuthorizer,
                                 RateLimitedLogFactory logFactory) {
        _dataProvider = dataProvider;
        _databusAuthorizer = databusAuthorizer;
        _rateLimitedLog = logFactory.from(_log);
    }

    public Iterable<OwnedSubscription> matches(Iterable<OwnedSubscription> subscriptions, final MatchEventData eventData) {
        return FluentIterable.from(subscriptions)
                .filter(subscription -> matches(subscription, eventData));
    }

    public boolean matches(OwnedSubscription subscription, ByteBuffer eventData, Date since) {
        MatchEventData matchEventData;
        try {
            matchEventData = getMatchEventData(eventData);
        } catch (OrphanedEventException e) {
            return false;
        }

        return matches(subscription, matchEventData)
                && (since == null || !matchEventData.getEventTime().before(since));
    }

    public boolean matches(OwnedSubscription subscription, MatchEventData eventData) {
        Table table = eventData.getTable();
        try {
            Map<String, Object> json;
            if (eventData.getTags().isEmpty()) {
                json = table.getAttributes();
            } else {
                json = Maps.newHashMap(table.getAttributes());
                json.put(UpdateRef.TAGS_NAME, eventData.getTags());
            }
            return ConditionEvaluator.eval(subscription.getTableFilter(), json, new SubscriptionIntrinsics(table, eventData.getKey())) &&
                    subscriberHasPermission(subscription, table);
        } catch (Exception e) {
            _rateLimitedLog.error(e, "Unable to evaluate condition for subscription " + subscription.getName() +
                    " on table {}: {}", table.getName(), subscription.getTableFilter());
            return false;
        }
    }

    public MatchEventData getMatchEventData(ByteBuffer eventData) throws OrphanedEventException {
        UpdateRef ref = UpdateRefSerializer.fromByteBuffer(eventData.duplicate());
        try {
            return new MatchEventData(_dataProvider.getTable(ref.getTable()), ref.getKey(), ref.getTags(), ref.getChangeId());
        } catch (UnknownTableException e) {
            throw new OrphanedEventException(ref.getTable(), Instant.ofEpochMilli(TimeUUIDs.getTimeMillis(ref.getChangeId())));
        }
    }

    private boolean subscriberHasPermission(OwnedSubscription subscription, Table table) {
        return _databusAuthorizer.owner(subscription.getOwnerId()).canReceiveEventsFromTable(table.getName());
    }

    protected class MatchEventData {
        private final Table _table;
        private final String _key;
        private final Set<String> _tags;
        private final UUID _changeId;

        public MatchEventData(Table table, String key, Set<String> tags, UUID changeId) {
            _table = requireNonNull(table, "table");
            _key = key;
            _tags = tags;
            _changeId = changeId;
        }

        public Table getTable() {
            return _table;
        }

        public String getKey() {
            return _key;
        }

        public Set<String> getTags() {
            return _tags;
        }

        public Date getEventTime() {
            return TimeUUIDs.getDate(_changeId);
        }
    }
}
