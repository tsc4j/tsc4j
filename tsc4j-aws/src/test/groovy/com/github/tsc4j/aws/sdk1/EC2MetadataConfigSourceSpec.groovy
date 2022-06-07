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

import com.github.tsc4j.core.Tsc4j
import com.github.tsc4j.core.Tsc4jImplUtils
import com.github.tsc4j.testsupport.TestConstants
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import groovy.util.logging.Slf4j
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import static com.amazonaws.SDKGlobalConfiguration.EC2_METADATA_SERVICE_OVERRIDE_SYSTEM_PROPERTY
import static com.github.tsc4j.aws.sdk1.EC2MetadataConfigSource.DEFAULT_CFG_PATH

@Slf4j
@Unroll
class EC2MetadataConfigSourceSpec extends Specification {
    def ec2MockEndpoint = 'http://localhost:1338' // WARNING: no trailing slash!

    def "should create valid e2 metadata config source builder"() {
        when:
        def config = ConfigValueFactory.fromMap([impl: impl]).toConfig()
        def source = Tsc4jImplUtils.createConfigSource(config, 1).get()

        then:
        source != null
        source instanceof EC2MetadataConfigSource

        where:
        impl << [
            "aws.ec2.metadata",
            " Aws.ec2.metadatA ",
            "ec2.metadaTA",
            "ec2",
            " EC2 ",
            "EC2MetadataConfigSource",
            "com.github.tsc4j.aws.sdk1.EC2MetadataConfigSource"
        ]
    }

    @Timeout(3)
    @RestoreSystemProperties
    def "should return expected values with installed at: '#atPath'"() {
        given:
        def cfgMap = ['impl': "ec2", 'at-path': atPath]
        def sourceConfig = ConfigFactory.parseMap(cfgMap)
        def source = Tsc4jImplUtils.createConfigSource(sourceConfig, 1).get()

        def expectedPath = Tsc4j.configPath(atPath)

        and: "set aws ec2 system properties"
        System.setProperty(EC2_METADATA_SERVICE_OVERRIDE_SYSTEM_PROPERTY, ec2MockEndpoint)

        when:
        def config = source.get(TestConstants.defaultConfigQuery);
        log.info("fetched config: {}", Tsc4j.render(config, 3))
        log.info("expected path: '{}'", expectedPath)

        then:
        !config.isEmpty()

        when: "fetch real config"
        def ec2Config = expectedPath.isEmpty() ? config.getConfig(DEFAULT_CFG_PATH) : config.getConfig(expectedPath)
        log.info("ec2 config: {}", Tsc4j.render(ec2Config, 3))

        then:
        !ec2Config.isEmpty()
        !ec2Config.getString('ami-id').isEmpty()
        !ec2Config.getString('instance-id').isEmpty()
        !ec2Config.getString('private-ip-address').isEmpty()

        assertConfig(ec2Config)

        where:
        atPath << [null, "", ".", "foo"]
    }

    def assertConfig(Config cfg) {
        log.info("asserting EC2 metadata config:\n{}", Tsc4j.render(cfg, true))

        // SEE: https://github.com/aws/amazon-ec2-metadata-mock/blob/main/pkg/config/defaults/aemm-metadata-default-values.json

        assert !cfg.isEmpty()

        with(cfg) {
            getString("ami-id") == 'ami-0a887e401f7654935'
            getInt('ami-launch-index') == 0
            getString('ami-manifest-path') == '(unknown)'
            getString('availability-zone') == 'us-east-1a'

            with(getConfig('block-device-mapping')) {
                !isEmpty()
                getString('ami') == '/dev/xvda'
                getString('ebs0') == 'sdb'
                getString('ephemeral0') == 'sdb'
                getString('root') == '/dev/xvda'
                getString('swap') == 'sdcs'
            }

            getString('ec2-instance-region') == 'us-east-1'

            getString("instance-action") == 'none'
            getString("instance-id") == 'i-1234567890abcdef0'

            with(getConfig('instance-info')) {
                getString("account-id") == "0123456789"
                getString("image-id") == "ami-0b69ea66ff7391e80"
                getString("availability-zone") == "us-east-1f"
                getString("version") == "2017-09-30"
                getString("private-ip") == "10.0.7.10"

                getString("instance-id") == "i-1234567890abcdef0"
                getString("pending-time") == "2019-10-31T07:02:24Z"
                getString("architecture") == "x86_64"
                getString("instance-type") == "m4.xlarge"
                getString("region") == "us-east-1"
            }

            getString("instance-signature") == "TesTTKmBbj+DUw6ut6BOr4mFGpax/k6BhIbsotUHvSIhqv7oKqwB4HZhgGP2Gvcxtz5m3QGUbnwI\nhy33GWxjn7+qfZ/GUeZB1Ilc+3rW/P9G/tGxIB3HtqB6q2J6B4DOh6CJiH+BnrHazGW+bJD406Nz\neP9n/rGEGGm0cGEbbeB="
            getString("instance-type") == 'm4.xlarge'
            getString("local-host-name") == 'ip-172-16-34-43.ec2.internal'
            getString("mac-address") == '0e:49:61:0f:c3:11'

            // network interfaces, too much to assert
            def ifs = getConfigList('network-interfaces')
            ifs.size() == 19

            getString("private-ip-address") == '172.16.34.43'
            getStringList("product-codes") == ['3iplms73etrdhxdepv72l6ywj']
            getString("public-key") == 'ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQC/JxGByvHDHgQAU+0nRFWdvMPi22OgNUn9ansrI8QN1ZJGxD1ML8DRnJ3Q3zFKqqjGucfNWW0xpVib+ttkIBp8G9P/EOcX9C3FF63O3SnnIUHJsp5faRAZsTJPx0G5HUbvhBvnAcCtSqQgmr02c1l582vAWx48pOmeXXMkl9qe9V/s7K3utmeZkRLo9DqnbsDlg5GWxLC/rWKYaZR66CnMEyZ7yBy3v3abKaGGRovLkHNAgWjSSgmUTI1nT5/S2OLxxuDnsC7+BiABLPaqlIE70SzcWZ0swx68Bo2AY9T9ymGqeAM/1T4yRtg0sPB98TpT7WrY5A3iia2UVtLO/xcTt test'
            getString("ramdisk-id") == 'ari-01bb5768'
            getString("reservation-id") == 'r-046cb3eca3e201d2f'
            getStringList("security-groups") == ['ura-launch-wizard-harry-1']

            // doesn't seem to be returned by the mock endpoint
            // getString("user-data") == ''
        }

        true
    }
}
