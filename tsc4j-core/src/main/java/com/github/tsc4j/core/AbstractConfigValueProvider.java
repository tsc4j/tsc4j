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

package com.github.tsc4j.core;

import com.github.tsc4j.core.utils.CollectionUtils;
import com.typesafe.config.ConfigValue;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.val;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class for writing {@link ConfigValueProvider} implementations.
 */
public abstract class AbstractConfigValueProvider extends BaseInstance implements ConfigValueProvider {
    /**
     * Tells whether this provider may execute parallel operations.
     */
    @Getter(AccessLevel.PROTECTED)
    private final boolean parallel;

    private final boolean allowMissing;

    /**
     * Instance type.
     *
     * @see #getTypeAliases()
     */
    @Getter
    private final String type;

    /**
     * Instance type aliases.
     *
     * @see #getType()
     */
    @Getter
    private final Set<String> typeAliases;

    /**
     * Creates instance.
     *
     * @param name         instance name
     * @param type         instance type
     * @param typeAliases  instance type aliases
     * @param allowMissing whether missing names should be tolerated
     * @param parallel     execute operations in parallel?
     * @see #isParallel()
     * @see #allowMissing()
     */
    protected AbstractConfigValueProvider(String name,
                                          @NonNull String type,
                                          @NonNull Collection<String> typeAliases,
                                          boolean allowMissing,
                                          boolean parallel) {
        super(name);
        this.type = type;
        this.typeAliases = CollectionUtils.toImmutableSet(typeAliases);
        this.allowMissing = allowMissing;
        this.parallel = parallel;
    }

    @Override
    public final boolean supportsConfigValueReferenceType(@NonNull String type) {
        return getType().equals(type) || getTypeAliases().contains(type);
    }

    @Override
    public final boolean allowMissing() {
        return allowMissing;
    }

    @Override
    public Map<String, ConfigValue> get(@NonNull Collection<String> names) {
        checkClosed();
        val uniqNames = Tsc4jImplUtils.toUniqueList(names);
        if (uniqNames.isEmpty()) {
            return Collections.emptyMap();
        }

        return doGet(uniqNames);
    }

    /**
     * Implementation-specific method for obtaining values.
     *
     * @param names non-empty list of value names to fetch data from
     * @return map of {@code name -> value} pairs
     */
    protected abstract Map<String, ConfigValue> doGet(@NonNull List<String> names);
}
