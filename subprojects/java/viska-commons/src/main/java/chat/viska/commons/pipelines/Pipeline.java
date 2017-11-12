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

import chat.viska.commons.reactive.MutableReactiveObject;
import chat.viska.commons.reactive.ReactiveObject;
import io.reactivex.Flowable;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.annotation.concurrent.ThreadSafe;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.concurrent.ConcurrentUtils;

/**
 * Serial container for a series of data processors (a.k.a {@link Pipe}s). This
 * class represents a full duplex pipeline where the reading and writing is
 * happening at the same time in 2 different {@link Thread}s.
 * For any moment, there is only one reading thread and one writing thread
 * running. As a result, Pipes can be dynamically added to, removed from or
 * replaced in the Pipeline while it is running. The manipulations to these
 * Pipes will only happen when neither of the reading or writing thread is
 * running.
 *
 * <p>Because of the multi-thread nature, all methods of this class are
 * non-blocking, and both the Pipes and the Pipeline must be designed as
 * thread-safe.</p>
 *
 * <p>Beware that although a {@link Pipe} is safe to invoke any methods of this
 * class, it must not wait for the operation to finish, otherwise expect
 * deadlocks.</p>
 * @param <I> Type of the inbound output.
 * @param <O> Type of the outbound output.
 */
@ThreadSafe
public class Pipeline<I, O> implements Iterable<Map.Entry<String, Pipe>> {

  /**
   * States of a {@link Pipeline}.
   */
  public enum State {

    /**
     * Indicates a {@link Pipeline} is running.
     */
    RUNNING,

    /**
     * Indicates a {@link Pipeline} has stopped.
     */
    STOPPED,

    /**
     * Indicates a {@link Pipeline} is stopping, which means
     * {@link #stopGracefully()} is invoked.
     */
    STOPPING,

    /**
     * Indicates a {@link Pipeline} is disposed of and can no longer be reused.
     */
    DISPOSED
  }

  private final ExecutorService threadpool = Executors.newCachedThreadPool();
  private final LinkedList<Map.Entry<String, Pipe>> pipes = new LinkedList<>();
  private final FlowableProcessor<I> inboundStream;
  private final FlowableProcessor<Throwable> inboundExceptionStream;
  private final FlowableProcessor<O> outboundStream;
  private final FlowableProcessor<Throwable> outboundExceptionStream;
  private final BlockingQueue<Object> readQueue = new LinkedBlockingQueue<>();
  private final BlockingQueue<Object> writeQueue = new LinkedBlockingQueue<>();
  private final ReadWriteLock pipeLock = new ReentrantReadWriteLock(true);
  private final MutableReactiveObject<State> state = new MutableReactiveObject<>(State.STOPPED);
  private Future<Void> readTask;
  private Future<Void> writeTask;
  private Object readingObject;
  private Object writingObject;

  private void processObject(@Nonnull final Object obj, final boolean isReading)
      throws InterruptedException {
    final ListIterator<Map.Entry<String, Pipe>> iterator = isReading
        ? pipes.listIterator()
        : pipes.listIterator(pipes.size());
    final List<Object> cache = new ArrayList<>();
    cache.add(obj);
    while (isReading ? iterator.hasNext() : iterator.hasPrevious()) {
      final Pipe pipe = isReading
          ? iterator.next().getValue()
          : iterator.previous().getValue();
      final List<Object> toForward = new ArrayList<>();
      for (Object it : cache) {
        final List<Object> out = new ArrayList<>();
        try {
          if (isReading) {
            pipe.onReading(this, it, out);
          } else {
            pipe.onWriting(this, it, out);
          }
        } catch (Throwable cause) {
          processException(iterator, cause, isReading);
          return;
        }
        toForward.addAll(out);
      }
      if (toForward.size() == 0) {
        return;
      } else {
        cache.clear();
        cache.addAll(toForward);
      }
    }
    for (Object it : cache) {
      try {
        if (isReading) {
          inboundStream.onNext((I) it);
        } else {
          outboundStream.onNext((O) it);
        }
      } catch (ClassCastException ex) {
        continue;
      }
    }
  }

