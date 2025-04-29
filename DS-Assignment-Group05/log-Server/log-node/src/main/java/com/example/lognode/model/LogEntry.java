package com.example.lognode.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

@Document(collection = "logs")
@Getter
@Setter
@NoArgsConstructor
public class LogEntry {

    @Id
    private String id;
    private long index;
    private long term;
    private String message;
    private String level;
    private long timestamp;
    private boolean committed;

    public LogEntry(String id, long term, String message, String level, long timestamp) {
        this.id =  UUID.randomUUID().toString();
        this.term = term;
        this.message = message;
        this.level = level != null ? level : "INFO";
        this.timestamp = timestamp;
    }


    public LogEntry(String id, String message, String level, long timestamp, long term, long index, boolean committed) {
        this.id = id;
        this.message = message;
        this.level = level;
        this.timestamp = timestamp;
        this.index = index;
        this.committed = committed;
    }
}
