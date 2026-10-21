package com.giraone.s3kit.demo.buckets;

import java.util.List;

/**
 * Interface for different job-to-bucket implementations.
 */
public interface ObjectToBucketMapper {

    /**
     * Return a bucket (name) for a given scope.
     * @param scope The scope to use.
     * @return a valid bucket name - null should never be returned!
     */
    String mapScopeToBucketName(String scope);

    /**
     * Get a list of bucket names to be created on startup.
     * @return List of valid bucket names.
     */
    List<String> getBucketNames();
}
