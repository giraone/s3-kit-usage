package com.giraone.s3kit.demo.buckets;

public final class ObjectToBucketMapperFactory {

    private static final ObjectToBucketMapper INSTANCE = new DefaultObjectToBucketMapper();

    private ObjectToBucketMapperFactory() {
    }

    public static ObjectToBucketMapper getInstance() {
        return INSTANCE;
    }
}
