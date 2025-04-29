package com.example.lognode.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class RaftRequest {

    @Getter
    @Setter
    public static class Request {
        private long term;
        private String candidateId;
    }

    @Getter
    @Setter
    public static class Response {
        private long term;
        private boolean voteGranted;
    }

    @Getter
    @Setter
    public static class HeartbeatRequest {
        private long term;
        private String leaderId;
    }

    @Getter
    @Setter
    public static class EntryAppendRequest {
        private long term;
        private String leaderId;
        private long prevLogIndex;
        private long prevLogTerm;
        private List<LogEntry> entries;
        private long leaderCommit;
    }

    @Getter
    @Setter
    public static class EntryAppendResponse {
        private long term;
        private boolean success;
    }
}
