package com.giraone.s3kit.demo.service;

import com.giraone.s3kit.demo.buckets.ObjectToBucketMapper;
import com.giraone.s3kit.demo.buckets.ObjectToBucketMapperFactory;
import com.giraone.s3kit.demo.config.ApplicationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Derives the bucket from the scope with the {@link ObjectToBucketMapper}.
 */
@Component
public class BucketResolver {

    private final ObjectToBucketMapper objectToBucketMapper = ObjectToBucketMapperFactory.getInstance();
    private final ApplicationProperties applicationProperties;

    public BucketResolver(final ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    public String resolveBucket(final String scope) {
        final String mapped = objectToBucketMapper.mapScopeToBucketName(scope);
        final List<String> names = usedBucketNames();
        final int index = objectToBucketMapper.getBucketNames().indexOf(mapped);
        return names.get(index % names.size());
    }

    public List<String> usedBucketNames() {
        final List<String> all = objectToBucketMapper.getBucketNames();
        return all.subList(0, Math.min(applicationProperties.bucketCount(), all.size()));
    }
}
