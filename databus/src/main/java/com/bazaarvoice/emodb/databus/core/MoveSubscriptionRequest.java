package com.bazaarvoice.emodb.databus.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;

public class MoveSubscriptionRequest {

    private final String _ownerId;
    private final String _from;
    private final String _to;

    @JsonCreator
    public MoveSubscriptionRequest(@JsonProperty ("ownerId") String ownerId,
                                   @JsonProperty ("from") String from,
                                   @JsonProperty ("to") String to) {
        _ownerId = requireNonNull(ownerId, "ownerId");
        _from = requireNonNull(from, "from");
        _to = requireNonNull(to, "to");
    }

    public String getOwnerId() {
        return _ownerId;
    }

    public String getFrom() {
        return _from;
    }

    public String getTo() {
        return _to;
    }
}
