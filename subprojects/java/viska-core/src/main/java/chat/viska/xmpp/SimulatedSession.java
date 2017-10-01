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
    outboundStream.onNext(new Stanza(error.toXml()));
    setState(State.DISCONNECTED);
  }

  public SimulatedSession() {
    final FlowableProcessor<Stanza> unsafeInboundStream = PublishProcessor.create();
    this.inboundStream = unsafeInboundStream.toSerialized();
    final FlowableProcessor<Stanza> unsafeOutboundStream = PublishProcessor.create();
    this.outboundStream = unsafeOutboundStream.toSerialized();
  }

  public Flowable<Stanza> getOutboundStream() {
    return outboundStream;
  }

  public void readStanza(@Nonnull final Stanza stanza) {
    inboundStream.onNext(stanza);
  }

  public void setJid(@Nullable final Jid jid) {
    this.jid = Jid.isEmpty(jid) ? Jid.EMPTY : jid;
  }


  @Override
  public void setState(@Nonnull final State state) {
    super.setState(state);
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
    return Completable.fromAction(() -> setState(State.DISPOSED));
  }

  @Nonnull
  @Override
  public Jid getNegotiatedJid() {
    return jid;
  }
}