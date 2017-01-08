package chat.viska.xmpp;

import chat.viska.xmpp.stanzas.Stanza;
import java.util.Set;

/**
 * XMPP Extension.
 * @since 0.1
 */
public abstract class Extension implements SessionAware {

  private Session session;

  protected Extension(Session session) throws DuplicatedExtensionsException {
    if (session == null) {
      throw new NullPointerException(
          "A session must be provided when constructing an Extension!"
      );
    }
    if (session.hasExtension(this.getExtensionId())) {
      throw new DuplicatedExtensionsException(this.getExtensionId());
    }
    this.session = session;
  }

  public abstract Set<Class<? extends Extension>> getDependencies();

  public abstract boolean quickMatch(Stanza stanza);

  public Session getSession() {
    return session;
  }

  public abstract Set<String> getFeatures();

  public abstract String getExtensionId();
}