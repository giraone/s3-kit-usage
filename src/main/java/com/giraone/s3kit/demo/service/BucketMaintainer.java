package com.giraone.s3kit.demo.service;

import com.giraone.s3kit.S3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Creates the buckets on startup, if they do not exist yet.
 */
@Service
public class BucketMaintainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(BucketMaintainer.class);

    private final S3Service s3Service;
    private final BucketResolver bucketResolver;

    public BucketMaintainer(final S3Service s3Service, final BucketResolver bucketResolver) {
        this.s3Service = s3Service;
        this.bucketResolver = bucketResolver;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        int created = 0;
        for (final String bucketName : bucketResolver.usedBucketNames()) {
            if (s3Service.createBucketIfNotExists(bucketName)) {
                created++;
            }
        }
        LOGGER.info("Bucket creation done: {} buckets created, {} already existed", created,
            bucketResolver.usedBucketNames().size() - created);
    }
}
