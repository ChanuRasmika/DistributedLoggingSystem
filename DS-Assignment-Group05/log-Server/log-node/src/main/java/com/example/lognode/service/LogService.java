package com.example.lognode.service;

import com.example.lognode.model.LogEntry;
import com.example.lognode.repository.LogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LogService {
    private static final Logger logger = LoggerFactory.getLogger(LogService.class);
    private final LogRepository logRepository;

    @Autowired
    public LogService(LogRepository logRepository) {
        this.logRepository = logRepository;
    }


    public LogEntry saveLog(LogEntry logEntry) {
        if (logEntry == null) {
            throw new IllegalArgumentException("Log entry cannot be null");
        }
        LogEntry savedEntry = logRepository.save(logEntry);
        logger.info("Saved log entry with index {} to MongoDB", savedEntry.getIndex());
        return savedEntry;
    }


}
