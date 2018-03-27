package chat.viska.xmpp;

import chat.viska.commons.DomUtils;
import chat.viska.commons.ExceptionCaughtEvent;
import io.reactivex.Flowable;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.xml.transform.TransformerException;

/**
 * Simulated {@link Session} for testing {@link Plugin}s.
 */
public class SimulatedSession extends Session {

  private final FlowableProcessor<XmlWrapperStanza> inboundStream = PublishProcessor
      .<XmlWrapperStanza>create()
      .toSerialized();
  private final FlowableProcessor<Stanza> outboundStream = PublishProcessor
      .<Stanza>create()
      .toSerialized();
  private final Set<StreamFeature> features = new CopyOnWriteArraySet<>();
  private Jid jid = Jid.EMPTY;

  @Override
  protected void onDisposing() {
    inboundStream.onComplete();
    outboundStream.onComplete();
  }

  @Override
  protected Flowable<XmlWrapperStanza> getInboundStanzaStream() {
    return inboundStream;
  }

  @Override
  protected void sendStanza(final Stanza stanza) {
    outboundStream.onNext(stanza);
  }

  @Override
  protected void sendError(final StreamErrorException error) {
    outboundStream.onNext(new XmlWrapperStanza(error.toXml()));
    changeStateToConnected();
  }

  public SimulatedSession() {
    outboundStream.subscribe(
        it -> System.out.println("[XML sent] " + DomUtils.writeString(it.toXml())),
        ex -> triggerEvent(new ExceptionCaughtEvent(this, ex))
    );
  }

  /**
   * Gets the outbound {@link Stanza} stream.
   */
  public Flowable<Stanza> getOutboundStream() {
    return outboundStream;
  }

  /**
   * Gets the inbound {@link Stanza} stream.
   */
  public Flowable<XmlWrapperStanza> getInboundStream() {
    return inboundStream;
  }

  /**
   * Reads a {@link Stanza} as if it is received from a server.
   */
  public void readStanza(final XmlWrapperStanza stanza) {
    try {
      System.out.println("[XML received] " + DomUtils.writeString(stanza.toXml()));
    } catch (TransformerException ex) {
      throw new RuntimeException(ex);
    }
    inboundStream.onNext(stanza);
  }

  /**
   * Sets the negotiated {@link Jid}.
   */
  public void setNegotiatedJid(final Jid jid) {
    this.jid = jid;
  }

  /**
   * Starts running this {@link Session}.
   */
  public void login() {
    changeStateToConnecting();
    changeStateToConnected();
    changeStateToHandshaking();
    changeStateToOnline();
  }

  @Override
  public Set<StreamFeature> getStreamFeatures() {
    return features;
  }

  @Override
  public Jid getNegotiatedJid() {
    return jid;
  }

  @Override
  public void disconnect() {
    changeStateToDisconnected();
  }
}