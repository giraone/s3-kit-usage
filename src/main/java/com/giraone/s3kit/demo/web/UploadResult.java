package com.giraone.s3kit.demo.web;

/**
 * @param bucket the bucket
 * @param key    the S3 key "{scope}/{objectId}"
 * @param size   the number of bytes stored, or -1 if unknown
 */
public record UploadResult(String bucket, String key, long size) {
}
