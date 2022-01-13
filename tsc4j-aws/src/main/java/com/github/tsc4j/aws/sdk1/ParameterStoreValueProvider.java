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

import com.amazonaws.services.simplesystemsmanagement.model.ParameterMetadata;
import com.github.tsc4j.aws.common.AwsConfig;
import com.github.tsc4j.aws.common.WithAwsConfig;
import com.github.tsc4j.core.AbstractConfigValueProvider;
import com.github.tsc4j.core.ValueProviderBuilder;
import com.github.tsc4j.core.utils.CollectionUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.val;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <a href="https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-paramstore.html">AWS SSM
 * Parameter Store</a> value provider implementation.
 */
public final class ParameterStoreValueProvider extends AbstractConfigValueProvider {
    private final SsmFacade ssmFacade;

    /**
     * Creates new instance.
     *
     * @param builder instance builder
     */
    protected ParameterStoreValueProvider(@NonNull Builder builder) {
        super(builder.getName(), SsmFacade.TYPE, SsmFacade.TYPE_ALIASES, builder.isAllowMissing(), builder.isParallel());
        this.ssmFacade = new SsmFacade(builder.getName(), builder.getAwsConfig(), builder.isDecrypt(), builder.isParallel());
    }

    /**
     * Creates new instance builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String getType() {
        return SsmFacade.TYPE;
    }

    @Override
    protected Map<String, ConfigValue> doGet(@NonNull List<String> names) {
        val ssmToNameMap = SsmFacade.ssmParamNameMap(names);
        val result = CollectionUtils.<String, ConfigValue>newMap();

        val ssmParams = ssmFacade.fetch(CollectionUtils.toList(ssmToNameMap.keySet()));
        ssmParams.forEach(it -> {
            val name = ssmToNameMap.get(it.getName());
            if (name != null) {
                result.put(name, SsmFacade.toConfigValue(it));
            }
        });

        return result;
    }

    @Override
    protected void doClose() {
        super.doClose();
        ssmFacade.close();
    }

    /**
     * Lists all parameter names in AWS SSM.
     *
     * @return list of all parameter names
     */
    public List<String> names() {
        return ssmFacade.list().stream()
            .map(ParameterMetadata::getName)
            .collect(Collectors.toList());
    }

    /**
     * Builder for {@link ParameterStoreValueProvider}.
     */
    @Data
    @Accessors(chain = true)
    @EqualsAndHashCode(callSuper = true)
    public static class Builder extends ValueProviderBuilder<Builder> implements WithAwsConfig<Builder> {
        @Getter
        private AwsConfig awsConfig = new AwsConfig();

        /**
         * Automatically decrypt parameter store values.
         */
        boolean decrypt = true;

        @Override
        public void withConfig(@NonNull Config config) {
            super.withConfig(config);
            getAwsConfig().withConfig(config);
            
            cfgBoolean(config, "decrypt", this::setDecrypt);
        }

        @Override
        public ParameterStoreValueProvider build() {
            return new ParameterStoreValueProvider(this);
        }
    }
}
