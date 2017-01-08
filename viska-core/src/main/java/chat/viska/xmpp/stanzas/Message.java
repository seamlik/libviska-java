package chat.viska.xmpp.stanzas;

import chat.viska.xmpp.Jid;
import java.util.Locale;
import java.util.Map;

/**
 * {@code <message/>} {@link Stanza}.
 * @since 0.1
 */
public abstract class Message extends Stanza {

  /**
   * {@code <thread/>} element.
   */
  public final class Thread {
    private String parent;
    private String id;

    /**
     * Default constructor.
     * @param id {@code id} attribute.
     * @param parent {@code parent} attribute.
     */
    public Thread(String id, String parent) {
      this.id = id;
      this.parent = parent;
    }

    /**
     * Returns the ID of this {@link Thread}.
     * <p>
     *   This property represents the content of this element.
     * </p>
     */
    public String getId() {
      return id;
    }

    /**
     * Returns the ID of the parent {@link Thread} of this {@link Thread}.
     * <p>
     *   This property represents the {@code parent} attribute.
     * </p>
     */
    public String getParent() {
      return parent;
    }
  }

  protected Message(String id, Type type, Jid sender, Jid recipient) {
    super(id, type, sender, recipient);
  }

  /**
   * Returns all {@code <body/>} elements.
   * @return never {@code null}
   * @see <a href="https://tools.ietf.org/html/rfc6121#section-5.2.3">Body
   *      Element</a>
   */
  public abstract Map<String, String> getAllBodies();

  /**
   * Returns all {@code <subject/>} elements.
   * @return never {@code null}
   * @see <a href="https://tools.ietf.org/html/rfc6121#section-5.2.4">Subject
   *      Element</a>
   */
  public abstract Map<String, String> getAllSubjects();

  /**
   * Returns the {@code <thread/>} element.
   * @return may be {@code null}
   * @see <a href="https://tools.ietf.org/html/rfc6121#section-5.2.5">Thread
   *      Element</a>
   */
  public abstract Thread getThread();
}