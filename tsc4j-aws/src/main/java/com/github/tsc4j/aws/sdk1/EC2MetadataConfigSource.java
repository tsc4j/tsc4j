/*
 * Copyright 2017 - 2022 tsc4j project
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

package com.github.tsc4j.aws.sdk1;

import com.amazonaws.util.EC2MetadataUtils;
import com.amazonaws.util.EC2MetadataUtils.IAMInfo;
import com.amazonaws.util.EC2MetadataUtils.IAMSecurityCredential;
import com.amazonaws.util.EC2MetadataUtils.InstanceInfo;
import com.amazonaws.util.EC2MetadataUtils.NetworkInterface;
import com.github.tsc4j.core.AbstractConfigSource;
import com.github.tsc4j.core.ConfigQuery;
import com.github.tsc4j.core.ConfigSource;
import com.github.tsc4j.core.ConfigSourceBuilder;
import com.github.tsc4j.core.Tsc4j;
import com.github.tsc4j.core.utils.CollectionUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.val;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static com.typesafe.config.ConfigFactory.empty;

/**
 * EC2 Metadata configuration source.
 *
 * @see <a href="https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html">EC2 instance
 *     metadata</a>
 * @see <a href="https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/util/EC2MetadataUtils.html">EC2MetadataUtils</a>
 */
public final class EC2MetadataConfigSource extends AbstractConfigSource {
    /**
     * Default {@link Config} path (value: <b>{@value}</b>)
     *
     * @see #atPath
     */
    protected static final String DEFAULT_CFG_PATH = "aws.ec2.metadata";

    private static final String ORIGIN_DESCRIPTION = "AWS EC2 metadata";

    static final String TYPE = "aws.ec2.metadata";
    static final Set<String> TYPE_ALIASES = CollectionUtils.toImmutableSet(
        "ec2.metadata", "ec2", "aws1.ec2.metadata", "aws1.ec2");

    /**
     * {@link Config} path to put AWS EC2 metadata to (default: {@value #DEFAULT_CFG_PATH})
     */
    private final String atPath;

    /**
     * Creates new instance
     *
     * @param builder instance builder
     */
    protected EC2MetadataConfigSource(@NonNull Builder builder) {
        super(builder);
        val path = Tsc4j.configPath(builder.getAtPath());
        this.atPath = path.isEmpty() ? DEFAULT_CFG_PATH : path;
    }

    /**
     * Creates instance builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    protected List<Config> fetchConfigs(@NonNull ConfigQuery query) {
        val configOpts = Arrays.asList(
            fetchEc2MetadataValue("ami-id", EC2MetadataUtils::getAmiId),
            fetchEc2MetadataValue("ami-launch-index", EC2MetadataUtils::getAmiLaunchIndex),
            fetchEc2MetadataValue("ami-manifest-path", EC2MetadataUtils::getAmiManifestPath),
            fetchEc2MetadataValue("ancestor-ami-ids", EC2MetadataUtils::getAncestorAmiIds),
            fetchEc2MetadataValue("availability-zone", EC2MetadataUtils::getAvailabilityZone),
            fetchEc2MetadataValue("block-device-mapping", EC2MetadataUtils::getBlockDeviceMapping),
            fetchEc2MetadataValue("ec2-instance-region", EC2MetadataUtils::getEC2InstanceRegion),
            fetchEc2MetadataValue("host-address-for-ec2-metadata-service", EC2MetadataUtils::getHostAddressForEC2MetadataService),
            fetchEc2MetadataValue("iam-instance-profile-info", EC2MetadataUtils::getIAMInstanceProfileInfo),
            //fetchEc2MetadataValue("iam-security-credentials", EC2MetadataUtils::getIAMSecurityCredentials),
            fetchEc2MetadataValue("instance-action", EC2MetadataUtils::getInstanceAction),
            fetchEc2MetadataValue("instance-id", EC2MetadataUtils::getInstanceId),
            fetchEc2MetadataValue("instance-info", EC2MetadataUtils::getInstanceInfo),
            fetchEc2MetadataValue("instance-signature", EC2MetadataUtils::getInstanceSignature),
            fetchEc2MetadataValue("instance-type", EC2MetadataUtils::getInstanceType),
            fetchEc2MetadataValue("local-host-name", EC2MetadataUtils::getLocalHostName),
            fetchEc2MetadataValue("mac-address", EC2MetadataUtils::getMacAddress),
            fetchEc2MetadataValue("network-interfaces", EC2MetadataUtils::getNetworkInterfaces),
            fetchEc2MetadataValue("private-ip-address", EC2MetadataUtils::getPrivateIpAddress),
            fetchEc2MetadataValue("product-codes", EC2MetadataUtils::getProductCodes),
            fetchEc2MetadataValue("public-key", EC2MetadataUtils::getPublicKey),
            fetchEc2MetadataValue("ramdisk-id", EC2MetadataUtils::getRamdiskId),
            fetchEc2MetadataValue("reservation-id", EC2MetadataUtils::getReservationId),
            fetchEc2MetadataValue("security-groups", EC2MetadataUtils::getSecurityGroups),
            fetchEc2MetadataValue("user-data", EC2MetadataUtils::getUserData)
        );

        val config = configOpts.stream()
            .filter(Optional::isPresent)
            .map(Optional::get)
            .reduce(empty(), (acc, cur) -> cur.withFallback(acc));

        val result = atPath.isEmpty() ? config : config.atPath(atPath);
        return Collections.singletonList(result);
    }

    private Optional<Config> fetchEc2MetadataValue(@NonNull String path, Supplier<Object> supplier) {
        try {
            return Optional.ofNullable(supplier.get())
                .map(e -> empty().withValue(path, toConfigValue(e)));
        } catch (Exception e) {
            log.warn("{} error fetching ec2 metadata path {}: {}", this, path, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * {@link ConfigValueFactory#fromAnyRef(Object)} can't handle list or bean values, this method works around this.
     *
     * @param o object to convert to {@link ConfigValue}
     * @return object as {@link ConfigValue}
     */
    @SneakyThrows
    @SuppressWarnings("unchecked")
    private ConfigValue toConfigValue(Object o) {
        if (o == null) {
            return createConfigValue(null);
        }

        if (o instanceof Iterable) {
            val list = new ArrayList<>();
            val i = (Iterable<?>) o;
            i.forEach(it -> list.add(toConfigValue(it)));
            return createConfigValue(list);
        } else if (o instanceof Map) {
            val m = (Map<String, ?>) o;
            val res = CollectionUtils.<String, ConfigValue>newMap();
            m.forEach((k, v) -> res.put(k, toConfigValue(v)));
            return ConfigValueFactory.fromMap(res, ORIGIN_DESCRIPTION);
        } else if (o instanceof InstanceInfo) {
            return toConfigValue((InstanceInfo) o);
        } else if (o instanceof NetworkInterface) {
            return toConfigValue((NetworkInterface) o);
        } else if (o instanceof IAMInfo) {
            return toConfigValue((IAMInfo) o);
        } else if (o instanceof IAMSecurityCredential) {
            return toConfigValue((IAMSecurityCredential) o);
        } else {
            return createConfigValue(o);
        }
    }

