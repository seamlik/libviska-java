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
import javax.annotation.Nonnull;
import io.reactivex.processors.PublishProcessor;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
public class MutableReactiveObject<T> implements ReactiveObject<T> {

  private final PublishProcessor<T> stream = PublishProcessor.create();
  private T value;

  public MutableReactiveObject() {}

  public MutableReactiveObject(@Nonnull final T initial) {
    this.value = initial;
    this.stream.onNext(initial);
  }

  public void setValue(@Nonnull final T value) {
    this.value = value;
    stream.onNext(value);
  }

  public void complete() {
    stream.onComplete();
  }

  @Override
  public T getValue() {
    return value;
  }

  @Override
  public Flowable<T> getStream() {
    return stream;
  }

  @Override
  public String toString() {
    return value.toString();
  }
}