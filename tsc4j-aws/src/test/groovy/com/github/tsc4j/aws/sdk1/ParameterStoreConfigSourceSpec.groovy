/*
 * Copyright 2017 - 2019 tsc4j project
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
 *
 */

package com.github.tsc4j.aws.sdk1

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement
import com.amazonaws.services.simplesystemsmanagement.model.DeleteParametersRequest
import com.amazonaws.services.simplesystemsmanagement.model.ParameterType
import com.amazonaws.services.simplesystemsmanagement.model.PutParameterRequest
import com.github.tsc4j.core.Tsc4j
import com.github.tsc4j.core.Tsc4jImplUtils
import com.github.tsc4j.testsupport.TestConstants
import com.typesafe.config.ConfigFactory
import groovy.util.logging.Slf4j
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Slf4j
@Unroll
class ParameterStoreConfigSourceSpec extends Specification {
    static def parameters = [
        "/a/x"  : [ParameterType.String, "x"],
        "/a/y"  : [ParameterType.StringList, "a,b,c", ["a", "b", "c"]],
        "/a/z"  : [ParameterType.SecureString, "val_a.z"],
        "/b/c/d": [ParameterType.String, "42"]
    ]

    @Shared
    String atPath = "foo.bar"

    @Shared
    @AutoCleanup
    def source = ParameterStoreConfigSource.builder()
                                           .setEndpoint(AwsTestEnv.awsEndpoint)
                                           .setRegion("us-east-1")
                                           .withPath('/')
                                           .setAtPath(atPath)
                                           .build()

    def "should load it using service loader"() {
        given:
        def config = ConfigFactory.parseMap([
            impl : impl,
            name : "foo",
            paths: ['/']
        ])

        when:
        def sourceOpt = Tsc4jImplUtils.createConfigSource(config, 1)
        log.info("got: {}", sourceOpt)

        then:
        sourceOpt.isPresent()

        def source = sourceOpt.get()
        source instanceof ParameterStoreConfigSource

        source.getName() == "foo"

        where:
        impl << ["aws.ssm", "ssm", "aws.param.store"]
    }

    def "should fetch config"() {
        when:
        def config = source.get(TestConstants.defaultConfigQuery)
        log.info("retrieved config: {}", Tsc4j.render(config, true))

        then:
        !config.isEmpty()
        config.withoutPath("foo").isEmpty()

        when:
        def cfg = config.getConfig(atPath)

        then:
        cfg.entrySet().size() == parameters.size()
        cfg.getInt("b.c.d") == 42

        parameters.each {
            def name = it.key
            def path = name.replaceFirst('^/+', '').replace('/', '.')
            def expectedValue = it.value.size() > 2 ? it.value[2] : it.value[1]

            assert cfg.hasPath(path)
            assert cfg.getValue(path).unwrapped() == expectedValue
        }
    }

    def setupSpec() {
        createSsmParameters()
    }

    def createSsmParameters() {
        SsmFacade facade = source.ssm
        AWSSimpleSystemsManagement ssm = facade.ssm
        log.info("we have ssm: {}", ssm)

        // remove all existing params
        def names = facade.list().collect { it.getName() }
        Tsc4jImplUtils.partitionList(names, 10).collect {
            def dreq = new DeleteParametersRequest().withNames(it)
            ssm.deleteParameters(dreq)
            log.info("deleted {} parameters: {}", it.size(), it)
        }

        // create params
        parameters.each {
            def name = it.key
            def e = it.value
            def req = new PutParameterRequest()
                .withType(e[0])
                .withValue(e[1])
                .withName(name)
                .withDescription("desc_" + name)
                .withOverwrite(true)
            ssm.putParameter(req)
            log.info("created parameter: {} -> {}", name, req)
        }
    }

    def "withConfig() should configure parameters to expected values"() {
        given:
        def cfgMap = [
            // common AWS configuration parameters
            "access-key-id"       : 'foo',
            "secret-access-key"   : 'bar',
            "region"              : 'us-east-5',
            "endpoint"            : "http://localhost:4566/",
            "gzip"                : false,
            "timeout"             : '67s',
            "max-connections"     : 13,
            "max-error-retry"     : 7,
            "s3-path-style-access": true,

            // config source specific configuration parameters
            "paths"               : ['/foo', '/bar/baz', '/blah'],
            "at-path"             : "/aws-ssm",
        ]
        def config = ConfigFactory.parseMap(cfgMap)

        and:
        def builder = ParameterStoreConfigSource.builder()

        expect: "default values"
        with(builder.getAwsConfig()) {
            getEndpoint() == null
            getAccessKeyId() == null
            getSecretAccessKey() == null
            getRegion() == null
            getEndpoint() == null
            isGzip() == true
            getTimeout().toSeconds() == 10
            getMaxConnections() == 100
            getMaxErrorRetry() == 0
            getS3PathStyleAccess() == null
        }

        builder.getPaths().isEmpty()
        builder.getAtPath().isEmpty()

        when:
        builder.withConfig(config)

        then: "settings should be applied"
        with(builder.getAwsConfig()) {
            getEndpoint() == cfgMap.endpoint
            getAccessKeyId() == cfgMap.'access-key-id'
            getSecretAccessKey() == cfgMap.'secret-access-key'
            getRegion() == cfgMap.region
            isGzip() == cfgMap.gzip
            getTimeout().toSeconds() == 67
            getMaxConnections() == 13
            getMaxErrorRetry() == 7
            getS3PathStyleAccess() == true
        }

        builder.getPaths() == cfgMap.paths
        builder.getAtPath() == cfgMap.'at-path'
    }
}
