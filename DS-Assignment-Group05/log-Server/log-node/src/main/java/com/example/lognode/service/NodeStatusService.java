package com.example.lognode.service;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NodeStatusService {
    private static final Logger logger = LoggerFactory.getLogger(NodeStatusService.class);
    @Getter
    private final Map<String, NodeStatus> nodeStatuses = new ConcurrentHashMap<>();
    private final Map<String, Integer> missedHeartbeats = new ConcurrentHashMap<>();
    private static final int MAX_MISSED_HEARTBEATS = 3;

    public enum NodeStatus {
        UP, DOWN
    }

    public void updateStatus(String nodeId, boolean heartbeatSuccess) {
        NodeStatus status = nodeStatuses.compute(nodeId, (id, current) -> {
            int missedCount = heartbeatSuccess ? 0 : missedHeartbeats.getOrDefault(id, 0) + 1;
            missedHeartbeats.put(id, missedCount);
            NodeStatus newStatus = missedCount >= MAX_MISSED_HEARTBEATS ? NodeStatus.DOWN : NodeStatus.UP;
            if (!newStatus.equals(current)) {
                logger.info("Node {} status changed to {}", id, newStatus);
            }
            return newStatus;
        });
    }


}