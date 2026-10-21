package com.giraone.s3kit.demo;

import com.giraone.s3kit.S3Service;
import com.giraone.s3kit.demo.service.BucketResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class S3ControllerIT {

    private static final GenericContainer<?> S3_CONTAINER = S3MockTestContainer.create();
    private static final int MIB = 1024 * 1024;
    private static final String BOUNDARY = "----s3kitDemoBoundary";

    static {
        S3_CONTAINER.start();
    }

    @DynamicPropertySource
    static void s3Properties(final DynamicPropertyRegistry registry) {
        registry.add("s3-properties.endpoint", () -> S3MockTestContainer.endpoint(S3_CONTAINER));
        registry.add("s3-properties.access-key", () -> S3MockTestContainer.ACCESS_KEY);
        registry.add("s3-properties.secret-key", () -> S3MockTestContainer.SECRET_KEY);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private S3Service s3Service;

    @Autowired
    private BucketResolver bucketResolver;

    private final HttpClient client = HttpClient.newHttpClient();

    private static String basic(final String user, final String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private URI uri(final String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** Generates content on the fly, so the test itself never holds the complete content. */
    private static InputStream generated(final long size) {
        return new InputStream() {
            private long pos;

            @Override
            public int read() {
                return pos < size ? 'a' + (int) (pos++ % 26) : -1;
            }

            @Override
            public int read(final byte[] b, final int off, final int len) {
                if (pos >= size) {
                    return -1;
                }
                final int n = (int) Math.min(len, size - pos);
                for (int i = 0; i < n; i++) {
                    b[off + i] = (byte) ('a' + (int) ((pos + i) % 26));
                }
                pos += n;
                return n;
            }
        };
    }

    private static String sha256(final InputStream in) throws IOException, NoSuchAlgorithmException {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (DigestInputStream din = new DigestInputStream(in, digest)) {
            din.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    @Test
    void bucketsAreCreatedOnStartup() {
        // act
        final var buckets = s3Service.listBuckets();
        // assert
        assertThat(bucketResolver.usedBucketNames()).hasSize(10).allSatisfy(b -> assertThat(buckets).contains(b));
    }

    @Test
    void requestWithoutCredentialsIsRejected() throws Exception {
        // arrange
        final HttpRequest request = HttpRequest.newBuilder(uri("/s3/download/scope/x")).GET().build();
        // act
        final HttpResponse<Void> response = client.send(request, BodyHandlers.discarding());
        // assert
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        // arrange
        final HttpRequest request = HttpRequest.newBuilder(uri("/s3/download/scope/x"))
            .header("Authorization", basic("user-a", "wrong")).GET().build();
        // act
        final HttpResponse<Void> response = client.send(request, BodyHandlers.discarding());
        // assert
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void uploadWithContentLengthAndDownload() throws Exception {
        // arrange
        final long size = 12L * MIB;
        final HttpRequest upload = HttpRequest.newBuilder(uri("/s3/upload/scope-1/big.bin"))
            .header("Authorization", basic("user-a", "change-me"))
            .header("Content-Type", "application/x-demo")
            .POST(BodyPublishers.fromPublisher(BodyPublishers.ofInputStream(() -> generated(size)), size))
            .build();
        // act
        final HttpResponse<String> uploadResponse = client.send(upload, BodyHandlers.ofString());
        final HttpResponse<InputStream> download = client.send(
            HttpRequest.newBuilder(uri("/s3/download/scope-1/big.bin"))
                .header("Authorization", basic("user-b", "change-me")).GET().build(),
            BodyHandlers.ofInputStream());
        final String downloadedHash = sha256(download.body());
        // assert
        assertThat(uploadResponse.statusCode()).isEqualTo(200);
        assertThat(uploadResponse.body()).contains("\"key\":\"scope-1/big.bin\"").contains("\"size\":" + size);
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.headers().firstValue("Content-Type")).contains("application/x-demo");
        assertThat(download.headers().firstValueAsLong("Content-Length")).hasValue(size);
        assertThat(downloadedHash).isEqualTo(sha256(generated(size)));
        assertThat(s3Service.headObject(bucketResolver.resolveBucket("scope-1"), "scope-1/big.bin")).isEqualTo(200);
    }

    @Test
    void uploadChunkedUsesMultipartUpload() throws Exception {
        // arrange
        final long size = 11L * MIB;
        final HttpRequest upload = HttpRequest.newBuilder(uri("/s3/upload/scope-2/chunked.bin"))
            .header("Authorization", basic("user-a", "change-me"))
            .header("Content-Type", "application/octet-stream")
            .POST(BodyPublishers.ofInputStream(() -> generated(size)))
            .build();
        // act
        final HttpResponse<String> uploadResponse = client.send(upload, BodyHandlers.ofString());
        final HttpResponse<InputStream> download = client.send(
            HttpRequest.newBuilder(uri("/s3/download/scope-2/chunked.bin"))
                .header("Authorization", basic("user-a", "change-me")).GET().build(),
            BodyHandlers.ofInputStream());
        // assert
        assertThat(uploadResponse.statusCode()).isEqualTo(200);
        assertThat(uploadResponse.body()).contains("\"size\":" + size);
        assertThat(sha256(download.body())).isEqualTo(sha256(generated(size)));
    }

    @Test
    void downloadOfMissingObjectIsNotFound() throws Exception {
        // arrange
        final HttpRequest request = HttpRequest.newBuilder(uri("/s3/download/scope-3/missing"))
            .header("Authorization", basic("user-a", "change-me")).GET().build();
        // act
        final HttpResponse<Void> response = client.send(request, BodyHandlers.discarding());
        // assert
        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void multipartUploadWithTwoFiles() throws Exception {
        // arrange
        final long bigSize = 6L * MIB;
        final String head1 = "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"first.txt\"; filename=\"a.txt\"\r\n"
            + "Content-Type: text/plain\r\n\r\n";
        final String head2 = "\r\n--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"second.bin\"; filename=\"b.bin\"\r\n"
            + "Content-Type: application/x-big\r\n\r\n";
        final String tail = "\r\n--" + BOUNDARY + "--\r\n";
        final HttpRequest upload = HttpRequest.newBuilder(uri("/s3/multipart-upload/scope-4"))
            .header("Authorization", basic("user-a", "change-me"))
            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
            .POST(BodyPublishers.ofInputStream(() -> new SequenceInputStream(java.util.Collections.enumeration(java.util.List.of(
                new ByteArrayInputStream(head1.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)),
                new ByteArrayInputStream(head2.getBytes(StandardCharsets.UTF_8)),
                generated(bigSize),
                new ByteArrayInputStream(tail.getBytes(StandardCharsets.UTF_8)))))))
            .build();
        // act
        final HttpResponse<String> response = client.send(upload, BodyHandlers.ofString());
        final HttpResponse<String> first = client.send(
            HttpRequest.newBuilder(uri("/s3/download/scope-4/first.txt"))
                .header("Authorization", basic("user-a", "change-me")).GET().build(),
            BodyHandlers.ofString());
        final HttpResponse<InputStream> second = client.send(
            HttpRequest.newBuilder(uri("/s3/download/scope-4/second.bin"))
                .header("Authorization", basic("user-a", "change-me")).GET().build(),
            BodyHandlers.ofInputStream());
        // assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("scope-4/first.txt").contains("scope-4/second.bin");
        assertThat(first.body()).isEqualTo("hello");
        assertThat(first.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).startsWith("text/plain"));
        assertThat(second.headers().firstValue("Content-Type")).contains("application/x-big");
        assertThat(sha256(second.body())).isEqualTo(sha256(generated(bigSize)));
    }

    private HttpResponse<String> call(final String method, final String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path))
            .header("Authorization", basic("user-a", "change-me"))
            .method(method, BodyPublishers.noBody()).build(), BodyHandlers.ofString());
    }

    private void putObject(final String scope, final String objectId) throws Exception {
        final HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/s3/upload/" + scope + "/" + objectId))
            .header("Authorization", basic("user-a", "change-me"))
            .header("Content-Type", "text/plain")
            .POST(BodyPublishers.ofString("content")).build(), BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void headObject() throws Exception {
        // arrange
        putObject("scope-5", "exists.txt");
        // act
        final HttpResponse<String> found = call("HEAD", "/s3/head/scope-5/exists.txt");
        final HttpResponse<String> notFound = call("HEAD", "/s3/head/scope-5/missing.txt");
        // assert
        assertThat(found.statusCode()).isEqualTo(200);
        assertThat(notFound.statusCode()).isEqualTo(404);
    }

    @Test
    void deleteObject() throws Exception {
        // arrange
        putObject("scope-6", "to-delete.txt");
        // act
        final HttpResponse<String> deleted = call("DELETE", "/s3/delete/scope-6/to-delete.txt");
        // assert
        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(call("HEAD", "/s3/head/scope-6/to-delete.txt").statusCode()).isEqualTo(404);
        assertThat(call("GET", "/s3/download/scope-6/to-delete.txt").statusCode()).isEqualTo(404);
    }

    @Test
    void listObjects() throws Exception {
        // arrange
        putObject("scope-7", "a-1.txt");
        putObject("scope-7", "a-2.txt");
        putObject("scope-7", "b-1.txt");
        // act
        final HttpResponse<String> all = call("GET", "/s3/list/scope-7");
        final HttpResponse<String> withPrefix = call("GET", "/s3/list/scope-7?prefix=a-");
        final HttpResponse<String> empty = call("GET", "/s3/list/scope-unknown");
        // assert
        assertThat(all.statusCode()).isEqualTo(200);
        assertThat(all.body()).contains("scope-7/a-1.txt", "scope-7/a-2.txt", "scope-7/b-1.txt");
        assertThat(withPrefix.body()).contains("scope-7/a-1.txt", "scope-7/a-2.txt").doesNotContain("b-1.txt");
        assertThat(empty.body()).contains("\"keys\":[]");
    }

    @Test
    void newEndpointsRequireAuthentication() throws Exception {
        // act
        final HttpResponse<String> response = client.send(
            HttpRequest.newBuilder(uri("/s3/list/scope-7")).GET().build(), BodyHandlers.ofString());
        // assert
        assertThat(response.statusCode()).isEqualTo(401);
    }
}
