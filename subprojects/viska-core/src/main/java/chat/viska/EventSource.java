package chat.viska;

import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;

/**
 * @since 0.1
 */
public interface EventSource {

  @NonNull Observable<Event> getEventStream();
}