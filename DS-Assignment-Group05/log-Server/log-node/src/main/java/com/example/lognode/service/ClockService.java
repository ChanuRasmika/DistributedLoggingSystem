package com.example.lognode.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class ClockService {

    private static final Logger logger = LoggerFactory.getLogger(ClockService.class);
    private long skewOffset;

    @PostConstruct
    public void init() {
        Random random = new Random();
        skewOffset = random.nextLong(1001) - 500; //+ or - 500ms
        logger.info("Initialized clock skew: {}ms", skewOffset);
    }

    public long now(){
        return System.currentTimeMillis() + skewOffset;
    }

    @Scheduled(fixedRate = 30000) // Every 30s
    public void correctSkew() {
        long oldSkew = skewOffset;
        skewOffset = skewOffset / 2; // Reduce skew by 50%
        logger.info("Corrected clock skew from {}ms to {}ms", oldSkew, skewOffset);
    }
}
