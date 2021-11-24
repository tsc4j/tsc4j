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

package com.github.tsc4j.api;

import lombok.NonNull;

import java.io.Closeable;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Value from {@link ReloadableConfig} which always holds most up to date value from configuration.
 * <p>
 * Container that contains latest configuration value or custom bean mapped
 *
 * @param <T> value type
 * @see Supplier
 */
public interface Reloadable<T> extends Supplier<T>, Closeable {
    /**
     * Tells whether value is present or not.
     *
     * @return true if reloadable contains value, otherwise false.
     * @see #isEmpty()
     */
    boolean isPresent();

    /**
     * Tells whether reloadable doesn't contain value.
     *
     * @return true if reloadable doesn't contain value, otherwise false.
     * @see #isPresent()
     */
    default boolean isEmpty() {
        return !isPresent();
    }

    /**
     * Retrieves the value if it's present.
     *
     * @return value if present.
     * @throws java.util.NoSuchElementException if value is not present.
     * @see #isPresent()
     * @see #isEmpty()
     * @see #orElse(Object)
     * @see #orElseGet(Supplier)
     */
    @Override
    T get();


    /**
     * Returns reloadable's stored value if it's present, otherwise returns {@code other}.
     *
     * @param other other value that should be returned if reloadable is empty.
     * @return reloadable's stored value if it's present, otherwise {@code other}.
     * @see #isPresent()
     * @see #isEmpty()
     */
    T orElse(T other);

    /**
     * Returns reloadable's stored value if it's present, otherwise returns value supplied by {@code supplier}.
     *
     * @param supplier the supplying function that produces a value to be returned if reloadable is empty.
     * @return reloadable's stored value if it's present, otherwise result of {@code supplier}.
     */
    T orElseGet(@NonNull Supplier<T> supplier);

    /**
     * If a value is present, returns the value, otherwise throws {@link NoSuchElementException}
     *
     * @return value if present
     * @throws NoSuchElementException – if no value is present
     */
    T orElseThrow();

    /**
     * If a value is present, returns the value, otherwise throws an exception produced by the exception supplying
     * function.
     *
     * @param <X>               Type of the exception to be thrown
     * @param exceptionSupplier the supplying function that produces an exception to be thrown
     * @return the value, if present
     * @throws X                    if no value is present
     * @throws NullPointerException if no value is present and the exception supplying function is {@code null}
     */
    <X extends Throwable> T orElseThrow(@NonNull Supplier<? extends X> exceptionSupplier) throws X;

    /**
     * Invokes specified consumer if value is present.
     *
     * @param consumer consumer to invoke if value is present
     * @return reference to itself
     * @throws NullPointerException in case of null arguments
     * @see #ifPresentAndRegister(Consumer)
     */
    Reloadable<T> ifPresent(@NonNull Consumer<T> consumer);

    /**
     * Invokes specified consumer if value is present and registers it for value updates.
     * <p><b>NOTE:</b> consumer should be thread-safe and non-blocking.</p>
     *
     * @param consumer consumer to register and invoke if value is present
     * @return reference to itself
     * @throws NullPointerException  in case of null argument(s)
     * @throws IllegalStateException if reloadable is closed
     * @see #ifPresent(Consumer)
     * @see #register(Consumer)
     */
    default Reloadable<T> ifPresentAndRegister(@NonNull Consumer<T> consumer) {
        return ifPresent(consumer).register(consumer);
    }

    /**
     * <p>Adds/registers new consumer that is going to be invoked on value update. Multiple consumers can be
     * registered. Consumer is invoked with newly assigned value when configuration changes; Note that <b>consumer is
     * invoked with
     * <i>null</i> value</b> if value was previously present in reloadable (see {@link #isPresent()}) and has been
     * removed from config during refresh.
     * </p>
     * <p><b>NOTE:</b> consumer should be thread-safe and non-blocking.</p>
     *
     * @param consumer consumer to invoke on value change
     * @return reference to itself
     * @throws NullPointerException  in case of null argument(s)
     * @throws IllegalStateException if reloadable is closed
     */
    Reloadable<T> register(@NonNull Consumer<T> consumer);

    /**
     * Creates new reloadable that uses this reloadable as a source of updates and that applies given mapping function
     * on value update.
     *
     * @param mapper mapping function that maps value of source reloadable and value of created reloadable
     * @param <R>    destination type
     * @return reloadable
     */
    <R> Reloadable<R> map(@NonNull Function<T, R> mapper);

    /**
     * Creates new reloadable that this reloadable as a source of updates and that applies given predicate on value
     * update.
     *
     * @param predicate stored value predicate.
     * @return reloadable that filters value.
     */
    Reloadable<T> filter(@NonNull Predicate<T> predicate);

    /**
     * Registers given runnable to be invoked when value gets cleared from this reloadable.
     *
     * @return reference to itself.
     */
    Reloadable<T> onClear(@NonNull Runnable onClear);

    /**
     * Registers given runnable to be invoked as part of {@link #close()} invocation.
     *
     * @param action action to run on {@link #close()} invocation.
     * @return reference to itself
     * @throws NullPointerException  in case of null argument(s)
     * @throws IllegalStateException if reloadable is closed
     */
    Reloadable<T> onClose(@NonNull Runnable action);

    /**
     * Closes reloadable, unsubscribes it from value updates and runs close action (see {@link #close()}); after
     * invocation of this method other methods will throw {@link IllegalStateException}.
     *
     * @see #onClose(Runnable)
     */
    @Override
    void close();
}
