package com.example.lognode.service;

import com.example.lognode.model.LogEntry;
import com.example.lognode.model.RaftRequest;
import com.example.lognode.model.RaftState;
import com.example.lognode.model.Role;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class RaftService {
    @Getter
    private static final Logger logger = LoggerFactory.getLogger(RaftService.class);
    @Getter
    private final RaftState raftState;
    private final LogService logService;
    private final PeerDiscoveryService peerDiscoveryService;
    private final RestTemplate restTemplate;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicLong lastHeartbeatTime = new AtomicLong(System.currentTimeMillis());
    @Getter
    private final String nodeId;
    private final int heartbeatInterval;
    private final int checkInterval;
    private final int electionTimeout;
    private static final int SNAPSHOT_INTERVAL = 100; //snapshot frequency
    private final NodeStatusService nodeStatusService;

    @Autowired
    public RaftService(LogService logService, PeerDiscoveryService peerDiscoveryService, RestTemplateBuilder restTemplateBuilder,
                       @Value("${raft.heartbeatInterval}") int heartbeatInterval, @Value("${raft.checkInterval}") int checkInterval, NodeStatusService nodeStatusService) {
        this.logService = logService;
        this.peerDiscoveryService = peerDiscoveryService;
        this.restTemplate = restTemplateBuilder.build();
        this.nodeStatusService = nodeStatusService;
        this.raftState = new RaftState();
        this.nodeId = System.getProperty("eureka.instance.instance-id", "node-" + System.currentTimeMillis());
        this.heartbeatInterval = heartbeatInterval;
        this.checkInterval = checkInterval;
        Random random = new Random();
        // CHANGE: Increased election timeout range to 1000-2000ms to reduce simultaneous timeouts and improve leader stability
        this.electionTimeout = 1000 + random.nextInt(1000);
    }

    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(this::checkHeartbeat, 0, checkInterval, TimeUnit.MILLISECONDS);
        // CHANGE: Schedule separate tasks for heartbeats and log replication for modularity
        scheduler.scheduleAtFixedRate(this::sendHeartbeats, 0, heartbeatInterval, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::replicateLogs, 0, heartbeatInterval * 2, TimeUnit.MILLISECONDS);
    }

    private void checkHeartbeat() {
        long currentTime = System.currentTimeMillis();
        if (raftState.getCurrentState() != Role.LEADER && currentTime - lastHeartbeatTime.get() > electionTimeout) {
            try { // NEW: Random delay to prevent election storm
                Thread.sleep(100 + new Random().nextInt(200)); // 100-300ms delay
            } catch (InterruptedException e) {
                logger.warn("Interrupted during election delay", e);
            }
            startElection();
        }
    }

    // CHANGE: New method to send heartbeats using RaftRequest.HeartbeatRequest
    private void sendHeartbeats() {
        if (raftState.getCurrentState() == Role.LEADER) {
            RaftRequest.HeartbeatRequest request = new RaftRequest.HeartbeatRequest();
            request.setTerm(raftState.getCurrentTerm());
            request.setLeaderId(nodeId);

            peerDiscoveryService.getPeerNodes().forEach((peerId, url) -> { // CHANGED: Use forEach with peerId
                int maxRetries = 3;
                boolean success = false;
                for (int attempt = 1; attempt <= maxRetries; attempt++) {
                    try {
                        RaftRequest.EntryAppendResponse response = restTemplate.postForObject(
                                url + "/api/raft/heartbeat", request, RaftRequest.EntryAppendResponse.class);
                        success = true;
                        break;
                    } catch (Exception e) {
                        logger.warn("Attempt {}/{}: Failed to send heartbeat to {}", attempt, maxRetries, url, e);
                        if (attempt == maxRetries) {
                            logger.error("Failed to send heartbeat to {} after {} attempts", url, maxRetries);
                        }
                    }
                }
                nodeStatusService.updateStatus(peerId, success); // NEW: Update node status
            });
        }
    }

    // CHANGE: New method to handle log replication and fault tolerance
    private void replicateLogs() {
        if (raftState.getCurrentState() == Role.LEADER) {
            ConcurrentLinkedQueue<LogEntry> logBuffer = raftState.getLogBuffer();
            long lastIndex = logBuffer.size();
            long lastTerm = lastIndex > 0 ? logBuffer.stream().skip(lastIndex - 1).findFirst().map(LogEntry::getTerm).orElse(0L) : 0;
            RaftRequest.EntryAppendRequest request = new RaftRequest.EntryAppendRequest();
            request.setTerm(raftState.getCurrentTerm());
            request.setLeaderId(nodeId);
            request.setPrevLogIndex(lastIndex);
            request.setPrevLogTerm(lastTerm);
            request.setLeaderCommit(raftState.getCommitIndex());

            peerDiscoveryService.getPeerNodes().forEach((peerId, url) -> { // CHANGED: Use forEach with peerId
                if (nodeStatusService.getNodeStatuses().getOrDefault(peerId, NodeStatusService.NodeStatus.UP) == NodeStatusService.NodeStatus.DOWN) { // NEW: Skip DOWN nodes
                    logger.debug("Skipping replication to DOWN node {}", peerId);
                    return;
                }
                int maxRetries = 3;
                for (int attempt = 1; attempt <= maxRetries; attempt++) {
                    try {
                        RaftRequest.EntryAppendResponse response = restTemplate.postForObject(
                                url + "/api/raft/appendEntry", request, RaftRequest.EntryAppendResponse.class);
                        if (response != null && !response.isSuccess()) {
                            handleFollowerCatchUp(url, lastIndex);
                        }
                        nodeStatusService.updateStatus(peerId, response != null && response.isSuccess()); // NEW: Update node status
                        break;
                    } catch (Exception e) {
                        logger.warn("Attempt {}/{}: Failed to send AppendEntries to {}", attempt, maxRetries, url, e);
                        if (attempt == maxRetries) {
                            logger.error("Failed to send AppendEntries to {} after {} attempts", url, maxRetries);
                            nodeStatusService.updateStatus(peerId, false); // NEW: Mark as DOWN on failure
                        }
                    }
                }
            });
        }
    }

    private void startElection() {
        raftState.setCurrentState(Role.CANDIDATE);
        raftState.setCurrentTerm(raftState.getCurrentTerm() + 1);
        raftState.setVotedTo(null);
        raftState.resetElectionTimeOut();
        lastHeartbeatTime.set(System.currentTimeMillis());

        logger.info("Starting election for term {}", raftState.getCurrentTerm());

        AtomicInteger votesCount = new AtomicInteger(1);
        int totalNodes = peerDiscoveryService.getPeerNodes().size() - 1;
        int majority = totalNodes / 2 + 1;
        RaftRequest.Request request = new RaftRequest.Request();
        request.setTerm(raftState.getCurrentTerm());
        request.setCandidateId(nodeId);

        peerDiscoveryService.getPeerNodes().forEach((peerId, url) -> {
            if (nodeStatusService.getNodeStatuses().getOrDefault(peerId, NodeStatusService.NodeStatus.UP) == NodeStatusService.NodeStatus.DOWN) { // NEW: Skip DOWN nodes
                logger.debug("Skipping vote request to DOWN node {}", peerId);
                return;
            }
            try {
                RaftRequest.Response response = restTemplate.postForObject(
                        url + "/api/raft/requestVote", request, RaftRequest.Response.class);
                if (response != null) {
                    synchronized (raftState) {
                        if (response.getTerm() > raftState.getCurrentTerm()) {
                            logger.info("Received higher term {} from {}. Stepping down to FOLLOWER", response.getTerm(), url);
                            raftState.setCurrentTerm(response.getTerm());
                            raftState.setCurrentState(Role.FOLLOWER);
                            raftState.setVotedTo(null);
                            lastHeartbeatTime.set(System.currentTimeMillis());
                            return;
                        }
                        if (response.isVoteGranted() && raftState.getCurrentState() == Role.CANDIDATE) {
                            int currentVotes = votesCount.incrementAndGet();
                            logger.debug("Received vote from {}. Total votes: {}", url, currentVotes);
                            if (currentVotes >= majority) {
                                raftState.setCurrentState(Role.LEADER);
                                raftState.setCurrentLeader(nodeId);
                                logger.info("Elected as leader for term {}", raftState.getCurrentTerm());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to request vote from {}", url, e);
            }
        });
    }

    public RaftRequest.Response handleVoteRequest(RaftRequest.Request request) {
        RaftRequest.Response response = new RaftRequest.Response();
        synchronized (raftState) {
            response.setTerm(raftState.getCurrentTerm());

            if (request.getTerm() < raftState.getCurrentTerm()) {
                response.setVoteGranted(false);
                return response;
            }

            if (request.getTerm() > raftState.getCurrentTerm()) {
                raftState.setCurrentTerm(request.getTerm());
                raftState.setCurrentState(Role.FOLLOWER);
                raftState.setVotedTo(null);
            }

            if (raftState.getVotedTo() == null || raftState.getVotedTo().equals(request.getCandidateId())) {
                raftState.setVotedTo(request.getCandidateId());
                response.setVoteGranted(true);
                lastHeartbeatTime.set(System.currentTimeMillis());
            } else {
                response.setVoteGranted(false);
            }
        }
        return response;
    }

    // CHANGE: New method to handle heartbeat requests
    public RaftRequest.EntryAppendResponse handleHeartbeat(RaftRequest.HeartbeatRequest request) {
        RaftRequest.EntryAppendResponse response = new RaftRequest.EntryAppendResponse();
        synchronized (raftState) {
            response.setTerm(raftState.getCurrentTerm());

            // CHANGE: Update lastHeartbeatTime for valid heartbeats to prevent premature elections
            if (request.getTerm() >= raftState.getCurrentTerm() && request.getLeaderId() != null) {
                lastHeartbeatTime.set(System.currentTimeMillis());
            }

            if (request.getTerm() > raftState.getCurrentTerm() ||
                    (request.getTerm() == raftState.getCurrentTerm() && !nodeId.equals(request.getLeaderId()))) {
                logger.info("Processing heartbeat from {} with term {}. Current state: {}", request.getLeaderId(), request.getTerm(), raftState.getCurrentState());

                if (request.getTerm() > raftState.getCurrentTerm()) {
                    raftState.setCurrentTerm(request.getTerm());
                    raftState.setVotedTo(null);
                }

                raftState.setCurrentState(Role.FOLLOWER);
                raftState.setCurrentLeader(request.getLeaderId());
                response.setSuccess(true);
            } else {
                logger.debug("Ignored heartbeat from {} with term {}. Current state: {}, term: {}", request.getLeaderId(), request.getTerm(), raftState.getCurrentState(), raftState.getCurrentTerm());
                response.setSuccess(false);
            }
        }
        return response;
    }

    public RaftRequest.EntryAppendResponse handleAppendEntries(RaftRequest.EntryAppendRequest request) {
        RaftRequest.EntryAppendResponse response = new RaftRequest.EntryAppendResponse();
        synchronized (raftState) {
            response.setTerm(raftState.getCurrentTerm());

            if (request.getTerm() >= raftState.getCurrentTerm() && request.getLeaderId() != null) {
                lastHeartbeatTime.set(System.currentTimeMillis());
            }

            if (request.getTerm() > raftState.getCurrentTerm() ||
                    (request.getTerm() == raftState.getCurrentTerm() && !nodeId.equals(request.getLeaderId()))) {
                logger.info("Processing AppendEntries from {} with term {}. Current state: {}", request.getLeaderId(), request.getTerm(), raftState.getCurrentState());

                if (request.getTerm() > raftState.getCurrentTerm()) {
                    raftState.setCurrentTerm(request.getTerm());
                    raftState.setVotedTo(null);
                }

                raftState.setCurrentState(Role.FOLLOWER);
                raftState.setCurrentLeader(request.getLeaderId());

                ConcurrentLinkedQueue<LogEntry> logBuffer = raftState.getLogBuffer();
                if (request.getPrevLogIndex() > 0 && (logBuffer.size() < request.getPrevLogIndex() ||
                        logBuffer.stream().skip(request.getPrevLogIndex() - 1).findFirst().map(LogEntry::getTerm).orElse(0L) != request.getPrevLogTerm())) {
                    response.setSuccess(false);
                    return response;
                }

                if (request.getEntries() != null && !request.getEntries().isEmpty()) {
                    request.getEntries().forEach(entry -> {
                        LogEntry newEntry = new LogEntry(
                                entry.getId(),
                                entry.getMessage(),
                                entry.getLevel(),
                                entry.getTimestamp(),
                                entry.getTerm(),
                                entry.getIndex(),
                                entry.isCommitted()
                        );
                        logBuffer.add(newEntry);
                        if (!newEntry.isCommitted()) { // NEW: Log uncommitted entries
                            logger.info("Stored uncommitted log entry with index {} in buffer", newEntry.getIndex());
                        }
                    });
                    logger.info("Appended {} entries to log buffer from leader {}", request.getEntries().size(), request.getLeaderId());
                }

                if (request.getLeaderCommit() > raftState.getCommitIndex()) {
                    commitLogs(request.getLeaderCommit());
                    raftState.setCommitIndex(request.getLeaderCommit());
                }

                response.setSuccess(true);
            } else {
                logger.debug("Ignored AppendEntries from {} with term {}. Current state: {}, term: {}", request.getLeaderId(), request.getTerm(), raftState.getCurrentState(), raftState.getCurrentTerm());
                response.setSuccess(false);
            }
        }
        return response;
    }

    private void commitLogs(long newCommitIndex) {
        ConcurrentLinkedQueue<LogEntry> logBuffer = raftState.getLogBuffer();
        logBuffer.stream()
                .filter(entry -> entry.getIndex() <= newCommitIndex && !entry.isCommitted())
                .forEach(entry -> {
                    entry.setCommitted(true);
                    logService.saveLog(entry);
                    logger.info("Follower committed log entry with index {} to MongoDB", entry.getIndex());
                });

        // Snapshot every SNAPSHOT_INTERVAL commits
        if (newCommitIndex >= SNAPSHOT_INTERVAL && newCommitIndex % SNAPSHOT_INTERVAL == 0) {
            createSnapshot();
        }
    }

    private void createSnapshot() {
        logger.info("Creating snapshot at commit index {}", raftState.getCommitIndex());
        // Basic snapshot: Rely on MongoDB persistence
        // In a full implementation, save log metadata (last included index/term)
    }


    private void handleFollowerCatchUp(String url, long lastIndex) {
        logger.info("Starting log recovery for follower at URL {} with lastIndex {}", url, lastIndex); // NEW: Log recovery
        ConcurrentLinkedQueue<LogEntry> logBuffer = raftState.getLogBuffer();
        long followerIndex = lastIndex;
        while (followerIndex >= 0) {
            RaftRequest.EntryAppendRequest request = new RaftRequest.EntryAppendRequest();
            request.setTerm(raftState.getCurrentTerm());
            request.setLeaderId(nodeId);
            request.setPrevLogIndex(followerIndex);
            request.setPrevLogTerm(followerIndex > 0 ? logBuffer.stream().skip(followerIndex - 1).findFirst().map(LogEntry::getTerm).orElse(0L) : 0);
            long finalFollowerIndex = followerIndex;
            List<LogEntry> entriesToSend = logBuffer.stream()
                    .filter(entry -> entry.getIndex() > finalFollowerIndex)
                    .collect(Collectors.toList());
            request.setEntries(entriesToSend);
            request.setLeaderCommit(raftState.getCommitIndex());

            try {
                RaftRequest.EntryAppendResponse response = restTemplate.postForObject(
                        url + "/api/raft/appendEntry", request, RaftRequest.EntryAppendResponse.class);
                if (response != null && response.isSuccess()) {
                    logger.info("Successfully sent {} logs to follower at URL {}", entriesToSend.size(), url); // NEW: Log recovery success
                    break;
                }
                followerIndex--;
            } catch (Exception e) {
                logger.warn("Failed to send catch-up logs to {}", url, e);
            }
        }
    }

    private long lastTerm(ConcurrentLinkedQueue<LogEntry> logBuffer) {
        return logBuffer.size() > 0 ? logBuffer.stream().skip(logBuffer.size() - 1).findFirst().map(LogEntry::getTerm).orElse(0L) : 0;
    }

    public void appendLog(LogEntry entry) {
        if (raftState.getCurrentState() != Role.LEADER) {
            throw new IllegalStateException("Not the leader");
        }

        ConcurrentLinkedQueue<LogEntry> logBuffer = raftState.getLogBuffer();
        entry.setIndex(logBuffer.size() + 1);
        entry.setTerm(raftState.getCurrentTerm());
        logBuffer.add(entry);
        logger.info("Appended log entry with index {} to leader buffer", entry.getIndex());

        RaftRequest.EntryAppendRequest request = new RaftRequest.EntryAppendRequest();
        request.setTerm(raftState.getCurrentTerm());
        request.setLeaderId(nodeId);
        request.setPrevLogIndex(logBuffer.size() - 1);
        request.setPrevLogTerm(logBuffer.size() > 1 ? logBuffer.stream().skip(logBuffer.size() - 2).findFirst().map(LogEntry::getTerm).orElse(0L) : 0);
        request.setEntries(List.of(entry));
        request.setLeaderCommit(raftState.getCommitIndex());

        AtomicInteger ACK = new AtomicInteger(1); // Count self
        int totalNodes = peerDiscoveryService.getPeerNodes().size() + 1; // Include self
        int majority = totalNodes / 2 + 1;

        peerDiscoveryService.getPeerNodes().values().forEach(url -> {
            int maxRetries = 3;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    RaftRequest.EntryAppendResponse response = restTemplate.postForObject(
                            url + "/api/raft/appendEntry", request, RaftRequest.EntryAppendResponse.class);
                    if (response != null && response.isSuccess()) {
                        if (ACK.incrementAndGet() >= majority) {
                            commitLogs(entry.getIndex());
                            raftState.setCommitIndex(entry.getIndex());
                            logger.info("Committed log entry with index {} to MongoDB", entry.getIndex());
                            notifyFollowers(entry.getIndex());
                        }
                        break;
                    }
                } catch (Exception e) {
                    logger.warn("Attempt {}/{}: Failed to replicate log to {}", attempt, maxRetries, url, e);
                    if (attempt == maxRetries) {
                        logger.error("Failed to replicate log to {} after {} attempts", url, maxRetries);
                    }
                }
            }
        });
    }

    private void notifyFollowers(long commitIndex) {
        RaftRequest.EntryAppendRequest commitRequest = new RaftRequest.EntryAppendRequest();
        commitRequest.setTerm(raftState.getCurrentTerm());
        commitRequest.setLeaderId(nodeId);
        commitRequest.setPrevLogIndex(raftState.getLogBuffer().size());
        commitRequest.setPrevLogTerm(lastTerm(raftState.getLogBuffer()));
        commitRequest.setLeaderCommit(commitIndex);

        peerDiscoveryService.getPeerNodes().values().forEach(url -> {
            int maxRetries = 3;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    RaftRequest.EntryAppendResponse response = restTemplate.postForObject(
                            url + "/api/raft/appendEntry", commitRequest, RaftRequest.EntryAppendResponse.class);
                    break;
                } catch (Exception e) {
                    logger.warn("Attempt {}/{}: Failed to send commit notice to {}", attempt, maxRetries, url, e);
                    if (attempt == maxRetries) {
                        logger.error("Failed to send commit notice to {} after {} attempts", url, maxRetries);
                    }
                }
            }
        });
    }
}