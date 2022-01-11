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

package com.github.tsc4j.core.utils;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Various collection utilities
 */
@UtilityClass
public class CollectionUtils {
    /**
     * Creates new <b>mutable</b> set that retains insertion order on traversal
     *
     * @param <T> element type
     * @return new mutable map
     */
    public <T> Set<T> newSet() {
        return new LinkedHashSet<>();
    }

    /**
     * Creates <b>mutable</b> set from a given items, retaining collection order, removing {@code null} elements.
     *
     * @param items items
     * @param <T>   item type
     * @return set of given items
     */
    @SafeVarargs
    public <T> Set<T> toSet(@NonNull T... items) {
        return toSet(Arrays.asList(items));
    }

    /**
     * Creates <b>mutable</b> set from a given items, retaining collection order, removing {@code null} elements.
     *
     * @param items items
     * @param <T>   item type
     * @return set of given items
     */
    public <T> Set<T> toSet(@NonNull Collection<T> items) {
        return StreamUtils.nonNull(items)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Creates <b>immutable</b> set from a given items, retaining collection order, removing {@code null} elements.
     *
     * @param items items
     * @param <T>   item type
     * @return set of given items
     */
    @SafeVarargs
    public <T> Set<T> toImmutableSet(@NonNull T... items) {
        return toImmutableSet(Arrays.asList(items));
    }

    /**
     * Creates <b>mutable</b> set from a given items, retaining collection order, removing {@code null} elements.
     *
     * @param items items
     * @param <T>   item type
     * @return set of given items
     */
    public <T> Set<T> toImmutableSet(@NonNull Collection<T> items) {
        return Collections.unmodifiableSet(toSet(items));
    }

    /**
     * Creates new <b>mutable</b> map.
     *
     * @param <K> key type
     * @param <V> value type
     * @return new mutable map
     */
    public <K, V> Map<K, V> newMap() {
        return new LinkedHashMap<>();
    }

    /**
     * Creates new <b>mutable</b> list from a given items, removing {@code null} elements.
     *
     * @param items item array
     * @param <T>   item type
     * @return mutable list.
     */
    @SafeVarargs
    public <T> List<T> toList(@NonNull T... items) {
        return toList(Arrays.asList(items));
    }

    /**
     * Creates new <b>mutable</b> list from a given collection, removing {@code null} elements.
     *
     * @param coll collection
     * @param <T>  item type
     * @return mutable list.
     */
    public <T> List<T> toList(@NonNull Collection<T> coll) {
        return StreamUtils.nonNull(coll)
            .collect(Collectors.toList());
    }

    /**
     * Creates new <b>immutable</b> list from given items, removing {@code null} elements.
     *
     * @param items items
     * @param <T>   item type
     * @return immutable list
     */
    @SafeVarargs
    public <T> List<T> toImmutableList(@NonNull T... items) {
        return toImmutableList(Arrays.asList(items));
    }

    /**
     * Creates new <b>immutable</b> list from given items, removing {@code null} elements.
     *
     * @param coll collection
     * @param <T>  item type
     * @return immutable list
     */
    public <T> List<T> toImmutableList(@NonNull Collection<T> coll) {
        return Collections.unmodifiableList(toList(coll));
    }

    /**
     * Creates new <b>mutable</b> list from given items with null items and duplicates removed.
     *
     * @param items items
     * @param <T>   item type
     * @return mutable list
     */
    @SafeVarargs
    public <T> List<T> toUniqList(@NonNull T... items) {
        return toUniqList(Arrays.asList(items));
    }

    /**
     * Creates new <b>mutable</b> list from a given collection with null items and duplicates removed.
     *
     * @param coll collection
     * @param <T>  item type
     * @return mutable list
     */
    public <T> List<T> toUniqList(@NonNull Collection<T> coll) {
        return toList(toSet(coll));
    }

    /**
     * Creates new <b>immutable</b> list from a given collection with null items and duplicates removed.
     *
     * @param items collection
     * @param <T>   item type
     * @return immutable list
     */
    @SafeVarargs
    public <T> List<T> toImmutableUniqList(@NonNull T... items) {
        return toImmutableUniqList(Arrays.asList(items));
    }

    /**
     * Creates new <b>immutable</b> list from a given collection with null items and duplicates removed.
     *
     * @param coll collection
     * @param <T>  item type
     * @return immutable list
     */
    public <T> List<T> toImmutableUniqList(@NonNull Collection<T> coll) {
        return Collections.unmodifiableList(toUniqList(coll));
    }

    /**
     * Partitions given collection to a list of sublists, removing null elements
     *
     * @param coll        collection to partition
     * @param maxElements maximum number of elements in a single sublist
     * @param <T>         item type
     * @return list of lists
     */
    public <T> List<List<T>> partition(@NonNull Collection<T> coll, int maxElements) {
        if (maxElements < 1) {
            throw new IllegalArgumentException("maxElements can't be < 1.");
        }

        if (coll.isEmpty()) {
            return Collections.emptyList();
        }

        // remove nulls
        val src = StreamUtils.nonNull(coll)
            .collect(Collectors.toList());

        // JDK8 doesn't provide takeUntil()/takeWhile() on Stream, this sucks, that's why
        // this operation uses old school java for loop.
        val result = new ArrayList<List<T>>();
        for (int fromIdx = 0; fromIdx < src.size(); fromIdx += maxElements) {
            int toIdx = fromIdx + maxElements;
            if (toIdx > src.size()) {
                toIdx = src.size();
            }
            val chunk = new ArrayList<T>(toIdx - fromIdx);
            chunk.addAll(src.subList(fromIdx, toIdx));
            result.add(chunk);
        }

        return result;
    }
}
