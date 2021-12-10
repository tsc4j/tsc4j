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

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.github.tsc4j.core.AbstractConfigSource
import com.github.tsc4j.core.ConfigQuery
import com.github.tsc4j.core.ConfigSourceBuilder
import com.github.tsc4j.core.FilesystemLikeConfigSourceSpec
import com.github.tsc4j.core.Tsc4jImplUtils
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import groovy.util.logging.Slf4j
import io.findify.s3mock.S3Mock
import software.amazon.awssdk.services.s3.S3Client
import spock.lang.Shared
import spock.lang.Unroll

import java.time.Clock
import java.time.Duration

@Unroll
@Slf4j
class S3ConfigSourceSpec extends FilesystemLikeConfigSourceSpec {
    static final int S3_PORT = 8001
    static final def S3_ENDPOINT = "http://localhost:$S3_PORT"
    static final def S3_REGION = "wonderland-42"
    static final def bucketName = "my-bucket-a"

    @Shared
    S3Mock s3Mock

    def setupSpec() {
        def url = getClass().getResource("/aws-bundled-config")
        def s3BaseDir = url.getFile()
        log.info("aws bundled config directory: {}", s3BaseDir)
        def s3BaseDirFile = new File(s3BaseDir)

        if (!s3BaseDirFile.isDirectory()) {
            throw new IllegalStateException("AWS bundled config directory doesn't exist: $s3BaseDir")
        }

        s3Mock = S3Mock.create(S3_PORT, s3BaseDir)
        s3Mock.start()
    }

    def cleanupSpec() {
        log.info("stopping s3 mock")
        s3Mock?.shutdown()
    }

    def "new builder should contain correct default values"() {
        when:
        def builder = S3ConfigSource.builder()

        then:
        builder.getCacheTtl() == Duration.ofDays(7)
        builder.getClock() == Clock.systemDefaultZone()
    }

    def "check s3 mock"() {
        given:
        def s3 = AmazonS3ClientBuilder
            .standard()
            .withPathStyleAccessEnabled(true)
            .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:8001", "us-west-667"))
            .withCredentials(new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()))
            .build()

        when:
        def buckets = s3.listBuckets()
                        .findAll({ !it.getName().endsWith(".metadata") }) // remove weird s3 mock .metadata buckets
                        .sort({ it.name })

        log.info("retrieved buckets:")
        buckets.each { log.info("  {} [{}]", it.getName(), it.getCreationDate()) }

        then:
        buckets.size() >= 2 && buckets.size() <= 3

        when:
        def bucket = buckets.first()

        then:
        bucket.getName() == bucketName
    }

    @Override
    protected String getNonExistentConfigPath() {
        return "s3://some_bucket" + super.getNonExistentConfigPath()
    }

    @Override
    protected boolean fetchConfigs() {
        Boolean.getBoolean("integration")
    }

    def "should load correct configuration"() {
        given: "setup the query"
        def appName = "mySuperApp"
        def envName = "foo"
        def datacenter = "myDatacenter"
        def query = ConfigQuery.builder()
                               .appName(appName)
                               .datacenter(datacenter)
                               .envs([envName])
                               .build()

        and: "setup the builder params"
        def prefix = ("s3://${bucketName}" + '/${application}').toString()
        def paths = [
            prefix + '/config/${env}',
            prefix + 'nonexistent',
            prefix + '/config2/${env}',
            's3://non-existent-s3-bucket/${application}/config2/${env}']

        def builder = builder()
            .setRegion(S3_REGION)
            .setEndpoint(S3_ENDPOINT)
            .setPaths(paths)
            .setParallel(parallel)
            .setS3PathStyleAccess(true)

        def source = builder.build();

        when: "fetch the config"
        def config = source.get(query)

        then:
        !config.isEmpty()
        !config.isResolved()

        config.getString("haha.a") == "c"
        config.getString("haha.b") == "d"

        config.getString("sect_aa.aa") == "trololo"
        config.getString("sect_aa.bb") == "bar"
        config.getString("sect_aa.cc") == "haha"

        // this one doesn't exist (unresolved config)
        // !config.getString("sect_aa.java").isEmpty()

        config.getString("sect_zz.x") == "foo"
        config.getString("sect_zz.y") == "bar"

        when: "ask for config again"
        def newConfig = source.get(query)

        then: "retrieved config should be the same as previous one, no actual fetches should be done because of caching"
        newConfig == config

        where:
        parallel << [false, true]
    }

    def "withConfig() should set s3 specific map properties"() {
        given:
        def map = [
            "access-key-id"    : "someKey",
            "secret-access-key": "s3cret",
            "region"           : "us-west-10"
        ]
        def config = ConfigFactory.parseMap(map)
        def builder = S3ConfigSource.builder()
        def info = builder.getAwsConfig()

        expect:
        info.getAccessKeyId() == null
        info.getSecretAccessKey() == null
        info.getRegion() == null

        when:
        builder.withConfig(config)
        info = builder.getAwsConfig()

        then:
        info.getAccessKeyId() == map["access-key-id"]
        info.getSecretAccessKey() == map["secret-access-key"]
        info.getRegion() == map["region"]
    }

    def "should create valid s3 config source"() {
        when:
        def config = ConfigValueFactory.fromMap([impl: "s3", paths: ["s3://foo/bar"]]).toConfig()
        def source = Tsc4jImplUtils.createConfigSource(config, 1).get()

        then:
        source instanceof S3ConfigSource
    }

    def "close() should really close supplier"() {
        given:
        def s3Client = Mock(S3Client)

        and:
        def source = new S3ConfigSource(builder().withPath("s3://foo/bar"), s3Client)

        when:
        source.close()

        then:
        1 * s3Client.close()
    }

    S3ConfigSource.Builder builder() {
        S3ConfigSource.builder()
                      .setRegion(S3_REGION)
                      .setEndpoint(S3_ENDPOINT)
    }

    def setupS3Bucket() {
    }

    @Override
    AbstractConfigSource dummySource(String appName) {
        builder()
            .withPath("s3://bucket/name")
            .build()
    }

    @Override
    ConfigSourceBuilder dummyBuilder() {
        builder()
    }
}