  private void processException(@Nonnull ListIterator<Map.Entry<String, Pipe>> iterator,
                                @Nonnull Throwable cause,
                                boolean isReading) {
    while (isReading ? iterator.hasNext() : iterator.hasPrevious()) {
      final Pipe pipe = isReading ? iterator.next().getValue() : iterator.previous().getValue();
      try {
        if (isReading) {
          pipe.catchInboundException(this, cause);
        } else {
          pipe.catchOutboundException(this, cause);
        }
        return;
      } catch (Throwable rethrown) {
        cause = rethrown;
      }
    }
    if (cause != null) {
      if (isReading) {
        inboundExceptionStream.onNext(cause);
      } else {
        outboundExceptionStream.onNext(cause);
      }
    }
  }

  @Nullable
  private ListIterator<Map.Entry<String, Pipe>>
  getIteratorOf(@Nonnull final String name) {
    if (StringUtils.isBlank(name)) {
      return null;
    }
    final ListIterator<Map.Entry<String, Pipe>> iterator = pipes.listIterator();
    while (iterator.hasNext()) {
      Map.Entry entry = iterator.next();
      if (entry.getKey().equals(name)) {
        iterator.previous();
        return iterator;
      }
    }
    return null;
  }

  @Nullable
  private ListIterator<Map.Entry<String, Pipe>>
  getIteratorOf(@Nonnull final Pipe pipe) {
    if (pipe == null) {
      return null;
    }
    final ListIterator<Map.Entry<String, Pipe>> iterator = pipes.listIterator();
    while (iterator.hasNext()) {
      Map.Entry entry = iterator.next();
      if (entry.getValue().equals(pipe)) {
        iterator.previous();
        return iterator;
      }
    }
    return null;
  }

  public Pipeline() {
    final FlowableProcessor<I> unsafeInboundStream = PublishProcessor.create();
    inboundStream = unsafeInboundStream.toSerialized();
    final FlowableProcessor<Throwable> unsafeInboundExceptionStream = PublishProcessor.create();
    inboundExceptionStream = unsafeInboundExceptionStream.toSerialized();
    final FlowableProcessor<O> unsafeOutboundStream = PublishProcessor.create();
    outboundStream = unsafeOutboundStream.toSerialized();
    final FlowableProcessor<Throwable> unsafeOutboundExceptionStream = PublishProcessor.create();
    outboundExceptionStream = unsafeOutboundExceptionStream.toSerialized();
  }

  /**
   * Gets the state.
   */
  @Nonnull
  public ReactiveObject<State> getState() {
    return state;
  }

  /**
   * Starts the pipeline.
   */
  public synchronized void start() {
    synchronized (state) {
      switch (state.getValue()) {
        case RUNNING:
          return;
        case DISPOSED:
          throw new IllegalStateException();
        default:
          break;
      }
      state.changeValue(State.RUNNING);
    }
    this.readTask = this.threadpool.submit(() -> {
      while (true) {
        if (this.state.getValue() != State.RUNNING) {
          return null;
        }
        if (this.readingObject == null) {
          this.readingObject = readQueue.take();
        }
        this.pipeLock.readLock().lockInterruptibly();
        try {
          processObject(this.readingObject, true);
        } finally {
          this.pipeLock.readLock().unlock();
        }
        this.readingObject = null;
        if (this.state.getValue() != State.RUNNING) {
          return null;
        }
      }
    });
    this.writeTask = this.threadpool.submit(() -> {
      while (true) {
        if (this.state.getValue() != State.RUNNING) {
          return null;
        }
        if (this.writingObject == null) {
          this.writingObject = writeQueue.take();
        }
        pipeLock.readLock().lockInterruptibly();
        try {
          processObject(this.writingObject, false);
        } finally {
          this.pipeLock.readLock().unlock();
        }
        this.writingObject = null;
        if (this.state.getValue() != State.RUNNING) {
          return null;
        }
      }
    });
  }

  /**
   * Stops the pipeline after the currently running reading task and writing
   * task finish.
   * @return Token to track the completion of this method. Canceling it will
   *         stop from waiting but the pipeline will still stop after the
   *         reading and writing threads finish.
   */
  public Future<?> stopGracefully() {
    synchronized (state) {
      switch (state.getValue()) {
        case STOPPING:
          return ConcurrentUtils.constantFuture(null);
        case STOPPED:
          return ConcurrentUtils.constantFuture(null);
        case DISPOSED:
          return ConcurrentUtils.constantFuture(null);
        default:
          state.changeValue(State.STOPPING);
      }
    }
    return threadpool.submit(() -> {
      if (this.readingObject != null) {
        readTask.get();
      } else {
        readTask.cancel(true);
      }
      if (this.writingObject != null) {
        writeTask.get();
      } else {
        writeTask.cancel(true);
      }
      state.changeValue(State.STOPPED);
      return null;
    });
  }

