package chat.viska.xmpp;

import chat.viska.xmpp.stanzas.Stanza;
import java.io.Reader;
import java.io.Writer;

/**
 * Generic XMPP stanza serializer.
 *
 * <p>
 *   This interface defines methods for converting between XML data and a
 *   {@link chat.viska.xmpp.stanzas.Stanza}. It is implementation neutral so
 *   that the APIs of Viska stay stable if it switched from Simple XML to other
 *   XML frameworks.
 * </p>
 * @since 0.1
 */
public interface StanzaSerializer {

  <T extends Stanza> T read(Class<? extends T> type, Reader input) throws Exception;

  void write(Object source, Writer output) throws Exception;
}