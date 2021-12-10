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

import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.transfer.TransferManager
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.github.tsc4j.aws.sdk2.SsmFacade
import com.github.tsc4j.core.Tsc4jImplUtils
import com.github.tsc4j.core.impl.Stopwatch
import groovy.util.logging.Slf4j
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.DeleteParametersRequest
import software.amazon.awssdk.services.ssm.model.PutParameterRequest

@Slf4j
class AwsTestEnv {
    static def awsRegion = "us-east-1"
    static def awsEndpoint = "http://localhost:4566"

    // example SSM parameter map
    // parameter path => [ aws parameter type, parameter raw value, optional expected parameter value ]
    /*
    static def ssmParameters = [
        "/a/x"  : [ParameterType.STRING, "a.x"],
        "/a/y"  : [ParameterType.STRING_LIST, "a.y,a,b,c", ["a.y", "a", "b", "c"]],
        "/a/z"  : [ParameterType.SECURE_STRING, "a.z"],
        "/b/c/d": [ParameterType.STRING, "42"]
        ]
    */

    private static SsmClient ssm
    private static AmazonS3 s3
    private static TransferManager tm

    static SsmClient ssmClient() {
        if (ssm == null) {
            ssm = SsmClient
                .builder()
                .endpointOverride(URI.create(awsEndpoint))
                .build()
        }
        ssm
    }

    static SsmClient setupSSM(SsmClient ssm, Map parameters) {
        cleanupSSM(ssm)
        createSSMParams(ssm, parameters)
    }

    static SsmClient createSSMParams(SsmClient ssm, Map ssmParameters) {
        def sw = new Stopwatch()
        ssmParameters.each {
            def name = it.key
            def e = it.value
            def req = PutParameterRequest.builder()
                                         .type(e[0])
                                         .value(e[1])
                                         .name(name)
                                         .description("desc_" + name)
                                         .overwrite(true)
                                         .build()
            ssm.putParameter(req)
            log.info("created AWS SSM parameter: {} -> {}", name, req)
        }
        log.info("created {} AWS SSM parameters in {}", ssmParameters.size(), sw)
        ssm
    }

    static SsmClient cleanupSSM(SsmClient ssm) {
        def sw = new Stopwatch()
        def facade = new SsmFacade("", ssm, true, false)
        def names = facade.list().collect { it.name() }

        def numDeleted = 0
        Tsc4jImplUtils.partitionList(names, 10).collect {
            def req = DeleteParametersRequest.builder().names(it).build()
            ssm.deleteParameters(req)
            numDeleted += it.size()
            log.info("deleted AWS SSM {} parameters: {}", it.size(), it)
        }
        log.info("deleted {} AWS SSM parameters in {}", numDeleted, sw)
        ssm
    }

    static AmazonS3 s3Client() {
        if (s3 == null) {
            def endpoint = new AwsClientBuilder.EndpointConfiguration(awsEndpoint, awsRegion)
            s3 = AmazonS3Client.builder()
                               .withEndpointConfiguration(endpoint)
                               .build()
        }
        s3
    }

    static TransferManager transferManager() {
        if (tm == null) {
            tm = new TransferManagerBuilder()
                .withDisableParallelDownloads(false)
                .withS3Client(s3Client())
                .withShutDownThreadPools(true)
                .build()
        }
        tm
    }

    static AmazonS3 uploadToS3(String srcDir, String bucketName, String bucketPrefix) {
        def tm = transferManager()
        def s3 = tm.getAmazonS3Client()

        def create = s3.createBucket(bucketName)
        log.info("bucket created: {}", create)

        log.info("uploading {} to s3://{}{}", srcDir, bucketName, "/")
        def res = tm.uploadDirectory(bucketName, "/", new File(srcDir), true)
        res.waitForCompletion()
        log.info("upload done: {}", srcDir)
        tm.getAmazonS3Client()
    }
}
