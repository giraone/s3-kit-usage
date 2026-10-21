package com.giraone.s3kit.demo;

import com.giraone.s3kit.config.S3ClientConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(S3ClientConfiguration.class)
@ConfigurationPropertiesScan
public class DemoApplication {

    static void main(final String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
