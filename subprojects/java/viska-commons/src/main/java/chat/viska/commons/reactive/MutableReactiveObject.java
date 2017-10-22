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
import io.reactivex.processors.BehaviorProcessor;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class MutableReactiveObject<T> implements ReactiveObject<T> {

  private final BehaviorProcessor<T> stream;
  private T value;

  public MutableReactiveObject(@Nonnull final T initial) {
    stream = BehaviorProcessor.createDefault(initial);
    value = initial;
  }

  /**
   * Sets the value.
   */
  public void setValue(@Nonnull final T value) {
    synchronized (stream) {
      this.value = value;
      stream.onNext(value);
    }
  }

  /**
   * Changes the value. Unlike {@link #setValue(Object)}, it does nothing if the new value equals to
   * the current value.
   */
  public void changeValue(@Nonnull final T value) {
    synchronized (this.stream) {
      if (!this.value.equals(value)) {
        setValue(value);
      }
    }
  }

  public void complete() {
    synchronized (this.stream) {
      this.stream.onComplete();
    }
  }

  @Override
  @Nonnull
  public T getValue() {
    return value;
  }

  @Override
  @Nonnull
  public Flowable<T> getStream() {
    return stream;
  }

  @Override
  public String toString() {
    return value.toString();
  }

  @Override
  public void getAndDo(@Nonnull final Consumer<T> consumer) throws Exception {
    synchronized (this.stream) {
      consumer.accept(value);
    }
  }

  @Override
  public <R> R getAndDo(@Nonnull Function<T, R> function) throws Exception {
    synchronized (this.stream) {
      return function.apply(value);
    }
  }
}