    private ConfigValue toConfigValue(IAMSecurityCredential c) {
        return new ConfigValueMap()
            .add("code", () -> c.code)
            .add("last-updated", () -> c.lastUpdated)
            .add("message", () -> c.message)
            .add("access-key-id", () -> c.accessKeyId)
            .add("message", () -> c.message)
            .add("expiration", () -> c.expiration)
            .add("secret-access-key", () -> c.secretAccessKey)
            .add("token", () -> c.token)
            .add("typt", () -> c.type)
            .toConfigValue();
    }

    private ConfigValue toConfigValue(IAMInfo i) {
        return new ConfigValueMap()
            .add("code", () -> i.code)
            .add("instance-profile-arn", () -> i.instanceProfileArn)
            .add("instance-profile-id", () -> i.instanceProfileId)
            .add("last-updated", () -> i.lastUpdated)
            .add("message", () -> i.message)
            .toConfigValue();
    }

    private ConfigValue toConfigValue(NetworkInterface n) {
        return new ConfigValueMap()
            .add("mac-address", n::getMacAddress)
            .add("owner-id", n::getOwnerId)
            .add("profile", n::getProfile)
            .add("hostname", n::getHostname)
            .add("local-ipv4s", n::getLocalIPv4s)
            .add("public-hostname", n::getPublicHostname)
            .add("public-ipv4s", n::getPublicIPv4s)
            .add("security-groups", n::getSecurityGroups)
            .add("security-group-ids", n::getSecurityGroupIds)
            .add("subnet-ipv4-cidr-block", n::getSubnetIPv4CidrBlock)
            .add("subnet-id", n::getSubnetId)
            .add("vpc-ipv4-cidr-block", n::getVpcIPv4CidrBlock)
            .add("vpc-id", n::getVpcId)
            .toConfigValue();
    }

    private ConfigValue toConfigValue(InstanceInfo i) {
        return new ConfigValueMap()
            .add("pending-time", i::getPendingTime)
            .add("instance-type", i::getInstanceType)
            .add("image-id", i::getImageId)
            .add("instance-id", i::getInstanceId)
            .add("billing-products", i::getBillingProducts)
            .add("architecture", i::getArchitecture)
            .add("account-id", i::getAccountId)
            .add("kernel-id", i::getKernelId)
            .add("ramdisk-id", i::getRamdiskId)
            .add("region", i::getRegion)
            .add("version", i::getVersion)
            .add("availability-zone", i::getAvailabilityZone)
            .add("private-ip", i::getPrivateIp)
            .add("devpay-product-codes", i::getDevpayProductCodes)
            .toConfigValue();
    }

    private static ConfigValue createConfigValue(Object o) {
        return ConfigValueFactory.fromAnyRef(o, ORIGIN_DESCRIPTION);
    }

    private static class ConfigValueMap {
        private final Map<String, Object> map = new LinkedHashMap<>();

        ConfigValueMap add(String key, Supplier<Object> valueSupplier) {
            val value = valueSupplier.get();
            if (value != null) {
                map.put(key, value);
            }
            return this;
        }

        ConfigValue toConfigValue() {
            return createConfigValue(map);
        }
    }

    /**
     * Builder for {@link EC2MetadataConfigSource}.
     */
    @Data
    @Accessors(chain = true)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    public static class Builder extends ConfigSourceBuilder<Builder> {
        /**
         * {@link Config} path to put AWS EC2 metadata to (default: {@value #DEFAULT_CFG_PATH})
         */
        private String atPath = DEFAULT_CFG_PATH;

        @Override
        public void withConfig(@NonNull Config config) {
            super.withConfig(config);

            cfgString(config, "at-path", this::setAtPath);
        }

        @Override
        public ConfigSource build() {
            return new EC2MetadataConfigSource(this);
        }
    }
}
