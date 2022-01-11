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

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Various {@link Stream} related utilities.
 */
@UtilityClass
public class StreamUtils {
    /**
     * Returns stream of items of a given collection with {@code null} elements removed.
     *
     * @param coll collection
     * @param <T>  item type
     * @return stream with null elements removed
     */
    public <T> Stream<T> nonNull(@NonNull Collection<T> coll) {
        return nonNull(coll.stream());
    }

    /**
     * Returns stream with {@code null} elements removed.
     *
     * @param s   source stream
     * @param <T> item type
     * @return stream with null elements removed
     */
    public <T> Stream<T> nonNull(@NonNull Stream<T> s) {
        return s.filter(Objects::nonNull);
    }

    /**
     * Removes {@code null} elements from given strings, trims elements and filters out empty strings.
     *
     * @param strings strings
     * @return stream of non-empty trimmed strings
     */
    public Stream<String> trimmedNonEmpty(@NonNull Collection<String> strings) {
        return trimmedNonEmpty(strings.stream());
    }

    /**
     * Removes {@code null} elements from a given string stream, trims elements and filters out empty strings.
     *
     * @param s stream of strings
     * @return stream of non-empty trimmed strings
     */
    public Stream<String> trimmedNonEmpty(@NonNull Stream<String> s) {
        return nonNull(s)
            .map(String::trim)
            .filter(it -> !it.isEmpty());
    }
}
