#!/usr/bin/env bash
# Curl tests for all endpoints of the s3kit demo. Requires a running demo application.
# Usage: BASE_URL=http://localhost:8080 ./run-tests.sh
set -u
cd "$(dirname "${BASH_SOURCE[0]}")" || exit 1
# shellcheck source=lib.sh
source ./lib.sh

wait_for_server 30 || exit 2

SCOPE="curl-$(date +%s)-$$"
make_file "$WORK_DIR/large.bin" $((12 * 1024 * 1024))

echo "== authentication"
AUTH_USER=- request GET "/s3/download/$SCOPE/small.txt"
check_status "download without credentials is rejected" 401
AUTH_PASSWORD=wrong request GET "/s3/download/$SCOPE/small.txt"
check_status "download with wrong password is rejected" 401

echo "== upload and download with content type and length"
for spec in "small.txt:text/plain" "data.json:application/json" "image.png:image/png" "medium.bin:application/octet-stream"; do
  file="${spec%%:*}"; type="${spec#*:}"
  request POST "/s3/upload/$SCOPE/$file" -H "Content-Type: $type" --data-binary "@$RESOURCES/$file"
  check_status "upload $file" 200
  check_contains "upload $file response has key" "\"key\":\"$SCOPE/$file\"" "$(cat "$LAST_BODY")"
  check_contains "upload $file response has size" "\"size\":$(wc -c < "$RESOURCES/$file" | tr -d ' ')" "$(cat "$LAST_BODY")"
  request GET "/s3/download/$SCOPE/$file"
  check_status "download $file" 200
  check_contains "download $file content type" "$type" "$(header Content-Type)"
  check_eq "download $file content length" "$(wc -c < "$RESOURCES/$file" | tr -d ' ')" "$(header Content-Length)"
  check_same_file "download $file content" "$RESOURCES/$file" "$LAST_BODY"
done

echo "== large file (12 MiB), other user"
request POST "/s3/upload/$SCOPE/large.bin" -H "Content-Type: application/octet-stream" --data-binary "@$WORK_DIR/large.bin"
check_status "upload large.bin" 200
AUTH_USER=user-b request GET "/s3/download/$SCOPE/large.bin"
check_status "download large.bin as user-b" 200
check_same_file "download large.bin content" "$WORK_DIR/large.bin" "$LAST_BODY"

echo "== chunked upload (unknown length, multipart upload in S3)"
request POST "/s3/upload/$SCOPE/chunked.bin" -H "Content-Type: application/octet-stream" -H "Transfer-Encoding: chunked" --data-binary "@$WORK_DIR/large.bin"
check_status "chunked upload" 200
check_contains "chunked upload size" "\"size\":$((12 * 1024 * 1024))" "$(cat "$LAST_BODY")"
request GET "/s3/download/$SCOPE/chunked.bin"
check_same_file "chunked upload content" "$WORK_DIR/large.bin" "$LAST_BODY"

echo "== multipart/form-data upload (object id = part name)"
request POST "/s3/multipart-upload/$SCOPE" \
  -F "form-small.txt=@$RESOURCES/small.txt;type=text/plain" \
  -F "form-image.png=@$RESOURCES/image.png;type=image/png" \
  -F "form-large.bin=@$WORK_DIR/large.bin;type=application/octet-stream"
check_status "multipart upload" 200
check_contains "multipart response has first key" "$SCOPE/form-small.txt" "$(cat "$LAST_BODY")"
for spec in "form-small.txt:$RESOURCES/small.txt:text/plain" "form-image.png:$RESOURCES/image.png:image/png" "form-large.bin:$WORK_DIR/large.bin:application/octet-stream"; do
  IFS=: read -r id file type <<< "$spec"
  request GET "/s3/download/$SCOPE/$id"
  check_status "download $id" 200
  check_contains "download $id content type" "$type" "$(header Content-Type)"
  check_same_file "download $id content" "$file" "$LAST_BODY"
done

echo "== head object"
request HEAD "/s3/head/$SCOPE/small.txt" -I
check_status "head of existing object" 200
request HEAD "/s3/head/$SCOPE/does-not-exist" -I
check_status "head of missing object" 404

echo "== list objects"
request GET "/s3/list/$SCOPE"
check_status "list all objects of scope" 200
check_contains "list contains small.txt" "$SCOPE/small.txt" "$(cat "$LAST_BODY")"
check_contains "list contains form-image.png" "$SCOPE/form-image.png" "$(cat "$LAST_BODY")"
request GET "/s3/list/$SCOPE?prefix=form-"
check_status "list with prefix" 200
check_contains "list with prefix contains form-small.txt" "$SCOPE/form-small.txt" "$(cat "$LAST_BODY")"
if grep -q "$SCOPE/small.txt" "$LAST_BODY"; then fail "list with prefix excludes other objects"; else pass "list with prefix excludes other objects"; fi
request GET "/s3/list/$SCOPE-unknown"
check_contains "list of unknown scope is empty" '"keys":[]' "$(cat "$LAST_BODY")"
AUTH_USER=- request GET "/s3/list/$SCOPE"
check_status "list without credentials is rejected" 401

echo "== delete object"
request DELETE "/s3/delete/$SCOPE/small.txt"
check_status "delete object" 204
request HEAD "/s3/head/$SCOPE/small.txt" -I
check_status "head of deleted object" 404
request GET "/s3/download/$SCOPE/small.txt"
check_status "download of deleted object" 404
request GET "/s3/list/$SCOPE"
if grep -q "$SCOPE/small.txt" "$LAST_BODY"; then fail "deleted object not listed"; else pass "deleted object not listed"; fi

echo "== errors"
request GET "/s3/download/$SCOPE/does-not-exist"
check_status "download of missing object" 404

summary