  /**
   * Stops the Pipeline immediately by killing the reading and writing threads.
   * The data being processed at the time will retain and be re-processed after
   * the pipeline starts again.
   */
  public void stopNow() {
    synchronized (state) {
      switch (state.getValue()) {
        case DISPOSED:
          return;
        case STOPPED:
          return;
        default:
          break;
      }
    }
    if (!readTask.isDone()) {
      readTask.cancel(true);
    }
    if (!writeTask.isDone()) {
      writeTask.cancel(true);
    }
    state.changeValue(State.STOPPED);
  }

  /**
   * Disposes of the pipeline.
   */
  public void dispose() {
    synchronized (state) {
      switch (state.getValue()) {
        case RUNNING:
          stopNow();
          break;
        case DISPOSED:
          return;
        default:
          state.changeValue(State.DISPOSED);
          break;
      }
    }
    clearQueues();
    threadpool.shutdownNow();
    inboundStream.onComplete();
    inboundExceptionStream.onComplete();
    outboundStream.onComplete();
    outboundExceptionStream.onComplete();
    readingObject = null;
    writingObject = null;
    for (Map.Entry<String, Pipe> entry : pipes) {
      entry.getValue().onRemovedFromPipeline(this);
    }
    pipes.clear();
  }

  @Nonnull
  public Future<Void> addTowardsInboundEnd(@Nonnull final String previous,
                                           @Nullable final String name,
                                           @Nonnull final Pipe pipe) {
    Validate.notBlank(previous);
    Objects.requireNonNull(pipe);
    return threadpool.submit(() -> {
      pipeLock.writeLock().lockInterruptibly();
      try {
        if (getIteratorOf(name) != null) {
          throw new IllegalArgumentException("Name collision: " + name);
        }
        ListIterator<Map.Entry<String, Pipe>> iterator = getIteratorOf(previous);
        if (iterator == null) {
          throw new NoSuchElementException(previous);
        }
        iterator.next();
        iterator.add(new AbstractMap.SimpleImmutableEntry<>(
            name == null ? "" : name,
            pipe
        ));
        pipe.onAddedToPipeline(this);
      } finally {
        pipeLock.writeLock().unlock();
      }
      return null;
    });
  }

  @Nonnull
  public Future<Void> addTowardsInboundEnd(@Nonnull final Pipe previous,
                                           @Nullable final String name,
                                           @Nonnull final Pipe pipe) {
    Objects.requireNonNull(previous);
    Objects.requireNonNull(pipe);
    return threadpool.submit(() -> {
      pipeLock.writeLock().lockInterruptibly();
      try {
        if (getIteratorOf(name) != null) {
          throw new IllegalArgumentException("Name collision: " + name);
        }
        ListIterator<Map.Entry<String, Pipe>> iterator = getIteratorOf(previous);
        if (iterator == null) {
          throw new NoSuchElementException();
        }
        iterator.next();
        iterator.add(new AbstractMap.SimpleImmutableEntry<>(
            name == null ? "" : name,
            pipe
        ));
        pipe.onAddedToPipeline(this);
      } finally {
        pipeLock.writeLock().unlock();
      }
      return null;
    });
  }

  @Nonnull
  public Future<Void> addTowardsOutboundEnd(@Nonnull final String next,
                                            @Nullable final String name,
                                            @Nonnull final Pipe pipe) {
    Validate.notBlank(next);
    Objects.requireNonNull(pipe);
    return threadpool.submit(() -> {
      pipeLock.writeLock().lockInterruptibly();
      try {
        if (getIteratorOf(name) != null) {
          throw new IllegalArgumentException("Name collision: " + name);
        }
        ListIterator<Map.Entry<String, Pipe>> iterator = getIteratorOf(next);
        if (iterator == null) {
          throw new NoSuchElementException(next);
        }
        iterator.add(new AbstractMap.SimpleImmutableEntry<>(
            name == null ? "" : name,
            pipe
        ));
        pipe.onAddedToPipeline(this);
      } finally {
        pipeLock.writeLock().unlock();
      }
      return null;
    });
  }

