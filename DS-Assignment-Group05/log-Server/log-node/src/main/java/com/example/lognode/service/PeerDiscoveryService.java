package com.example.lognode.service;

import com.netflix.discovery.EurekaClient;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PeerDiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(PeerDiscoveryService.class);
    private final EurekaClient eurekaClient;
    @Getter
    private final Map<String, String> peerNodes = new ConcurrentHashMap<>();

    @Autowired
    public PeerDiscoveryService(EurekaClient eurekaClient) {
        this.eurekaClient = eurekaClient;
    }

    @Scheduled(fixedRate = 3000) // Reduce to 3 seconds
    public void refreshPeerNodes() {
        logger.debug("Refreshing peer nodes");
        Map<String, String> newPeerNodes = new ConcurrentHashMap<>();
        try {
            com.netflix.discovery.shared.Application application = eurekaClient.getApplication("log-node");
            if (application == null) {
                logger.warn("No application found for 'log-node' in Eureka");
                return;
            }
            logger.debug("Found {} instances for log-node", application.getInstances().size());
            application.getInstances().forEach(instance -> {
                String nodeId = instance.getInstanceId();
                String url = instance.getHomePageUrl();
                if (url != null) {
                    if (!url.endsWith("/")) {
                        url = url + "/";
                    }
                    newPeerNodes.put(nodeId, url);
                    logger.debug("Added peer: {} -> {}", nodeId, url);
                }
            });
            peerNodes.clear();
            peerNodes.putAll(newPeerNodes); // Atomic update
        } catch (Exception e) {
            logger.error("Failed to refresh peer nodes from Eureka", e);
            // Keep existing peerNodes to avoid losing data
        }
    }

    // Add method to force refresh if leader URL is missing
    public void forceRefresh() {
        refreshPeerNodes();
    }
}