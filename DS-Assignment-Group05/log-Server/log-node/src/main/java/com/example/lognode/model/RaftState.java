package com.example.lognode.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.concurrent.ConcurrentLinkedQueue;

@Getter
@Setter
@NoArgsConstructor
public class RaftState {

    private long currentTerm;
    private String votedTo;
    private String currentLeader;
    private Role currentState;
    private long electionTimeout;
    private ConcurrentLinkedQueue<LogEntry> logBuffer = new ConcurrentLinkedQueue<>();
    private long commitIndex;

    public RaftState(long currentTerm, String votedTo, String currentLeader, Role currentState, long electionTimeout) {
        this.currentTerm = 0;
        this.votedTo = null;
        this.currentLeader = null;
        this.currentState = Role.FOLLOWER;
        this.electionTimeout = generateRandomTimeOut();
        this.commitIndex = 0;
    }



    private synchronized long generateRandomTimeOut() {
        return 150 + (long) (Math.random() * 150);
    }

    public void resetElectionTimeOut() {
        this.electionTimeout = generateRandomTimeOut();
    }

    public synchronized ConcurrentLinkedQueue<LogEntry> getLogBuffer() {
        return logBuffer;
    }

    public synchronized long getCommitIndex() {
        return commitIndex;
    }

    public synchronized void setCommitIndex(long commitIndex) {
        this.commitIndex = commitIndex;
    }
}
