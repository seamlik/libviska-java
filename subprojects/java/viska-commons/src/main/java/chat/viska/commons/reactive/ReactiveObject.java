/*
 * Copyright 2017 Kai-Chung Yan (殷啟聰)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chat.viska.commons.reactive;

import io.reactivex.Flowable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import javax.annotation.Nonnull;

/**
 * Object whose changes can be monitored.
 * @param <T> Type of the values.
 */
public interface ReactiveObject<T> {

  /**
   * Gets the current value;
   */
  @Nonnull
  T getValue();

  /**
   * Gets a stream of the emitted values. Subscribers will also receive the most recently emitted
   * value plus all subsequent ones.
   */
  @Nonnull
  Flowable<T> getStream();

  /**
   * Atomically gets and does something with the current value.
   * @throws Exception If {@code consumer} throws an {@link Exception}.
   */
  void getAndDo(@Nonnull Consumer<T> consumer) throws Exception;

  /**
   * Atomically gets and does something the current value.
   * @throws Exception If {@code consumer} throws an {@link Exception}.
   */
  <R> R getAndDo(@Nonnull Function<T, R> function) throws Exception;
}