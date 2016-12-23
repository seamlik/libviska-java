package chat.viska.xmpp;

import java.util.Locale;
import java.util.Map;

/**
 * @author Kai-Chung Yan (殷啟聰)
 * @since 0.1
 */
public interface Message extends Stanza {

  class Thread {
    private String parent;
    private String id;

    public Thread(String id, String parent) {
      this.id = id;
      this.parent = parent;
    }

    public String getId() {
      return id;
    }

    public String getParent() {
      return parent;
    }
  }

  /**
   * Returns all {@code <body/>} elements.
   * @return should never {@code null}
   * @see <a href="https://tools.ietf.org/html/rfc6121#section-5.2.3">Body
   *      Element</a>
   */
  Map<Locale, String> getAllBodies();

  /**
   * Returns all {@code <subject/>} elements.
   * @return should never {@code null}
   * @see <a href="https://tools.ietf.org/html/rfc6121#section-5.2.4">Subject
   *      Element</a>
   */
  Map<Locale, String> getAllSubjects();

  /**
   * Returns the {@code <thread/>} element.
   * @return may be {@code null}
   * @see <a href="https://tools.ietf.org/html/rfc6121#section-5.2.5">Thread
   *      Element</a>
   */
  Thread getThread();
}