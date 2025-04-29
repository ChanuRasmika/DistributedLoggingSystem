package com.example.logclient;

import model.LogEntry;
import model.RaftState;
import model.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;


import java.util.List;

@RestController // NEW: Make LogSender a controller
@Component
public class LogSender {
    private static final Logger logger = LoggerFactory.getLogger(LogSender.class);
    private final RestTemplate restTemplate;
    private final DiscoveryClient discoveryClient;

    @Autowired
    public LogSender(RestTemplate restTemplate, DiscoveryClient discoveryClient) {
        this.restTemplate = restTemplate;
        this.discoveryClient = discoveryClient;
    }

    public void sendLog(LogEntry logEntry) {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String leaderUrl = findLeaderUrl();
                if (leaderUrl == null) {
                    logger.warn("No leader found, retrying {}/{}", attempt, maxRetries);
                    Thread.sleep(1000);
                    continue;
                }
                logger.info("Sending log to leader: {}", leaderUrl);
                restTemplate.postForObject(leaderUrl + "/api/log", logEntry, LogEntry.class);
                return;
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 503) {
                    logger.warn("Leader unavailable, retrying {}/{}", attempt, maxRetries);
                } else {
                    throw e;
                }
            } catch (Exception e) {
                logger.error("Failed to send log, retrying {}/{}", attempt, maxRetries, e);
            }
        }
        throw new RuntimeException("Failed to send log after " + maxRetries + " attempts");
    }

    private String findLeaderUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances("log-node");
        for (ServiceInstance instance : instances) {
            try {
                String url = instance.getUri().toString();
                RaftState state = restTemplate.getForObject(url + "/api/raft/state", RaftState.class);
                if (state != null && state.getCurrentState() == Role.LEADER) {
                    return url.endsWith("/") ? url : url + "/";
                }
            } catch (Exception e) {
                logger.warn("Failed to check state for {}", instance.getUri(), e);
            }
        }
        return null;
    }

    @Bean
    public CommandLineRunner sendLog() {
        return args -> {
            LogEntry entry = new LogEntry();
            entry.setMessage("Application started successfully");
            entry.setLevel("BUG");
            sendLog(entry);
            logger.info("Log sent successfully");
        };
    }

    @PostMapping("/send") // NEW: REST endpoint for front-end
    public void send(@RequestBody LogEntry logEntry) {
        sendLog(logEntry);
    }
}
