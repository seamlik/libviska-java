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

package chat.viska.commons;

import io.reactivex.Maybe;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import io.reactivex.functions.Predicate;
import io.reactivex.subjects.MaybeSubject;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Utility class that executes time-consuming tasks in parallel in order to
 * solve one single problem.
 *
 * <p>Given a problem that has multiple ways to solve and one would like to
 * speed up the overall solution by solving it in all these possible ways at the
 * same time instead of trying them one after another. This class is designed
 * for such scenario. It executes all submitted tasks in parallel and returns
 * the first result yielded by those tasks. One may also specify a <em>judge
 * function</em> so that the scheduler abandons the results that fail to meet
 * certain criteria and keeps waiting for a next result until all tasks are
 * finished.</p>
 *
 * @param <T> Solution type of the problem.
 */
public class ParallelSolversScheduler<T> {

  private final ExecutorService threadPool = Executors.newCachedThreadPool();
  private final Set<Callable<T>> tasks;
  private final Predicate<T> judge;
  private final Subject<T> resultStream;
  private final MaybeSubject<T> finalResult = MaybeSubject.create();
  private int finishedTasksCount = 0;

  /**
   * Default constructor.
   */
  public ParallelSolversScheduler(@NonNull final Collection<Callable<T>> tasks,
                                  @Nullable final Predicate<T> judge) {
    this.judge = judge;

    final Subject<T> unsafeSubject = PublishSubject.create();
    this.resultStream = unsafeSubject.toSerialized();
    resultStream.subscribe(result -> {
      ++this.finishedTasksCount;
      if (judge == null || judge.test(result)) {
        finalResult.onSuccess(result);
      }
      if (this.finishedTasksCount == getTasks().size()) {
        cancel();
      }
    });
    this.tasks = new HashSet<>(tasks);
  }

  /**
   * Gets all the submitted tasks.
   * @return Unmodifiable set.
   */
  @NonNull
  public Set<Callable<T>> getTasks() {
    return Collections.unmodifiableSet(tasks);
  }

  /**
   * Gets the judge function.
   */
  @Nullable
  public Predicate<T> getJudge() {
    return judge;
  }

  /**
   * Starts all submitted tasks.
   * @return Token to query the result in a non-blocking way.
   */
  @NonNull
  public Maybe<T> run() {
    for (Callable<T> task : this.tasks) {
      threadPool.submit(task);
    }
    return finalResult;
  }

  /**
   * Cancels all tasks and releases system resources.
   */
  public void cancel() {
    threadPool.shutdownNow();
    resultStream.onComplete();
    finalResult.onComplete();
  }
}