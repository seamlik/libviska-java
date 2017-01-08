package chat.viska.xmpp.stanzas;

import chat.viska.xmpp.Jid;
import java.util.Map;

/**
 * @author Kai-Chung Yan (殷啟聰)
 * @since 0.1
 */
public abstract class Presence extends Stanza {
  public enum Show {
    AWAY,
    CHAT,
    DND,
    XA
  }

  protected Presence(String id, Type type, Jid sender, Jid recipient) {
    super(id, type, sender, recipient);
  }

  /**
   * Returns the value of the optional
   * @return {@code null} if the element does not exist
   * @see <a href="https://tools.ietf.org/html/rfc6121#section-4.7.2.1">Show
   *      Element</a>
   */
  public abstract Show getShow();

  /**
   * Returns the values of all {@code <status/>} elements.
   * @return An empty {@link Map} if there is no such elements, otherwise a
   *        {@link Map} of the status.
   * @see <a href="https://tools.ietf.org/html/rfc6121#section-4.7.2.2">Status
   *      Element</a>
   */
  public abstract Map<String, String> getAllStatus();

  /**
   * Returns the value of the optional element {@code <priority/>}.
   * @return {@code null} if the element does not exist
   * @see <a href="https://tools.ietf.org/html/rfc6121#section-4.7.2.3">Priority
   *      Element</a>
   */
  public abstract Byte getPriority();
}