package com.giraone.s3kit.demo.web;

import com.giraone.s3kit.S3Service;
import com.giraone.s3kit.demo.service.BucketResolver;
import com.giraone.s3kit.download.S3Download;
import com.giraone.s3kit.model.ListObjectsResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * REST API demonstrating streaming uploads and downloads with s3kit.
 */
@RestController
@RequestMapping("/s3")
public class S3Controller {

    private final S3Service s3Service;
    private final BucketResolver bucketResolver;

    public S3Controller(final S3Service s3Service, final BucketResolver bucketResolver) {
        this.s3Service = s3Service;
        this.bucketResolver = bucketResolver;
    }

    @PostMapping("/upload/{scope}/{objectId}")
    public ResponseEntity<UploadResult> upload(
        @PathVariable final String scope, @PathVariable final String objectId, final HttpServletRequest request)
        throws IOException {

        final String bucket = bucketResolver.resolveBucket(scope);
        final String key = key(scope, objectId);
        final String contentType = request.getContentType() != null
            ? request.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        final long contentLength = request.getContentLengthLong();
        final long size;
        try (InputStream body = request.getInputStream()) {
            if (contentLength >= 0) {
                s3Service.uploadSingleFile(bucket, key, contentLength, contentType, body);
                size = contentLength;
            } else {
                // chunked transfer encoding: length unknown, so use a multipart upload
                size = s3Service.uploadMultipartFile(bucket, key, contentType, body);
            }
        }
        return ResponseEntity.ok(new UploadResult(bucket, key, size));
    }

    @GetMapping("/download/{scope}/{objectId}")
    public ResponseEntity<InputStreamResource> download(@PathVariable final String scope, @PathVariable final String objectId) {

        final S3Download download = s3Service.fetch(bucketResolver.resolveBucket(scope), key(scope, objectId));
        final ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
            .contentType(download.contentType() != null
                ? MediaType.parseMediaType(download.contentType()) : MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.fileName() + "\"");
        if (download.contentLength() != null) {
            builder.contentLength(download.contentLength());
        }
        // The resource is streamed to the client and closed by Spring afterwards
        return builder.body(new InputStreamResource(download.inputStream()));
    }

    @PostMapping(path = "/multipart-upload/{scope}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<UploadResult>> multipartUpload(
        @PathVariable final String scope, final MultipartHttpServletRequest request) throws IOException {

        final String bucket = bucketResolver.resolveBucket(scope);
        final List<UploadResult> results = new ArrayList<>();
        final MultiValueMap<String, MultipartFile> files = request.getMultiFileMap();
        for (final var entry : files.entrySet()) {
            for (final MultipartFile file : entry.getValue()) {
                final String key = key(scope, entry.getKey());
                final String contentType = file.getContentType() != null
                    ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
                try (InputStream in = file.getInputStream()) {
                    s3Service.uploadSingleFile(bucket, key, file.getSize(), contentType, in);
                }
                results.add(new UploadResult(bucket, key, file.getSize()));
            }
        }
        return ResponseEntity.ok(results);
    }

    @DeleteMapping("/delete/{scope}/{objectId}")
    public ResponseEntity<Void> delete(@PathVariable final String scope, @PathVariable final String objectId) {

        s3Service.deleteObject(bucketResolver.resolveBucket(scope), key(scope, objectId));
        return ResponseEntity.noContent().build();
    }

    @RequestMapping(path = "/head/{scope}/{objectId}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> head(@PathVariable final String scope, @PathVariable final String objectId) {

        final int status = s3Service.headObject(bucketResolver.resolveBucket(scope), key(scope, objectId));
        return ResponseEntity.status(status).build();
    }

    /**
     * Lists the keys of a scope, optionally restricted to the keys whose objectId starts with the given prefix.
     */
    @GetMapping("/list/{scope}")
    public ResponseEntity<ListResult> list(
        @PathVariable final String scope, @RequestParam(required = false, defaultValue = "") final String prefix) {

        final String bucket = bucketResolver.resolveBucket(scope);
        final ListObjectsResult result = s3Service.listObjects(bucket, key(scope, prefix));
        return ResponseEntity.ok(new ListResult(bucket, result.keys()));
    }

    private static String key(final String scope, final String objectId) {
        return scope + S3Service.PATH_DELIMITER + objectId;
    }
}
