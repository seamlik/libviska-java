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
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.commons.lang3.Validate;

public class Pipeline<I, O> implements Iterable<Map.Entry<String, Pipe>> {

  public enum State {
    RUNNING,
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
  private FutureTask<Void> readTask;
  private FutureTask<Void> writeTask;

  private void processObject(@NonNull Object obj, boolean isReading)
      throws InterruptedException {
    if (state == State.STOPPED) {
      return;
    }
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
    ListIterator<Map.Entry<String, Pipe>> iterator = pipes.listIterator();
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
    ListIterator<Map.Entry<String, Pipe>> iterator = pipes.listIterator();
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
    PublishSubject<I> unsafeInboundStream = PublishSubject.create();
    inboundStream = unsafeInboundStream.toSerialized();
    PublishSubject<Throwable> unsafeInboundExceptionStream = PublishSubject.create();
    inboundExceptionStream = unsafeInboundExceptionStream.toSerialized();
    PublishSubject<O> unsafeOutboundStream = PublishSubject.create();
    outboundStream = unsafeOutboundStream.toSerialized();
    PublishSubject<Throwable> unsafeOutboundExceptionStream = PublishSubject.create();
    outboundExceptionStream = unsafeOutboundExceptionStream.toSerialized();
  }

  public @NonNull State getState() {
    return state;
  }

