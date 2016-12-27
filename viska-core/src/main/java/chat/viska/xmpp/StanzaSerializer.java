package chat.viska.xmpp;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Kai-Chung Yan (殷啟聰)
 * @since 0.1
 */
public interface StanzaSerializer {

  <T> T read(Class<? extends T> type, InputStream input) throws Exception;

  void write(Object source, OutputStream output) throws Exception;
}