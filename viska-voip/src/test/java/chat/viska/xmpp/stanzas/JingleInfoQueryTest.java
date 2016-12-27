package chat.viska.xmpp.stanzas;

import org.simpleframework.xml.XmppStanzaSerializer;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JingleInfoQueryTest {

  @Test
  public void parseSessionInitiationStanza() throws Exception {
    XmppStanzaSerializer serializer = new XmppStanzaSerializer();
    JingleInfoQuery iq = serializer.read(
      JingleInfoQuery.class,
      JingleInfoQuery.class.getResource("jingle-rtp-initiate-session.xml").openStream()
    );
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    serializer.write(iq, outputStream);
    String output = outputStream.toString();
    Assertions.assertNotNull(iq);
    Assertions.assertTrue(!output.isEmpty());
  }

  @Test
  public void parseAlternativeSession() throws Exception {
    XmppStanzaSerializer serializer = new XmppStanzaSerializer();
    JingleInfoQuery iq = serializer.read(
      JingleInfoQuery.class,
      JingleInfoQuery.class.getResource("jingle-alternative-session.xml").openStream()
    );
    Assertions.assertEquals(
      iq.getJingle().getReason().getAlternativeSessionId(),
      "a73sjjvkla37jfea"
    );
    Assertions.assertEquals(
      iq.getJingle().getReason().getReasonType(),
      JingleInfoQuery.Jingle.Reason.ReasonType.ALTERNATIVE_SESSION
    );
  }
}