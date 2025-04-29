package com.example.lognode.repository;

import com.example.lognode.model.LogEntry;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface LogRepository extends MongoRepository<LogEntry, String> {
    List<LogEntry> findAll(Sort sort);
}
