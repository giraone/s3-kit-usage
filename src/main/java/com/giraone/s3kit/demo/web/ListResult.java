package com.giraone.s3kit.demo.web;

import java.util.List;

/**
 * @param bucket the bucket
 * @param keys   the matching S3 keys "{scope}/{objectId}"
 */
public record ListResult(String bucket, List<String> keys) {
}
