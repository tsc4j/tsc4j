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

package com.github.tsc4j.aws.sdk2

import com.github.tsc4j.aws.common.AwsConfig
import com.github.tsc4j.core.Tsc4jException
import com.github.tsc4j.core.impl.Stopwatch
import com.typesafe.config.ConfigFactory
import groovy.util.logging.Slf4j
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.awscore.client.config.AwsClientOption
import software.amazon.awssdk.core.client.config.SdkClientOption
import software.amazon.awssdk.services.s3.DefaultS3Client
import software.amazon.awssdk.services.s3.S3Client
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

@Slf4j
@Unroll
class AwsSdk2UtilsSpec extends Specification {
    @Shared
    def customizerInvocations = new AtomicInteger()

    def setup() {
        customizerInvocations.set(0)
    }

    def "credentialsProvider() should return default credentials provider if both keys are not specified"() {
        given:
        def awsConfig = new AwsConfig().setAccessKeyId(accessKey).setSecretAccessKey(secretKey)

        when:
        def provider = AwsSdk2Utils.credentialsProvider(awsConfig)

        then:
        provider != null
        provider instanceof DefaultCredentialsProvider

        where:
        accessKey | secretKey
        null      | null
        ""        | null
        null      | ""
        " "       | null
        null      | " "
        " "       | "   "
    }

    def "credentialsProvider() should return static credentials provider if both keys are specified"() {
        given:
        def awsConfig = new AwsConfig().setAccessKeyId(accessKey)
                                       .setSecretAccessKey(secretKey)

        when:
        def provider = AwsSdk2Utils.credentialsProvider(awsConfig)

        then:
        provider != null
        provider instanceof StaticCredentialsProvider

        when:
        def credentials = provider.resolveCredentials()

        then:
        credentials instanceof AwsBasicCredentials

        where:
        accessKey | secretKey
        "a"       | " b"
        "a"       | " b"
    }

    def "region() should return empty optional for invalid input"() {
        given:
        def awsConfig = new AwsConfig().setRegion(region)

        expect:
        !AwsSdk2Utils.region(awsConfig).isPresent()

        where:
        region << [null, "", " ", "  "]
    }

    def "region() should return supplied region for valid input"() {
        given:
        def awsConfig = new AwsConfig().setRegion(region)

        when:
        def regionOpt = AwsSdk2Utils.region(awsConfig)

        then:
        regionOpt.isPresent()
        regionOpt.get().toString() == region.trim()

        where:
        region << ["us-west-1", " foo-region "]
    }

    def "credentialsProvider() should return default provider chain if not all props are set"() {
        // don't run test if both keys are set
        if (accessKey?.trim()?.length() > 0 && secretKey?.trim()?.length()) {
            return
        }

        given:
        def awsConfig = new AwsConfig().setAccessKeyId(accessKey)
                                       .setSecretAccessKey(secretKey)
        when:
        def provider = AwsSdk2Utils.credentialsProvider(awsConfig)

        then:
        provider instanceof DefaultCredentialsProvider

        where:
        [accessKey, secretKey] << [[null, " ", "accessKey"], [null, " ", "secretKey"]].combinations()
    }

    def "credentialsProvider() should basic credentials provider if both keys are set"() {
        given:
        def accessKey = "foo"
        def secretKey = "bar"
        def awsConfig = new AwsConfig().setAccessKeyId(accessKey)
                                       .setSecretAccessKey(secretKey)

        when:
        def provider = AwsSdk2Utils.credentialsProvider(awsConfig)

        then:
        provider instanceof StaticCredentialsProvider

        def credentials = provider.resolveCredentials()
        credentials.accessKeyId() == accessKey
        credentials.secretAccessKey() == secretKey
    }

    def "region() should return empty region optional if no region is set"() {
        given:
        def awsConfig = new AwsConfig().setRegion(region)

        expect:
        !AwsSdk2Utils.region(awsConfig).isPresent()

        where:
        region << [null, "", "  "]
    }