  @Nonnull
  public Future<Void> addTowardsOutboundEnd(@Nonnull final Pipe next,
                                            @Nullable final String name,
                                            @Nonnull final Pipe pipe) {
    Objects.requireNonNull(next);
    Objects.requireNonNull(pipe);
    return threadpool.submit(() -> {
      pipeLock.writeLock().lockInterruptibly();
      try {
        if (getIteratorOf(name) != null) {
          throw new IllegalArgumentException("Name collision: " + name);
        }
        ListIterator<Map.Entry<String, Pipe>> iterator = getIteratorOf(next);
        if (iterator == null) {
          throw new NoSuchElementException();
        }
        iterator.add(new AbstractMap.SimpleImmutableEntry<>(
            name == null ? "" : name,
            pipe
        ));
        pipe.onAddedToPipeline(this);
      } finally {
        pipeLock.writeLock().unlock();
      }
      return null;
    });
  }

  /**
   * Adds a {@link Pipe} at the outbound end.
   */
  @Nonnull
  public Future<?> addAtOutboundEnd(@Nullable final String name,
                                    @Nonnull final Pipe pipe) {
    Objects.requireNonNull(pipe);
    return threadpool.submit(() -> {
      pipeLock.writeLock().lockInterruptibly();
      try {
        if (getIteratorOf(name) != null) {
          throw new IllegalArgumentException("Name collision: " + name);
        }
        pipes.addFirst(new AbstractMap.SimpleImmutableEntry<>(
            name == null ? "" : name,
            pipe
        ));
        pipe.onAddedToPipeline(this);
      } finally {
        pipeLock.writeLock().unlock();
      }
      return null;
    });
  }

  /**
   * Adds a {@link Pipe} at the inbound end.
   */
  @Nonnull
  public Future<?> addAtInboundEnd(@Nullable final String name,
                                   @Nonnull final Pipe pipe) {
    Objects.requireNonNull(pipe);
    return threadpool.submit(() -> {
      pipeLock.writeLock().lockInterruptibly();
      try {
        if (getIteratorOf(name) != null) {
          throw new IllegalArgumentException("Name collision: " + name);
        }
        pipes.addLast(new AbstractMap.SimpleImmutableEntry<>(
            name == null ? "" : name,
            pipe
        ));
        pipe.onAddedToPipeline(this);
      } finally {
        pipeLock.writeLock().unlock();
      }
      return null;
    });
  }

  /**
   * Clears the read queue and write queue.
   */
  public void clearQueues() {
    readQueue.clear();
    writeQueue.clear();
  }

  /**
   * Removes all {@link Pipe}s.
   * @return Token to track the completion of this method or cancel it.
   */
  @Nonnull
  public Future<?> removeAll() {
    return threadpool.submit(() -> {
      pipeLock.writeLock().lockInterruptibly();
      pipes.clear();
      pipeLock.writeLock().unlock();
      return null;
    });
  }

  /**
   * Removes a {@link Pipe}.
   */
  @Nonnull
  public Future<Pipe> remove(@Nonnull final String name) {
    Validate.notBlank(name);
    return threadpool.submit(() -> {
      final Pipe pipe;
      pipeLock.writeLock().lockInterruptibly();
      try {
        final ListIterator<Map.Entry<String, Pipe>> iterator = getIteratorOf(name);
        if (iterator == null) {
          return null;
        }
        pipe = iterator.next().getValue();
        iterator.remove();
        pipe.onRemovedFromPipeline(this);
      } finally {
        pipeLock.writeLock().unlock();
      }
      return pipe;
    });
  }

  /**
   * Removes a {@link Pipe}. Fails silently if the specified {@link Pipe} does
   * not exist in the pipeline.
   */
  @Nonnull
  public Future<Pipe> remove(@Nonnull final Pipe pipe) {
    Objects.requireNonNull(pipe);
    return threadpool.submit(() -> {
      pipeLock.writeLock().lockInterruptibly();
      try {
        final ListIterator iterator = getIteratorOf(pipe);
        if (iterator == null) {
          return null;
        }
        iterator.next(); // For the remove() to work
        iterator.remove();
        pipe.onRemovedFromPipeline(this);
      } finally {
        pipeLock.writeLock().unlock();
      }
      return pipe;
    });
  }

