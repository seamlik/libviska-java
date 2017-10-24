package chat.viska.xmpp;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Simulated {@link Session} for testing {@link Plugin}s.
 */
public class SimulatedSession extends Session {

  private final FlowableProcessor<Stanza> inboundStream;
  private final FlowableProcessor<Stanza> outboundStream;
  private final Set<StreamFeature> features = new CopyOnWriteArraySet<>();
  private Jid jid = Jid.EMPTY;

  private void onDisposing() {
    inboundStream.onComplete();
    outboundStream.onComplete();
  }

  @Nonnull
  @Override
  protected Flowable<Stanza> getInboundStanzaStream() {
    return inboundStream;
  }

  @Override
  protected void sendStanza(@Nonnull final Stanza stanza) {
    outboundStream.onNext(stanza);
  }

  @Override
  protected void sendError(@Nonnull final StreamErrorException error) {
    outboundStream.onNext(new XmlWrapperStanza(error.toXml()));
    changeState(State.DISCONNECTED);
  }

  public SimulatedSession() {
    final FlowableProcessor<Stanza> unsafeInboundStream = PublishProcessor.create();
    this.inboundStream = unsafeInboundStream.toSerialized();
    final FlowableProcessor<Stanza> unsafeOutboundStream = PublishProcessor.create();
    this.outboundStream = unsafeOutboundStream.toSerialized();
  }

  @Nonnull
  public Flowable<Stanza> getOutboundStream() {
    return outboundStream;
  }

  @Nonnull
  public Flowable<Stanza> getInboundStream() {
    return inboundStream;
  }

  public void readStanza(@Nonnull final Stanza stanza) {
    inboundStream.onNext(stanza);
  }

  public void setNegotiatedJid(@Nullable final Jid jid) {
    this.jid = Jid.isEmpty(jid) ? Jid.EMPTY : jid;
  }


  @Override
  public void changeState(@Nonnull final State state) {
    super.changeState(state);
  }

  /**
   * Gets the {@link StreamFeature}s.
   * @return Modifiable thread-safe {@link Set}.
   */
  @Nonnull
  @Override
  public Set<StreamFeature> getStreamFeatures() {
    return features;
  }

  @Nonnull
  @Override
  @CheckReturnValue
  public Completable dispose() {
    return Completable.fromAction(() -> changeState(State.DISPOSED));
  }

  @Nonnull
  @Override
  public Jid getNegotiatedJid() {
    return jid;
  }
}