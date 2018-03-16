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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Pipe as in a {@link Pipeline}.
 */
@ThreadSafe
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

  /**
   * Invoked when the Pipe received an exception in the reading direction.
   * @param pipeline Attached {@link Pipeline}.
   * @param cause Received exception.
   * @throws Throwable If the Pipe decides to forward the exception.
   */
  void catchInboundException(Pipeline<?, ?> pipeline, Throwable cause) throws Throwable;

  /**
   * Invoked when the Pipe received an exception in the writing direction.
   * @param pipeline Attached {@link Pipeline}.
   * @param cause Received exception.
   * @throws Throwable If the Pipe decides to forward the exception.
   */
  void catchOutboundException(Pipeline<?, ?> pipeline, Throwable cause) throws Throwable;

  /**
   * Invoked when the Pipe is reading data.
   * @param pipeline Attached {@link Pipeline}.
   * @param toRead The data it is reading.
   * @param toForward An output placeholder for forwarding.
   * @throws Exception If any {@link Exception} is thrown during the reading.
   */
  void onReading(Pipeline<?, ?> pipeline, Object toRead, List<Object> toForward) throws Exception;


  /**
   * Invoked when the Pipe is writing data.
   * @param pipeline Attached {@link Pipeline}.
   * @param toWrite Data it is writing.
   * @param toForward Output placeholder for forwarding.
   * @throws Exception If any {@link Exception} is thrown during the writing.
   */
  void onWriting(Pipeline<?, ?> pipeline, Object toWrite, List<Object> toForward) throws Exception;

  /**
   * Invoked when the Pipe is inserted into a {@link Pipeline}.
   * @param pipeline Attached {@link Pipeline}.
   */
  void onAddedToPipeline(Pipeline<?, ?> pipeline);

  /**
   * Invoked when the Pipe is removed from a {@link Pipeline}.
   */
  void onRemovedFromPipeline(Pipeline<?, ?> pipeline);
}