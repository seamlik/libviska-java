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

import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.commons.lang3.Validate;

public class Pipeline<I, O> implements Iterable<Map.Entry<String, Pipe>> {

  public enum State {
    RUNNING,
    SHUTDOWN,
    STOPPED
  }

  private static final ExecutorService THREAD_POOL_INSTANCE = Executors.newCachedThreadPool();

  private final LinkedList<Map.Entry<String, Pipe>> pipes = new LinkedList<>();
  private final Subject<I> inboundStream;
  private final Subject<Throwable> inboundExceptionStream;
  private final Subject<O> outboundStream;
  private final Subject<Throwable> outboundExceptionStream;
  private final BlockingQueue<Object> readQueue = new LinkedBlockingQueue<>();
  private final BlockingQueue<Object> writeQueue = new LinkedBlockingQueue<>();
  private State state = State.STOPPED;
  private ReadWriteLock pipeLock = new ReentrantReadWriteLock(true);
  private Future<Void> readTask;
  private Future<Void> writeTask;

  private void processObject(@NonNull Object obj, boolean isReading)
      throws InterruptedException {
    final ListIterator<Map.Entry<String, Pipe>> iterator = isReading
        ? pipes.listIterator()
        : pipes.listIterator(pipes.size());
    final List<Object> cache = new ArrayList<>();
    cache.add(obj);
    while (isReading ? iterator.hasNext() : iterator.hasPrevious()) {
      final Pipe pipe = isReading ? iterator.next().getValue() : iterator.previous().getValue();
      final List<Object> toForward = new ArrayList<>();
      for (Object it : cache) {
        final List<Object> out = new ArrayList<>();
        try {
          if (isReading) {
            pipe.read(this, it, out);
          } else {
            pipe.write(this, it, out);
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

  private void processException(@NonNull ListIterator<Map.Entry<String, Pipe>> iterator,
                                @NonNull Throwable cause,
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

  private @Nullable ListIterator<Map.Entry<String, Pipe>> getIteratorOf(@NonNull String name) {
    if (name == null || name.trim().isEmpty()) {
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

  private @Nullable ListIterator<Map.Entry<String, Pipe>> getIteratorOf(@NonNull Pipe pipe) {
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
    final PublishSubject<I> unsafeInboundStream = PublishSubject.create();
    inboundStream = unsafeInboundStream.toSerialized();
    final PublishSubject<Throwable> unsafeInboundExceptionStream = PublishSubject.create();
    inboundExceptionStream = unsafeInboundExceptionStream.toSerialized();
    final PublishSubject<O> unsafeOutboundStream = PublishSubject.create();
    outboundStream = unsafeOutboundStream.toSerialized();
    final PublishSubject<Throwable> unsafeOutboundExceptionStream = PublishSubject.create();
    outboundExceptionStream = unsafeOutboundExceptionStream.toSerialized();
  }

  public @NonNull State getState() {
    return state;
  }

  public void start() {
    switch (state) {
      case RUNNING:
        return;
      case SHUTDOWN:
        throw new IllegalStateException();
      default:
        break;
    }
    state = State.RUNNING;
    readTask = THREAD_POOL_INSTANCE.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        while (true) {
          switch (state) {
            case STOPPED:
              return null;
            case SHUTDOWN:
              return null;
            default:
              break;
          }
          Object obj = readQueue.take();
          pipeLock.readLock().lock();
          try {
            processObject(obj, true);
          } catch (InterruptedException ex) {
            pipeLock.readLock().unlock();
            throw ex;
          }
          pipeLock.readLock().unlock();
        }
      }
    });
    writeTask = THREAD_POOL_INSTANCE.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        while (true) {
          switch (state) {
            case STOPPED:
              return null;
            case SHUTDOWN:
              return null;
            default:
              break;
          }
          Object obj = writeQueue.take();
          pipeLock.readLock().lock();
          try {
            processObject(obj, false);
          } catch (InterruptedException ex) {
            pipeLock.readLock().unlock();
            throw ex;
          }
          pipeLock.readLock().unlock();
        }
      }
    });
  }

  public void stop() {
    switch (state) {
      case STOPPED:
        return;
      case SHUTDOWN:
        return;
      default:
        break;
    }
    // The tasks will stop themselves by checking the state. But if they are
    // already waiting & taking an object from the queues, they still need to be
    // manually killed.
    state = State.STOPPED;
    if (!readTask.isDone()) {
      readTask.cancel(true);
    }
    if (!writeTask.isDone()) {
      writeTask.cancel(true);
    }
  }

  public void shutdown() {
    switch (state) {
      case RUNNING:
        stop();
      case SHUTDOWN:
        return;
      default:
        break;
    }
    readQueue.clear();
    writeQueue.clear();
    inboundStream.onComplete();
    inboundExceptionStream.onComplete();
    outboundStream.onComplete();
    outboundExceptionStream.onComplete();
    THREAD_POOL_INSTANCE.shutdown();
    state = State.SHUTDOWN;
  }

  public Future<Void> addAfter(final @NonNull String previous,
                               final @Nullable String name,
                               final @NonNull Pipe pipe) {
    Validate.notBlank(previous);
    Validate.notNull(pipe);
    final Pipeline thisPipeline = this;
    return THREAD_POOL_INSTANCE.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        pipeLock.writeLock().lock();
        try {
          if (getIteratorOf(name) != null) {
            throw new IllegalArgumentException(name);
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
          pipe.onAddedToPipeline(thisPipeline);
        } catch (Throwable cause) {
          pipeLock.writeLock().unlock();
          throw cause;
        }
        pipeLock.writeLock().unlock();
        return null;
      }
    });
  }

