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

package chat.viska.commons.pipelines;

import chat.viska.commons.pipelines.Pipe.Shareable;
import java.util.List;

/**
 * {@link Pipe} that does nothing but forwarding anything coming through. It can
 * serve as a parent class for an implementation of {@link Pipe} for convenience
 * or as a placeholder in a {@link Pipeline}.
 */
@Shareable
public class BlankPipe implements Pipe {

  @Override
  public void catchInboundException(final Pipeline<?, ?> pipeline,
                                    final Throwable cause)
      throws Throwable {
    throw cause;
  }

  @Override
  public void catchOutboundException(final Pipeline<?, ?> pipeline,
                                     final Throwable cause)
      throws Throwable {
    throw cause;
  }

  @Override
  public void onReading(final Pipeline<?, ?> pipeline,
                        final Object toRead,
                        final List<Object> toForward)
      throws Exception {
    toForward.add(toRead);
  }

  @Override
  public void onWriting(final Pipeline<?, ?> pipeline,
                        final Object toWrite,
                        final List<Object> toForward)
      throws Exception {
    toForward.add(toWrite);
  }

  @Override
  public void onAddedToPipeline(final Pipeline<?, ?> pipeline) {}

  @Override
  public void onRemovedFromPipeline(final Pipeline<?, ?> pipeline) {}
}