    def "region() should return static region if region is set"() {
        given:
        def config = new AwsConfig().setRegion(region)

        when:
        def regionOpt = AwsSdk2Utils.region(config)

        then:
        regionOpt.isPresent()
        regionOpt.get().toString() == region.trim()

        where:
        region << ["us-west-1", "   us-west-2 ", " fooRegion"]
    }

    def "endpointUri() should return empty optional when endpoint is not defined or empty"() {
        given:
        def awsConfig = new AwsConfig().setEndpoint(endpoint)

        expect:
        !AwsSdk2Utils.endpointUri(awsConfig).isPresent()

        where:
        endpoint << [null, "", "    "]
    }

    def "endpointUri() should throw in case of badly formatted endpoint uri"() {
        given:
        def awsConfig = new AwsConfig().setEndpoint(endpoint)

        when:
        def uriOpt = AwsSdk2Utils.endpointUri(awsConfig)

        then:
        thrown(Tsc4jException)
        uriOpt == null

        where:
        endpoint << [" foo ", "foobar://blah/co    "]
    }

    def "endpointUri() should return optional with a configured endpoint endpoint"() {
        given:
        def endpoint = 'http://foo.example.com:9090/'
        def awsConfig = new AwsConfig().setEndpoint(endpoint)

        when:
        def uriOpt = AwsSdk2Utils.endpointUri(awsConfig)

        then:
        uriOpt.isPresent()
        uriOpt.get().toString() == endpoint
    }

    def "configuredClient() should create client with expected configuration"() {
        given:
        def endpoint = 'http://localhost:9090/'
        def region = 'us-west-5'
        def timeout = Duration.ofSeconds(42)
        def maxConn = 667
        def maxErrRetry = 7
        def gzip = true

        def cfgMap = [
            'endpoint'       : endpoint,
            'region'         : region,
            'gzip'           : gzip,
            'timeout'        : timeout,
            'max-connections': maxConn,
            'max-error-retry': maxErrRetry
        ]
        def cfg = ConfigFactory.parseMap(cfgMap)

        when: "create AwsConfig and configure it using lightbend config instance"
        def awsConfig = new AwsConfig()
        awsConfig.withConfig(cfg)

        then:
        with(awsConfig) {
            getEndpoint() == endpoint
            getRegion() == region
            isGzip() == gzip
            getTimeout() == timeout
            getMaxConnections() == maxConn
            getMaxErrorRetry() == maxErrRetry
        }

        when: "configure example create configured client"
        def sw = new Stopwatch()
        def s3Client = (customizer) ?
            AwsSdk2Utils.configuredClient({ S3Client.builder() }, awsConfig, customizer)
            :
            AwsSdk2Utils.configuredClient({ S3Client.builder() }, awsConfig)
        log.info("created client in {}: {}", sw, s3Client)

        then:
        s3Client instanceof DefaultS3Client
        if (customizer) {
            // make sure that builder customizer has been invoked
            assert customizerInvocations.get() == 1
        }

        when: "extract client configuration"
        Map attrMap = s3Client.clientConfiguration.attributes.attributes

        def sb = new StringBuilder()
        attrMap.each { sb.append("  " + it.key + "  => " + it.value + "\n") }
        log.info("SDK client options:\n{}", sb)

        then:
        attrMap.get(SdkClientOption.ENDPOINT) == URI.create(endpoint)
        attrMap.get(AwsClientOption.CREDENTIALS_PROVIDER) instanceof DefaultCredentialsProvider
        attrMap.get(AwsClientOption.AWS_REGION).toString() == region
        attrMap.get(AwsClientOption.SIGNING_REGION).toString() == region

        attrMap.get(SdkClientOption.API_CALL_TIMEOUT) == timeout
        attrMap.get(SdkClientOption.RETRY_POLICY).numRetries() == maxErrRetry
        attrMap.get(AwsClientOption.DUALSTACK_ENDPOINT_ENABLED) == null

        cleanup:
        s3Client?.close()

        where:
        customizer << [
            null, // no customizer
            { customizerInvocations.incrementAndGet(); it }
        ]
    }
}
