package model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogEntry {
    private String id;
    private String message;
    private String level;
    private long timestamp;
}
