/*
 * Copyright 2017 - 2021 tsc4j project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.tsc4j.aws.sdk2;

import com.github.tsc4j.aws.common.AwsConfig;
import com.github.tsc4j.aws.common.WithAwsConfig;
import com.github.tsc4j.core.ConfigQuery;
import com.github.tsc4j.core.ConfigSource;
import com.github.tsc4j.core.FilesystemLikeConfigSource;
import com.github.tsc4j.core.Tsc4jCache;
import com.github.tsc4j.core.Tsc4jException;
import com.github.tsc4j.core.Tsc4jImplUtils;
import com.github.tsc4j.core.WithCache;
import com.github.tsc4j.core.utils.CollectionUtils;
import com.typesafe.config.Config;
import lombok.Getter;
import lombok.NonNull;
import lombok.val;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.Reader;
import java.io.StringReader;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <a href="https://aws.amazon.com/s3/">Amazon S3</a> implementation of {@link ConfigSource}.
 */
public final class S3ConfigSource
    extends FilesystemLikeConfigSource<Map<String, S3Object>>
    implements WithCache<String, Config> {
    private static final String S3_URL_PREFIX = "s3://";
    private static final Pattern S3_URL_PATTERN = Pattern.compile("^" + S3_URL_PREFIX + "([\\w\\-\\.]+)/(.*)");

    static final String TYPE = "aws2.s3";
    static final Set<String> TYPE_ALIASES = CollectionUtils.toImmutableSet("aws.s3", "s3");

    /**
     * S3 client in use
     */
    private final S3Client s3Client;

    @Getter
    private final Tsc4jCache<String, Config> cache;

    /**
     * Creates new instance.
     */
    protected S3ConfigSource(@NonNull Builder builder) {
        this(builder, createS3Client(builder));
    }

    /**
     * Creates new instance.
     *
     * @param builder  builder
     * @param s3Client s3 client
     */
    protected S3ConfigSource(@NonNull Builder builder, @NonNull S3Client s3Client) {
        super(builder);
        this.s3Client = s3Client;
        this.cache = Tsc4jImplUtils.newCache(toString(), builder.getCacheTtl(), builder.getClock());
    }

    /**
     * Creates s3 client
     *
     * @param builder builder
     * @return s3 client
     */
    protected static S3Client createS3Client(@NonNull Builder builder) {
        val awsConfig = builder.getAwsConfig();
        return AwsSdk2Utils.configuredClient(S3Client::builder, awsConfig, b -> s3BuilderCustomizer(b, awsConfig));
    }

    private static S3ClientBuilder s3BuilderCustomizer(S3ClientBuilder b, AwsConfig awsConfig) {
        val s3Config = S3Configuration.builder()
            .pathStyleAccessEnabled(awsConfig.getS3PathStyleAccess())
            .build();
        return b.serviceConfiguration(s3Config);
    }

    /**
     * Creates new instance builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    protected void doClose() {
        super.doClose();
        s3Client.close();
    }

    @Override
    protected boolean sanitizePathFilter(@NonNull String path) {
        if (!path.startsWith(S3_URL_PREFIX)) {
            log.warn("{} invalid S3 config path [syntax: 's3://bucket_name/path']: '{}'", this, path);
            return false;
        }
        return super.sanitizePathFilter(path);
    }

    @Override
    protected boolean removeDoubleSlashesFromConfigNames() {
        return false;
    }

    @Override
    protected Map<String, S3Object> createFetchContext(@NonNull ConfigQuery query) {
        val tasks = createFetchContextTasks(query);
        val results = runTasks(tasks, isParallel());

        val map = new LinkedHashMap<String, S3Object>();
        results.forEach(e -> map.putAll(e));
        return map;
    }

    /**
     * Creates list of tasks that need to be performed to create a fetch context for given query
     *
     * @param query query
     * @return list of tasks
     */
    private List<Callable<Map<String, S3Object>>> createFetchContextTasks(@NonNull ConfigQuery query) {
        return interpolateVarStrings(getPaths(), query).stream()
            .flatMap(this::restrictS3Path)
            .distinct()
            .map(s3Url -> (Callable<Map<String, S3Object>>) () -> fetchSummary(s3Url))
            .collect(Collectors.toList());
    }

    private Stream<String> restrictS3Path(@NonNull String path) {
        if (isConfdEnabled()) {
            return Stream.of(path + "/application.conf", path + "/" + CONF_D_DIR);
        } else {
            return Stream.of(path + "/application.conf");
        }
    }

    @Override
    protected Config loadConfig(String path, Map<String, S3Object> context) {
        val etag = Optional.ofNullable(context.get(path))
            .map(e -> e.eTag())
            .orElse("");

        // load from cache if path/etag matches
        val cacheKey = cacheKey(path, etag);
        return getFromCache(cacheKey)
            .orElseGet(() -> {
                val config = super.loadConfig(path, context);
                // put to cache with latest etag
                return putToCache(cacheKey, config);
            });
    }

    /**
     * Fetches summary for s3Url and returns map of all objects.
     *
     * @param s3Url s3 url with path
     * @return map of object summaries; empty map is returned if s3 url or bucket doesn't exist.
     * @throws RuntimeException if there was a problem fetching object summaries.
     * @see #createFetchContext(ConfigQuery)
     */
    private Map<String, S3Object> fetchSummary(String s3Url) {
        val map = new LinkedHashMap<String, S3Object>();

        try {
            val bucketName = bucketName(s3Url);
            val request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(bucketPath(s3Url))
                .build();
            s3Client.listObjectsV2(request)
                .contents()
                .stream()
                .sorted(Comparator.comparing(S3Object::key))
                .forEach(it -> {
                    val url = S3_URL_PREFIX + bucketName + "/" + it.key();
                    map.put(url, it);
                });
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                warnOrThrowOnMissingConfigLocation(s3Url);
                log.debug("{} non-existing s3 bucket path: {}: {}", this, s3Url, e.getMessage());
            } else {
                throw Tsc4jException.of("Error fetching summary for %s: %%s", e, s3Url);
            }
        } catch (Exception e) {
            throw Tsc4jException.of("Error fetching summary for %s: %%s", e, s3Url);
        }

        if (log.isDebugEnabled()) {
            if (map.isEmpty()) {
                log.debug("{} empty fetch summary for: {}", this, s3Url);
            } else {
                val sb = new StringBuilder();
                map.forEach((k, v) -> sb.append("  " + k + " -> " + v + "\n"));
                log.debug("{} fetch summary for {}:\n  {}", this, s3Url, sb.toString().trim());
            }
        }

        return map;
    }

    @Override
    protected Optional<Reader> openConfig(@NonNull String s3Url, Map<String, S3Object> context) {
        log.debug("{} opening config: {}", this, s3Url);
        try {
            val req = GetObjectRequest.builder()
                .bucket(bucketName(s3Url))
                .key(bucketPath(s3Url))
                .build();

            return Optional.ofNullable(s3Client.getObjectAsBytes(req))
                .map(it -> it.asUtf8String())
                .filter(it -> !it.isEmpty())
                .map(StringReader::new);
        } catch (Exception e) {
            throw Tsc4jException.of("Error loading config %s: %%s", e, s3Url);
        }
    }

    @Override
    protected boolean isDirectory(String s3Url, Map<String, S3Object> context) {
        val result = context.keySet().stream()
            .anyMatch(e -> e.startsWith(s3Url + "/"));
        return debugIsDirectory(s3Url, result);
    }

    @Override
    protected boolean pathExists(String s3Url, Map<String, S3Object> context) {
        val result = context.keySet().stream()
            .anyMatch(e -> e.startsWith(s3Url));
        return debugPathExists(s3Url, result);
    }

    @Override
    protected Stream<String> listDirectory(String s3Url, Map<String, S3Object> context) {
        val prefix = s3Url + "/";
        return context.keySet().stream()
            .filter(e -> e.startsWith(prefix))
            .map(e -> e.replace(s3Url, "").substring(1))
            .filter(e -> !e.isEmpty());
    }

    private Matcher s3UrlMatcher(@NonNull String s3Url) {
        val matcher = S3_URL_PATTERN.matcher(s3Url);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Bad S3 URL: " + s3Url);
        }
        return matcher;
    }

    /**
     * Extracts bucket name from a s3 url
     */
    private String bucketName(@NonNull String s3Url) {
        return s3UrlMatcher(s3Url).group(1);
    }

    /**
     * Extracts bucket name from a s3 url
     *
     * @param s3Url s3 object url in a form of {@code s3://bucketName/some/path}
     * @return s3 bucket name
     * @throws NullPointerException     in case of null arguments
     * @throws IllegalArgumentException if {@code s3Url} is not a valid s3 url
     */
    private String bucketPath(@NonNull String s3Url) {
        return s3UrlMatcher(s3Url).group(2);
    }

    private String cacheKey(String path, String etag) {
        return path + "|" + etag;
    }

    /**
     * Builder for {@link S3ConfigSource}.
     */
    public static class Builder extends FilesystemLikeConfigSource.Builder<Builder> implements WithAwsConfig<Builder> {
        @Getter
        private final AwsConfig awsConfig = new AwsConfig();

        @Override
        protected Duration defaultCacheTtl() {
            return Duration.ofDays(7);
        }

        @Override
        public void withConfig(@NonNull Config cfg) {
            super.withConfig(cfg);

            getAwsConfig().withConfig(cfg);
        }

        @Override
        public ConfigSource build() {
            return new S3ConfigSource(this);
        }
    }
}
