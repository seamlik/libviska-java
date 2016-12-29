package chat.viska.xmpp.stanzas;

import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.simpleframework.xml.XmppStanzaSerializer;

public class JingleInfoQueryTest {

  @Test
  public void parseRtpSessionInitiationStanza() throws Exception {
    XmppStanzaSerializer serializer = new XmppStanzaSerializer();
    JingleInfoQuery iq = serializer.read(
        JingleInfoQuery.class,
        JingleInfoQuery.class
                       .getResource("jingle-rtp-session-initiate.xml")
                       .openStream()
    );
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    serializer.write(iq, outputStream);
    String output = outputStream.toString();
    Assertions.assertNotNull(iq);
    Assertions.assertTrue(!output.isEmpty());
  }

  @Test
  public void parseRtpContentAcceptStanza() throws Exception {
    XmppStanzaSerializer serializer = new XmppStanzaSerializer();
    JingleInfoQuery iq = serializer.read(
      JingleInfoQuery.class,
      JingleInfoQuery.class
        .getResource("jingle-rtp-content-accept.xml")
        .openStream()
    );
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    serializer.write(iq, outputStream);
    String output = outputStream.toString();
    Assertions.assertNotNull(iq);
    Assertions.assertTrue(!output.isEmpty());
  }

  @Test
  public void parseZrtpSessionInitiationStanza() throws Exception {
    XmppStanzaSerializer serializer = new XmppStanzaSerializer();
    JingleInfoQuery iq = serializer.read(
      JingleInfoQuery.class,
      JingleInfoQuery.class
        .getResource("jingle-zrtp-session-initiation.xml")
        .openStream()
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
        JingleInfoQuery.class
                       .getResource("jingle-alternative-session.xml")
                       .openStream()
    );
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    serializer.write(iq, outputStream);
    String output = outputStream.toString();
    Assertions.assertEquals(
        iq.getJingle().getReason().getAlternativeSessionId(),
        "b84tkkwlmb48kgfb"
    );
    Assertions.assertEquals(
        iq.getJingle().getReason().getReason(),
        JingleInfoQuery.Jingle.Reason.ReasonType.ALTERNATIVE_SESSION
    );
    Assertions.assertTrue(!output.isEmpty());
  }

  @Test
  public void parseTerminateSession() throws Exception {
    XmppStanzaSerializer serializer = new XmppStanzaSerializer();
    JingleInfoQuery iq = serializer.read(
      JingleInfoQuery.class,
      JingleInfoQuery.class
        .getResource("jingle-terminate-session.xml")
        .openStream()
    );
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    serializer.write(iq, outputStream);
    String output = outputStream.toString();
    Assertions.assertEquals(
      iq.getJingle().getReason().getText(),
      "Sorry, gotta go!"
    );
    Assertions.assertEquals(
      iq.getJingle().getReason().getReason(),
      JingleInfoQuery.Jingle.Reason.ReasonType.SUCCESS
    );
    Assertions.assertTrue(!output.isEmpty());
  }

  public static void test(String text) {
    text = "shit!";
  }
}