  public void start() {
    if (state == State.RUNNING) {
      return;
    }
    state = State.RUNNING;
    readTask = new FutureTask<>(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        while (true) {
          if (state == State.STOPPED) {
            return null;
          }
          processObject(readQueue.take(), true);
        }
      }
    });
    writeTask = new FutureTask<>(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        while (true) {
          if (state == State.STOPPED) {
            return null;
          }
          processObject(writeQueue.take(), false);
        }
      }
    });
    THREAD_POOL_INSTANCE.submit(readTask);
    THREAD_POOL_INSTANCE.submit(writeTask);
  }

  public void stop() {
    if (state == State.STOPPED) {
      return;
    }
    state = State.STOPPED;
    if (!readTask.isDone()) {
      readTask.cancel(true);
    }
    if (!writeTask.isDone()) {
      writeTask.cancel(true);
    }
  }

  public Future<Void> addAfter(final @NonNull String previous,
                               final @Nullable String name,
                               final @NonNull Pipe pipe) {
    Validate.notBlank(previous);
    Validate.notNull(pipe);
    FutureTask<Void> task = new FutureTask<>(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        pipeLock.writeLock().lock();
        ListIterator<Map.Entry<String, Pipe>> iterator = getIteratorOf(previous);
        if (iterator == null) {
          throw new NoSuchElementException(previous);
        }
        iterator.next();
        iterator.add(new AbstractMap.SimpleImmutableEntry<>(name, pipe));
        pipeLock.writeLock().unlock();
        return null;
      }
    });
    THREAD_POOL_INSTANCE.submit(task);
    return task;
  }

  public @NonNull Future<Void> addAfter(final @NonNull Pipe previous,
                                        final @Nullable String name,
                                        final @NonNull Pipe pipe) {
    Validate.notNull(previous);
    Validate.notNull(pipe);
    FutureTask<Void> task = new FutureTask<>(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        pipeLock.writeLock().lock();
        ListIterator<Map.Entry<String, Pipe>> iterator = getIteratorOf(previous);
        if (iterator == null) {
          throw new NoSuchElementException();
        }
        iterator.next();
        iterator.add(new AbstractMap.SimpleImmutableEntry<>(name, pipe));
        pipeLock.writeLock().unlock();
        return null;
      }
    });
    THREAD_POOL_INSTANCE.submit(task);
    return task;
  }

  public @NonNull Future<Void> addBefore(final @NonNull String next,
                                         final @Nullable String name,
                                         final @NonNull Pipe pipe) {
    Validate.notBlank(next);
    Validate.notNull(pipe);
    FutureTask<Void> task = new FutureTask<>(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        pipeLock.writeLock().lock();
        ListIterator<Map.Entry<String, Pipe>> iterator = getIteratorOf(next);
        if (iterator == null) {
          throw new NoSuchElementException(next);
        }
        iterator.add(new AbstractMap.SimpleImmutableEntry<>(name, pipe));
        pipeLock.writeLock().unlock();
        return null;
      }
    });
    THREAD_POOL_INSTANCE.submit(task);
    return task;
  }

  public @NonNull Future<Void> addBefore(final @NonNull Pipe next,
                                         final @Nullable String name,
                                         final @NonNull Pipe pipe) {
    Validate.notNull(next);
    Validate.notNull(pipe);
    FutureTask<Void> task = new FutureTask<>(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        pipeLock.writeLock().lock();
        ListIterator<Map.Entry<String, Pipe>> iterator = getIteratorOf(next);
        if (iterator == null) {
          throw new NoSuchElementException();
        }
        iterator.add(new AbstractMap.SimpleImmutableEntry<>(name, pipe));
        pipeLock.writeLock().unlock();
        return null;
      }
    });
    THREAD_POOL_INSTANCE.submit(task);
    return task;
  }

  public @NonNull Future<Void> addFirst(final @Nullable String name,
                                        final @NonNull Pipe pipe) {
    Validate.notNull(pipe);
    FutureTask<Void> task = new FutureTask<>(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        pipeLock.writeLock().lock();
        pipes.addFirst(new AbstractMap.SimpleImmutableEntry<>(name, pipe));
        pipeLock.writeLock().unlock();
        return null;
      }
    });
    THREAD_POOL_INSTANCE.submit(task);
    return task;
  }

  public @NonNull Future<Void> addLast(final @Nullable String name,
                                       final @NonNull Pipe pipe) {
    Validate.notNull(pipe);
    FutureTask<Void> task = new FutureTask<>(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        pipeLock.writeLock().lock();
        pipes.addLast(new AbstractMap.SimpleImmutableEntry<>(name, pipe));
        pipeLock.writeLock().unlock();
        return null;
      }
    });
    THREAD_POOL_INSTANCE.submit(task);
    return task;
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

  public @NonNull Observable<O> getOutboundStream() {
    return outboundStream;
  }

  public @NonNull Future<Boolean> remove(final @NonNull String name) {
    Validate.notBlank(name);
    FutureTask<Boolean> task = new FutureTask<>(new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        pipeLock.writeLock().lock();
        ListIterator iterator = getIteratorOf(name);
        if (iterator == null) {
          return false;
        }
        pipes.remove(iterator.nextIndex());
        pipeLock.writeLock().unlock();
        return true;
      }
    });
    THREAD_POOL_INSTANCE.submit(task);
    return task;
  }

  public @NonNull Future<Boolean> remove(final @NonNull Pipe pipe) {
    Validate.notNull(pipe);
    FutureTask<Boolean> task = new FutureTask<>(new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        pipeLock.writeLock().lock();
        ListIterator iterator = getIteratorOf(pipe);
        if (iterator == null) {
          return false;
        }
        pipes.remove(iterator.nextIndex());
        pipeLock.writeLock().unlock();
        return true;
      }
    });
    THREAD_POOL_INSTANCE.submit(task);
    return task;
  }

  public @NonNull Future<Pipe> removeFirst() {
    FutureTask<Pipe> task = new FutureTask<>(new Callable<Pipe>() {
      @Override
      public Pipe call() throws Exception {
        pipeLock.writeLock().lock();
        final Pipe result = pipes.pollFirst().getValue();
        pipeLock.writeLock().unlock();
        return result;
      }
    });
    THREAD_POOL_INSTANCE.submit(task);
    return task;
  }

  public @NonNull Future<Pipe> removeLast() {
    FutureTask<Pipe> task = new FutureTask<>(new Callable<Pipe>() {
      @Override
      public Pipe call() throws Exception {
        pipeLock.writeLock().lock();
        final Pipe result = pipes.pollLast().getValue();
        pipeLock.writeLock().unlock();
        return result;
      }
    });
    THREAD_POOL_INSTANCE.submit(task);
    return task;
  }

  public @NonNull Future<Pipe> replace(@NonNull final String oldName, @NonNull final Pipe newPipe) {
    Validate.notBlank(oldName);
    Validate.notNull(newPipe);
    FutureTask<Pipe> task = new FutureTask<>(new Callable<Pipe>() {
      @Override
      public Pipe call() throws Exception {
        pipeLock.writeLock().lock();
        ListIterator<Map.Entry<String, Pipe>> iterator = getIteratorOf(oldName);
        if (iterator == null) {
          throw new NoSuchElementException(oldName);
        }
        Pipe oldPipe = iterator.next().getValue();
        iterator.set(new AbstractMap.SimpleImmutableEntry<String, Pipe>(
            oldName,
            newPipe
        ));
        return oldPipe;
      }
    });
    THREAD_POOL_INSTANCE.submit(task);
    return task;
  }

  public @NonNull Future<Pipe> replace(final @NonNull Pipe oldPipe,
                                       final @NonNull Pipe newPipe) {
    Validate.notNull(oldPipe);
    Validate.notNull(newPipe);
    FutureTask<Pipe> task = new FutureTask<>(new Callable<Pipe>() {
      @Override
      public Pipe call() throws Exception {
        pipeLock.writeLock().lock();
        ListIterator<Map.Entry<String, Pipe>> iterator = getIteratorOf(oldPipe);
        if (iterator == null) {
          throw new NoSuchElementException();
        }
        Map.Entry<String, Pipe> oldEntry = iterator.next();
        iterator.set(new AbstractMap.SimpleImmutableEntry<String, Pipe>(
            oldEntry.getKey(),
            newPipe
        ));
        return oldPipe;
      }
    });
    THREAD_POOL_INSTANCE.submit(task);
    return task;
  }

  public void read(@NonNull Object obj) {
    readQueue.add(obj);
  }

  public void write(@NonNull Object obj) {
    writeQueue.add(obj);
  }

  @Override
  public Iterator<Map.Entry<String, Pipe>> iterator() {
    return pipes.iterator();
  }
}