  public @NonNull Future<Void> addAfter(final @NonNull Pipe previous,
                                        final @Nullable String name,
                                        final @NonNull Pipe pipe) {
    Validate.notNull(previous);
    Validate.notNull(pipe);
    final Pipeline thisPipeline = this;
    return THREAD_POOL_INSTANCE.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        pipeLock.writeLock().lock();
        try {
          if (getIteratorOf(name) != null) {
            throw new IllegalArgumentException(name);
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
          pipe.onAddedToPipeline(thisPipeline);
        } catch (Throwable cause) {
          pipeLock.writeLock().unlock();
          throw cause;
        }
        pipeLock.writeLock().unlock();
        return null;
      }
    });
  }

  public @NonNull Future<Void> addBefore(final @NonNull String next,
                                         final @Nullable String name,
                                         final @NonNull Pipe pipe) {
    Validate.notBlank(next);
    Validate.notNull(pipe);
    final Pipeline thisPipeline = this;
    return THREAD_POOL_INSTANCE.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        pipeLock.writeLock().lock();
        try {
          if (getIteratorOf(name) != null) {
            throw new IllegalArgumentException(name);
          }
          ListIterator<Map.Entry<String, Pipe>> iterator = getIteratorOf(next);
          if (iterator == null) {
            throw new NoSuchElementException(next);
          }
          iterator.add(new AbstractMap.SimpleImmutableEntry<>(
              name == null ? "" : name,
              pipe
          ));
          pipe.onAddedToPipeline(thisPipeline);
        } catch (Throwable cause) {
          pipeLock.writeLock().unlock();
          throw cause;
        }
        pipeLock.writeLock().unlock();
        return null;
      }
    });
  }

  public @NonNull Future<Void> addBefore(final @NonNull Pipe next,
                                         final @Nullable String name,
                                         final @NonNull Pipe pipe) {
    Validate.notNull(next);
    Validate.notNull(pipe);
    final Pipeline thisPipeline = this;
    return THREAD_POOL_INSTANCE.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        pipeLock.writeLock().lock();
        try {
          if (getIteratorOf(name) != null) {
            throw new IllegalArgumentException(name);
          }
          ListIterator<Map.Entry<String, Pipe>> iterator = getIteratorOf(next);
          if (iterator == null) {
            throw new NoSuchElementException();
          }
          iterator.add(new AbstractMap.SimpleImmutableEntry<>(
              name == null ? "" : name,
              pipe
          ));
          pipe.onAddedToPipeline(thisPipeline);
        } catch (Throwable cause) {
          pipeLock.writeLock().unlock();
          throw cause;
        }
        pipeLock.writeLock().unlock();
        return null;
      }
    });
  }

  public @NonNull Future<Void> addFirst(final @Nullable String name,
                                        final @NonNull Pipe pipe) {
    Validate.notNull(pipe);
    final Pipeline thisPipeline = this;
    return THREAD_POOL_INSTANCE.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        pipeLock.writeLock().lock();
        try {
          if (getIteratorOf(name) != null) {
            throw new IllegalArgumentException(name);
          }
          pipes.addFirst(new AbstractMap.SimpleImmutableEntry<>(
              name == null ? "" : name,
              pipe
          ));
          pipe.onAddedToPipeline(thisPipeline);
        } catch (Throwable cause) {
          pipeLock.writeLock().unlock();
          throw cause;
        }
        pipeLock.writeLock().unlock();
        return null;
      }
    });
  }

  public @NonNull Future<Void> addLast(final @Nullable String name,
                                       final @NonNull Pipe pipe) {
    Validate.notNull(pipe);
    final Pipeline thisPipeline = this;
    return THREAD_POOL_INSTANCE.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        pipeLock.writeLock().lock();
        try {
          if (getIteratorOf(name) != null) {
            throw new IllegalArgumentException(name);
          }
          pipes.addLast(new AbstractMap.SimpleImmutableEntry<>(
              name == null ? "" : name,
              pipe
          ));
          pipe.onAddedToPipeline(thisPipeline);
        } catch (Throwable cause) {
          pipeLock.writeLock().unlock();
          throw cause;
        }
        pipeLock.writeLock().unlock();
        return null;
      }
    });
  }

  public @NonNull Future<Pipe> remove(final @NonNull String name) {
    Validate.notBlank(name);
    final Pipeline thisPipeline = this;
    return THREAD_POOL_INSTANCE.submit(new Callable<Pipe>() {
      @Override
      public Pipe call() throws Exception {
        final Pipe pipe;
        pipeLock.writeLock().lock();
        try {
          final ListIterator<Map.Entry<String, Pipe>> iterator = getIteratorOf(name);
          if (iterator == null) {
            return null;
          }
          pipe = iterator.next().getValue();
          iterator.remove();
          pipe.onRemovedFromPipeline(thisPipeline);
        } catch (Throwable cause) {
          pipeLock.writeLock().unlock();
          throw cause;
        }
        pipeLock.writeLock().unlock();
        return pipe;
      }
    });
  }

  public @NonNull Future<Pipe> remove(final @NonNull Pipe pipe) {
    Validate.notNull(pipe);
    final Pipeline thisPipeline = this;
    return THREAD_POOL_INSTANCE.submit(new Callable<Pipe>() {
      @Override
      public Pipe call() throws Exception {
        pipeLock.writeLock().lock();
        try {
          final ListIterator iterator = getIteratorOf(pipe);
          if (iterator == null) {
            return null;
          }
          iterator.next(); // For the remove() to work
          iterator.remove();
          pipe.onRemovedFromPipeline(thisPipeline);
        } catch (Throwable cause) {
          pipeLock.writeLock().unlock();
          throw cause;
        }
        pipeLock.writeLock().unlock();
        return pipe;
      }
    });
  }

  public @NonNull Future<Pipe> removeFirst() {
    final Pipeline thisPipeline = this;
    return THREAD_POOL_INSTANCE.submit(new Callable<Pipe>() {
      @Override
      public Pipe call() throws Exception {
        final Pipe pipe;
        pipeLock.writeLock().lock();
        try {
          pipe = pipes.pollFirst().getValue();
          if (pipe != null) {
            pipe.onRemovedFromPipeline(thisPipeline);
          }
        } catch (Throwable cause) {
          pipeLock.writeLock().unlock();
          throw cause;
        }
        pipeLock.writeLock().unlock();
        return pipe;
      }
    });
  }

  public @NonNull Future<Pipe> removeLast() {
    final Pipeline thisPipeline = this;
    return THREAD_POOL_INSTANCE.submit(new Callable<Pipe>() {
      @Override
      public Pipe call() throws Exception {
        pipeLock.writeLock().lock();
        final Pipe pipe;
        try {
          pipe = pipes.pollLast().getValue();
          if (pipe != null) {
            pipe.onRemovedFromPipeline(thisPipeline);
          }
        } catch (Throwable cause) {
          pipeLock.writeLock().unlock();
          throw cause;
        }
        pipeLock.writeLock().unlock();
        return pipe;
      }
    });
  }

  public @NonNull Future<Pipe> replace(@NonNull final String oldName,
                                       @NonNull final Pipe newPipe) {
    Validate.notBlank(oldName);
    Validate.notNull(newPipe);
    final Pipeline thisPipeline = this;
    return THREAD_POOL_INSTANCE.submit(new Callable<Pipe>() {
      @Override
      public Pipe call() throws Exception {
        pipeLock.writeLock().lock();
        final Pipe oldPipe;
        try {
          final ListIterator<Map.Entry<String, Pipe>> iterator = getIteratorOf(oldName);
          if (iterator == null) {
            throw new NoSuchElementException(oldName);
          }
          oldPipe = iterator.next().getValue();
          iterator.set(new AbstractMap.SimpleImmutableEntry<>(
              oldName,
              newPipe
          ));
          oldPipe.onRemovedFromPipeline(thisPipeline);
          newPipe.onAddedToPipeline(thisPipeline);
        } catch (Throwable cause) {
          pipeLock.writeLock().unlock();
          throw cause;
        }
        pipeLock.writeLock().unlock();
        return oldPipe;
      }
    });
  }

  public @NonNull Future<Pipe> replace(final @NonNull Pipe oldPipe,
                                       final @NonNull Pipe newPipe) {
    Validate.notNull(oldPipe);
    Validate.notNull(newPipe);
    final Pipeline thisPipeline = this;
    return THREAD_POOL_INSTANCE.submit(new Callable<Pipe>() {
      @Override
      public Pipe call() throws Exception {
        pipeLock.writeLock().lock();
        try {
          final ListIterator<Map.Entry<String, Pipe>> iterator = getIteratorOf(oldPipe);
          if (iterator == null) {
            throw new NoSuchElementException();
          }
          Map.Entry<String, Pipe> oldEntry = iterator.next();
          iterator.set(new AbstractMap.SimpleImmutableEntry<String, Pipe>(
              oldEntry.getKey(),
              newPipe
          ));
          oldPipe.onRemovedFromPipeline(thisPipeline);
          newPipe.onAddedToPipeline(thisPipeline);
        } catch (Throwable cause) {
          pipeLock.writeLock().unlock();
          throw cause;
        }
        return oldPipe;
      }
    });
  }

  public void read(@NonNull Object obj) {
    readQueue.add(obj);
  }

  public void write(@NonNull Object obj) {
    writeQueue.add(obj);
  }

  public @Nullable Pipe get(String name) {
    for(Map.Entry<String, Pipe> it : pipes) {
      if (it.getKey().equals(name)) {
        return it.getValue();
      }
    }
    return null;
  }

  public @Nullable Pipe getFirst() {
    return pipes.peekFirst().getValue();
  }

  public @Nullable Pipe getLast() {
    return pipes.peekLast().getValue();
  }

  public @NonNull Observable<I> getInboundStream() {
    return inboundStream;
  }

  public @NonNull Observable<Throwable> getInboundExceptionStream() {
    return inboundExceptionStream;
  }

  public @NonNull Observable<O> getOutboundStream() {
    return outboundStream;
  }

  public @NonNull Observable<Throwable> getOutboundExceptionStream() {
    return outboundExceptionStream;
  }

  @Override
  public Iterator<Map.Entry<String, Pipe>> iterator() {
    return pipes.iterator();
  }
}