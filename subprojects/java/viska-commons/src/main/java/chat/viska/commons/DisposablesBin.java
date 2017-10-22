package chat.viska.commons;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Container for storing {@link Disposable}s.
 */
@ThreadSafe
public class DisposablesBin {

  @GuardedBy("itself")
  private final Set<Disposable> bin = new LinkedHashSet<>();

  /**
   * Removes all disposed {@link Disposable}s from this class. It normally does not need to be
   * invoked manually.
   */
  public void clean() {
    synchronized (this.bin) {
      this.bin.removeAll(
          Observable.fromIterable(this.bin).filter(Disposable::isDisposed).toList().blockingGet()
      );
    }
  }

  /**
   * Adds a {@link Disposable} to this class.
   */
  public void add(final Disposable disposable) {
    synchronized (this.bin) {
      this.bin.add(disposable);
    }
    Completable.fromAction(this::clean).subscribeOn(Schedulers.io()).subscribe();
  }

  /**
   * Disposes all {@link Disposable}s and removes them.
   */
  public void clear() {
    synchronized (this.bin) {
      for (Disposable it : this.bin) {
        it.dispose();
      }
      this.bin.clear();
    }
  }
}