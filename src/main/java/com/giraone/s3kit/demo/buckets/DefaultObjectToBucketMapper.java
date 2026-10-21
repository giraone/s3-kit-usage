package com.giraone.s3kit.demo.buckets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * The default implementation for scope to S3 bucket mapping.
 * Maps via the hash of the scope string.
 */
public class DefaultObjectToBucketMapper implements ObjectToBucketMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultObjectToBucketMapper.class);

    private static final String[] bucketNames = {
        "v1-00", "v1-01", "v1-02", "v1-03", "v1-04", "v1-05", "v1-06", "v1-07", "v1-08", "v1-09"
    };

    DefaultObjectToBucketMapper() {
    }

    @Override
    public String mapScopeToBucketName(String scope) {

        final String bucketName = bucketNames[Math.abs(scope.hashCode()) % bucketNames.length];
        LOGGER.debug("Mapped scope {} to bucket {}", scope, bucketName);
        return bucketName;
    }

    @Override
    public List<String> getBucketNames() {
        return Arrays.asList(bucketNames);
    }
}
