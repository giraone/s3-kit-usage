# s3kit-demo

Spring Boot demo for the [s3-kit library](https://github.com/giraone/s3-kit/README.md) library.

## Build and run

Install the s3-kit library first (`mvn clean install` in the parent directory) or use a released one from Maven Central.

Tests: `mvn clean verify` (Testcontainers with `adobe/s3mock:5.2.3`).

Run:

- Run S3 service (*rustfs*) using *docker compose* locally on port 9000: `cd docker; docker compose -f docker-compose-services-rustfs.yml up -d; cd -`
- `mvn spring-boot:run` (needs an S3 service endpoint, default `http://localhost:9090`)

The *rustfs* S3 service has an admin UI on `http://localhost:9001` with user/password `minioadmin`.

## API (basic authentication, users `user-a` and `user-b`, password `change-me`)

The S3 key is `{scope}/{objectId}`.

| Method and path                        | Description                                                                                                                      |
|----------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| `POST /s3/upload/{scope}/{objectId}`   | Body is streamed to S3. Content type and length from the headers.<br>Without `Content-Length` (chunked) a multipart upload is used. |
| `GET /s3/download/{scope}/{objectId}`  | Streams the object to the client.                                                                                                |
| `HEAD /s3/head/{scope}/{objectId}`     | 200 if the object exists, 404 otherwise.                                                                                         |
| `DELETE /s3/delete/{scope}/{objectId}` | Deletes the object (204).                                                                                                        |
| `GET /s3/list/{scope}[?prefix=]`       | Lists the keys of the scope,<br>optionally restricted to objectIds starting with `prefix` (JSON `{bucket, keys}`).                  |
| `POST /s3/multipart-upload/{scope}`    | `multipart/form-data`; the part `name` is the `objectId`.<br>Content type and size from the part.                                   |

```
curl -u user-a:change-me -H 'Content-Type: text/plain' --data-binary @file.txt localhost:8080/s3/upload/my-scope/file.txt
curl -u user-a:change-me -o out.txt localhost:8080/s3/download/my-scope/file.txt
curl -u user-a:change-me -F 'file.txt=@file.txt' -F 'other.bin=@other.bin' localhost:8080/s3/multipart-upload/my-scope
```

## Buckets

The demo creates 10 buckets (see [BucketMaintainer.java](src/main/java/com/giraone/s3kit/demo/service/BucketMaintainer.java).
The objects mapped into these buckets (see [DefaultJobToBucketMapper.java](src/main/java/com/giraone/s3kit/demo/buckets/DefaultJobToBucketMapper.java).

## Large files

Nothing is held completely in memory: raw uploads stream from the request, multipart files are spooled to
temporary files by Tomcat (threshold 1 MB), downloads stream from S3 to the response.

## Curl tests

`src/test/curl` contains manual end-to-end tests with curl for all endpoints (needs a running demo and S3):

```
src/test/curl/run-tests.sh            # BASE_URL, AUTH_USER, AUTH_PASSWORD can be overridden
```

`lib.sh` is a reusable helper library (`request`, `header`, `check_status`, `check_eq`, `check_contains`,
`check_same_file`, `make_file`, `wait_for_server`, `summary`) for further manual tests. `run-tests.sh` reports
PASS/FAIL per check and exits with a non-zero code on failures. The example files in `src/test/curl/resources/`
(text, JSON, PNG, 1 MiB binary) are used; a 12 MiB file is generated per run.
