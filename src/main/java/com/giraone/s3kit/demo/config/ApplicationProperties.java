package com.giraone.s3kit.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param bucketCount  number of buckets used and created on startup
 * @param users        the users for basic authentication
 */
@ConfigurationProperties(prefix = "demo")
public record ApplicationProperties(int bucketCount, List<User> users) {

    public ApplicationProperties {
        bucketCount = bucketCount <= 0 ? 10 : bucketCount;
        users = users == null || users.isEmpty()
            ? List.of(new User("user-a", "change-me"), new User("user-b", "change-me"))
            : users;
    }

    public record User(String name, String password) {
    }
}
