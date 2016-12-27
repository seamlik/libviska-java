package chat.viska.xmpp.stanzas;

import java.util.Map;

/**
 * @author Kai-Chung Yan (殷啟聰)
 * @since 0.1
 */
public interface Presence extends Stanza {
  enum Show {
    AWAY,
    CHAT,
    DND,
    XA
  }

  /**
   * Returns the value of the optional
   * @return {@code null} if the element does not exist
   * @see <a href="https://tools.ietf.org/html/rfc6121#section-4.7.2.1">Show
   *      Element</a>
   */
  Show getShow();

  /**
   * Returns the values of all {@code <status/>} elements.
   * @return An empty {@link Map} if there is no such elements, otherwise a
   *        {@link Map} of the status.
   * @see <a href="https://tools.ietf.org/html/rfc6121#section-4.7.2.2">Status
   *      Element</a>
   */
  Map<String, String> getAllStatus();

  /**
   * Returns the value of the optional element {@code <priority/>}.
   * @return {@code null} if the element does not exist
   * @see <a href="https://tools.ietf.org/html/rfc6121#section-4.7.2.3">Priority
   *      Element</a>
   */
  Byte getPriority();
}