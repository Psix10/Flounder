package com.acme.sportplatform.protocol.application;
import java.util.List;
import java.util.UUID;
import com.acme.sportplatform.protocol.api.RuleBlockRequest;
public record ResolvedProtocolRules(UUID versionId, int capacity, String prefix, String metricKey,
        List<Integer> order, List<Integer> allowed, String heatOrder, String partialHeat,
        List<RuleBlockRequest> blocks) { }
