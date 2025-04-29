package com.example.lognode.controller;

import com.example.lognode.model.RaftRequest;
import com.example.lognode.model.RaftState;
import com.example.lognode.service.NodeStatusService;
import com.example.lognode.service.PeerDiscoveryService;
import com.example.lognode.service.RaftService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class PeerController {

    private final PeerDiscoveryService peerDiscoveryService;
    private final RaftService raftService;
    private final NodeStatusService nodeStatusService;

    @Autowired
    public PeerController(PeerDiscoveryService peerDiscoveryService, RaftService raftService, NodeStatusService nodeStatusService) {
        this.peerDiscoveryService = peerDiscoveryService;
        this.raftService = raftService;
        this.nodeStatusService = nodeStatusService;
    }

    @GetMapping("/peer")
    public Map<String, String> getPeer() {
        return peerDiscoveryService.getPeerNodes();
    }

    @GetMapping("/nodes/status") // NEW: Endpoint for node statuses
    public Map<String, NodeStatusService.NodeStatus> getNodeStatuses() {
        return nodeStatusService.getNodeStatuses();
    }

    @PostMapping("/raft/requestVote")
    public RaftRequest.Response requestVote(@RequestBody RaftRequest.Request request) {
        return raftService.handleVoteRequest(request);
    }

    @PostMapping("/raft/appendEntry")
    public RaftRequest.EntryAppendResponse appendEntries(@RequestBody RaftRequest.EntryAppendRequest request) {
        return raftService.handleAppendEntries(request);
    }

    // CHANGE: Added endpoint to handle heartbeat requests for Raft leader authority
    @PostMapping("/raft/heartbeat")
    public RaftRequest.EntryAppendResponse handleHeartbeat(@RequestBody RaftRequest.HeartbeatRequest request) {
        return raftService.handleHeartbeat(request);
    }

    @GetMapping("/raft/state")
    public RaftState getRaftState() {
        return raftService.getRaftState();
    }
}