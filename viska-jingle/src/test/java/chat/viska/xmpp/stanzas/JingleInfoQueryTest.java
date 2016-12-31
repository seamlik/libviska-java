package chat.viska.xmpp.stanzas;

import chat.viska.xmpp.SimpleXmlSerializer;
import java.io.InputStreamReader;
import java.io.StringWriter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JingleInfoQueryTest {

  @Test
  public void parseRtpSessionInitiationStanza() throws Exception {
    SimpleXmlSerializer serializer = new SimpleXmlSerializer();
    JingleInfoQuery iq = serializer.read(
        JingleInfoQuery.class,
        new InputStreamReader(
          JingleInfoQuery.class.getResourceAsStream(
            "jingle-rtp-session-initiate.xml"
          )
        )
    );
    StringWriter outputWriter = new StringWriter();
    serializer.write(iq, outputWriter);
    String output = outputWriter.toString();
    Assertions.assertEquals(iq.getJingle().getContents().size(), 2);
  }

  @Test
  public void parseZrtpSessionInitiationStanza() throws Exception {
    SimpleXmlSerializer serializer = new SimpleXmlSerializer();
    JingleInfoQuery iq = serializer.read(
        JingleInfoQuery.class,
        new InputStreamReader(JingleInfoQuery.class.getResourceAsStream(
            "jingle-zrtp-session-initiation.xml"
        )
      )
    );
    StringWriter outputWriter = new StringWriter();
    serializer.write(iq, outputWriter);
    String output = outputWriter.toString();
    JingleInfoQuery.Jingle.Content.RtpDescription rtpDescription =
        (JingleInfoQuery.Jingle.Content.RtpDescription) iq.getJingle()
                                                          .getContents()
                                                          .get(0)
                                                          .getDescription();
    Assertions.assertEquals(
        rtpDescription.getEncryption().getZrtpHash().toString(),
        "fe30efd02423cb054e50efd0248742ac7a52c8f91bc2df881ae642c371ba46df"
    );
  }

  @Test
  public void parseAlternativeSession() throws Exception {
    SimpleXmlSerializer serializer = new SimpleXmlSerializer();
    JingleInfoQuery iq = serializer.read(
        JingleInfoQuery.class,
        new InputStreamReader(JingleInfoQuery.class.getResourceAsStream(
          "jingle-alternative-session.xml"
        )
      )
    );
    StringWriter outputWriter = new StringWriter();
    serializer.write(iq, outputWriter);
    String output = outputWriter.toString();
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
    SimpleXmlSerializer serializer = new SimpleXmlSerializer();
    JingleInfoQuery iq = serializer.read(
        JingleInfoQuery.class,
        new InputStreamReader(JingleInfoQuery.class.getResourceAsStream(
            "jingle-terminate-session.xml"
        )
      )
    );
    StringWriter outputWriter = new StringWriter();
    serializer.write(iq, outputWriter);
    String output = outputWriter.toString();
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
}