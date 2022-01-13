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
import com.github.tsc4j.core.Tsc4jException;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.SystemPropertyCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.github.tsc4j.core.Tsc4jImplUtils.optString;
import static com.github.tsc4j.core.Tsc4jImplUtils.validString;

/**
 * AWS SDK 2.x utilities.
 */
@Slf4j
@UtilityClass
public class AwsSdk2Utils {
    private Optional<AwsCredentialsProvider> customCredentialsProvider(AwsConfig config) {
        val type = config.getCredentialsProvider();
        switch (type) {
            case anonymous:
                return Optional.of(AnonymousCredentialsProvider.create());
            case sysprops:
                return Optional.of(SystemPropertyCredentialsProvider.create());
            case env:
                return Optional.of(EnvironmentVariableCredentialsProvider.create());
        }
        return Optional.empty();
    }

    private Optional<AwsCredentialsProvider> staticCredentialsProvider(AwsConfig config) {
        val accessKey = validString(config.getAccessKeyId());
        val secretKey = validString(config.getSecretAccessKey());
        if (!(accessKey.isEmpty() || secretKey.isEmpty())) {
            val credentials = AwsBasicCredentials.create(accessKey, secretKey);
            return Optional.of(StaticCredentialsProvider.create(credentials));
        }
        return Optional.empty();
    }

    public AwsCredentialsProvider credentialsProvider(@NonNull AwsConfig config) {
        return customCredentialsProvider(config)
            .map(Optional::of)
            .orElseGet(() -> staticCredentialsProvider(config))
            .orElseGet(DefaultCredentialsProvider::create);
    }

    public Optional<URI> endpointUri(@NonNull AwsConfig config) {
        val endpoint = config.getEndpoint();
        try {
            return optString(endpoint)
                .map(it -> URI.create(it))
                .map(AwsSdk2Utils::checkURI);
        } catch (Exception e) {
            throw Tsc4jException.of("Invalid AWS endpoint URI: '%s'", e, endpoint);
        }
    }

    @SneakyThrows
    private URI checkURI(URI uri) {
        if (uri.toURL() == null) {
            throw new IllegalArgumentException("URI resolves to empty URL, this should not happen.");
        }
        return uri;
    }

    public Optional<Region> region(@NonNull AwsConfig config) {
        val region = config.getRegion();
        try {
            return optString(region)
                .map(Region::of);
        } catch (Exception e) {
            throw Tsc4jException.of("Invalid AWS region: '%s'", e, region);
        }
    }

    /**
     * Applies common configuration of a AWS client builder.
     *
     * @param builder   aws client builder
     * @param awsConfig aws configuration
     * @return configured builder
     */
    public <C, T extends AwsClientBuilder<T, C>> T configureClientBuilder(@NonNull T builder,
                                                                          @NonNull AwsConfig awsConfig) {
        log.trace("[{}] customizing aws sdk 2.x client builder with config: {}", builder, awsConfig);
        AwsSdk2Utils.endpointUri(awsConfig)
            .ifPresent(it -> {
                log.debug("[{}]: adding custom AWS endpoint: {}", builder, it);
                builder.endpointOverride(it);
            });

        AwsSdk2Utils.region(awsConfig)
            .ifPresent(it -> {
                log.debug("[{}]: applying custom AWS region: {}", builder, it);
                builder.region(it);

            });

        return builder
            .credentialsProvider(AwsSdk2Utils.credentialsProvider(awsConfig))
            .overrideConfiguration(clientOverrideConfiguration(awsConfig));
    }

    /**
     * Creates AWS SDK 2.x client with customizations from a given aws config.
     *
     * @param builderSupplier SDK builder supplier.
     * @param awsConfig       AWS config
     * @param <T>             AWS SDK client type
     * @param <B>             AWS SDK client builder type
     * @return configured AWS SDK 2.x client
     */
    public <T, B extends AwsClientBuilder<B, T>> T configuredClient(@NonNull Supplier<B> builderSupplier,
                                                                    @NonNull AwsConfig awsConfig) {
        return configuredClient(builderSupplier, awsConfig, Function.identity());

    }

    /**
     * Creates AWS SDK 2.x client with customizations from a given aws config.
     *
     * @param builderSupplier   SDK builder supplier.
     * @param awsConfig         AWS config
     * @param builderCustomizer additional SDK client builder customizer that allows additional customization
     * @param <T>               AWS SDK client type
     * @param <B>               AWS SDK client builder type
     * @return configured AWS SDK 2.x client
     */
    public <T, B extends AwsClientBuilder<B, T>> T configuredClient(@NonNull Supplier<B> builderSupplier,
                                                                    @NonNull AwsConfig awsConfig,
                                                                    Function<B, B> builderCustomizer) {
        // create a builder
        val builder = Objects.requireNonNull(
            builderSupplier.get(), "AWS SDK 2.x builder supplier returned null.");

        // implement basic configuration
        configureClientBuilder(builder, awsConfig);

        // apply sdk builder customizer
        val customizedBuilder = Objects.requireNonNull(
            builderCustomizer.apply(builder),
            "AWS SDK 2.x builder customizer of " + builder + " returned null.");

        val client = customizedBuilder.build();
        log.debug("created AWS SDK 2.x client: {}", client);

        return client;
    }

    /**
     * Returns SDK client override configuration from a given aws config
     *
     * @param awsConfig aws config
     * @return sdk client config
     */
    ClientOverrideConfiguration clientOverrideConfiguration(@NonNull AwsConfig awsConfig) {
        val b = ClientOverrideConfiguration.builder();

        // GZIP is not supported in AWS SDK 2.x
        // https://github.com/aws/aws-sdk-java-v2/blob/master/docs/LaunchChangelog.md#133-client-override-configuration

        // api call timeout
        if (awsConfig.getTimeout().toMillis() > 0) {
            b.apiCallTimeout(awsConfig.getTimeout());
        }

        // maxConnections is a http client builder option, not applicable here

        // max retries
        if (awsConfig.getMaxErrorRetry() > 0) {
            val retryPolicy = RetryPolicy.builder()
                .numRetries(awsConfig.getMaxErrorRetry())
                .build();
            b.retryPolicy(retryPolicy);
        }

        return b.build();
    }

}
