package com.example.lognode.controller;

import com.example.lognode.model.LogEntry;
import com.example.lognode.model.Role;
import com.example.lognode.repository.LogRepository;
import com.example.lognode.service.ClockService;
import com.example.lognode.service.PeerDiscoveryService;
import com.example.lognode.service.RaftService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class LogController {
    private static final Logger logger = LoggerFactory.getLogger(LogController.class);
    private final RaftService raftService;
    private final LogRepository logRepository;
    private final ClockService clockService;

    @Autowired
    public LogController(RaftService raftService, LogRepository logRepository, ClockService clockService) {
        this.raftService = raftService;
        this.logRepository = logRepository;
        this.clockService = clockService;
    }

    @PostMapping("/log")
    public ResponseEntity<LogEntry> submitLog(@RequestBody LogEntry logEntry) {
        logger.info("Received log submission: {}", logEntry.getMessage());
        if (logEntry.getMessage() == null || logEntry.getMessage().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LogEntry message cannot be empty");
        }

        if (raftService.getRaftState().getCurrentState() != Role.LEADER) {
            logger.warn("Node is not the leader for log submission");
            HttpHeaders headers = new HttpHeaders();
            headers.add("Retry-After", "5");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .headers(headers)
                    .body(null);
        }

        // Set UUID, timestamp, and default level
        logEntry.setId(UUID.randomUUID().toString());
        logEntry.setTimestamp(clockService.now());
        logEntry.setLevel(logEntry.getLevel() != null ? logEntry.getLevel() : "INFO");

        try {
            raftService.appendLog(logEntry);
        } catch (IllegalStateException e) {
            logger.error("Node is no longer the leader during log submission", e);
            HttpHeaders headers = new HttpHeaders();
            headers.add("Retry-After", "5");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .headers(headers)
                    .body(null);
        }
        logger.info("Successfully appended log entry with index {}", logEntry.getIndex());
        return ResponseEntity.ok(logEntry);
    }

    @GetMapping("/logs") // NEW: Fetch all logs
    public ResponseEntity<List<LogEntry>> getLogs() {
        logger.info("Fetching all logs");
        List<LogEntry> logs = logRepository.findAll();
        return ResponseEntity.ok(logs);
    }
}