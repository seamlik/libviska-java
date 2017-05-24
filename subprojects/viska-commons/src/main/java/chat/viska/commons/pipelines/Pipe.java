/*
 * Copyright (C) 2017 Kai-Chung Yan (殷啟聰)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chat.viska.commons.pipelines;

import io.reactivex.annotations.NonNull;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;

public interface Pipe {

  /**
   * Indicates that the same instance of the annotated {@link Pipe} can be added
   * to multiple {@link Pipeline}s without a race condition. Implementations of
   * {@link Pipe} without this annotation should check for misbehavior and may
   * throw an {@link IllegalStateException} in {@link Pipe#onAddedToPipeline(Pipeline)}.
   */
  @Documented
  @Target(value = ElementType.TYPE)
  @interface Shareable {}

  void catchInboundException(@NonNull Pipeline<?, ?> pipeline,
                             @NonNull Throwable cause) throws Throwable;

  void catchOutboundException(@NonNull Pipeline<?, ?> pipeline,
                              @NonNull Throwable cause) throws Throwable;

  void onReading(@NonNull Pipeline<?, ?> pipeline,
                 @NonNull Object toRead,
                 @NonNull List<Object> toForward) throws Exception;


  void onWriting(@NonNull Pipeline<?, ?> pipeline,
                 @NonNull Object toWrite,
                 @NonNull List<Object> toForward) throws Exception;

  void onAddedToPipeline(@NonNull Pipeline<?, ?> pipeline);

  void onRemovedFromPipeline(@NonNull Pipeline<?, ?> pipeline);
}