  @Nonnull
  public Future<Pipe> removeAtOutboundEnd() {
    return threadpool.submit(() -> {
      final Pipe pipe;
      pipeLock.writeLock().lockInterruptibly();
      try {
        pipe = pipes.pollFirst().getValue();
        if (pipe != null) {
          pipe.onRemovedFromPipeline(this);
        }
      } finally {
        pipeLock.writeLock().unlock();
      }
      return pipe;
    });
  }

  @Nonnull
  public Future<Pipe> removeAtInboundEnd() {
    return threadpool.submit(() -> {
      pipeLock.writeLock().lockInterruptibly();
      final Pipe pipe;
      try {
        pipe = pipes.pollLast().getValue();
        if (pipe != null) {
          pipe.onRemovedFromPipeline(this);
        }
      } finally {
        pipeLock.writeLock().unlock();
      }
      return pipe;
    });
  }

  @Nonnull
  public Future<Pipe> replace(@Nonnull final String name,
                              @Nonnull final Pipe newPipe) {
    Validate.notBlank(name);
    Objects.requireNonNull(newPipe);
    return threadpool.submit(() -> {
      pipeLock.writeLock().lockInterruptibly();
      final Pipe oldPipe;
      try {
        final ListIterator<Map.Entry<String, Pipe>> iterator = getIteratorOf(name);
        if (iterator == null) {
          throw new NoSuchElementException(name);
        }
        oldPipe = iterator.next().getValue();
        iterator.set(new AbstractMap.SimpleImmutableEntry<>(
            name,
            newPipe
        ));
        oldPipe.onRemovedFromPipeline(this);
        newPipe.onAddedToPipeline(this);
      } finally {
        pipeLock.writeLock().unlock();
      }
      return oldPipe;
    });
  }

  @Nonnull
  public Future<Pipe> replace(@Nonnull final Pipe oldPipe,
                              @Nonnull final Pipe newPipe) {
    Objects.requireNonNull(oldPipe);
    Objects.requireNonNull(newPipe);
    return threadpool.submit(() -> {
      pipeLock.writeLock().lockInterruptibly();
      try {
        final ListIterator<Map.Entry<String, Pipe>> iterator = getIteratorOf(oldPipe);
        if (iterator == null) {
          throw new NoSuchElementException();
        }
        Map.Entry<String, Pipe> oldEntry = iterator.next();
        iterator.set(new AbstractMap.SimpleImmutableEntry<>(
            oldEntry.getKey(),
            newPipe
        ));
        oldPipe.onRemovedFromPipeline(this);
        newPipe.onAddedToPipeline(this);
      } finally {
        pipeLock.writeLock().unlock();
      }
      return oldPipe;
    });
  }

  /**
   * Feeds a data at the outbound end.
   * @throws IllegalStateException If the pipeline is disposed of.
   */
  public void read(@Nonnull final Object obj) {
    if (state.getValue() == State.DISPOSED) {
      throw new IllegalStateException("Pipeline disposed.");
    }
    readQueue.add(obj);
  }

  /**
   * Feeds a data at the inbound end.
   * @throws IllegalStateException If the pipeline is disposed of.
   */
  public void write(@Nonnull final Object obj) {
    if (state.getValue() == State.DISPOSED) {
      throw new IllegalStateException("Pipeline disposed.");
    }
    writeQueue.add(obj);
  }

  @Nonnull
  public Pipe get(@Nullable final String name) {
    for (Map.Entry<String, Pipe> it : pipes) {
      if (it.getKey().equals(name)) {
        return it.getValue();
      }
    }
    return null;
  }

  @Nullable
  public Pipe getFirst() {
    return pipes.peekFirst().getValue();
  }

  @Nullable
  public Pipe getLast() {
    return pipes.peekLast().getValue();
  }

  @Nonnull
  public Flowable<I> getInboundStream() {
    return inboundStream;
  }

  @Nonnull
  public Flowable<Throwable> getInboundExceptionStream() {
    return inboundExceptionStream;
  }

  @Nonnull
  public Flowable<O> getOutboundStream() {
    return outboundStream;
  }

  @Nonnull
  public Flowable<Throwable> getOutboundExceptionStream() {
    return outboundExceptionStream;
  }

  @Override
  public Iterator<Map.Entry<String, Pipe>> iterator() {
    return pipes.iterator();
  }
}