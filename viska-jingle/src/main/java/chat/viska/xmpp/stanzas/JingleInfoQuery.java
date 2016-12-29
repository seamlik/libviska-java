package chat.viska.xmpp.stanzas;

import chat.viska.xmpp.InvalidJidSyntaxException;
import chat.viska.xmpp.Jid;
import chat.viska.xmpp.jingle.SdpBandwidthType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.ice4j.ice.CandidateType;
import org.simpleframework.xml.*;
import org.simpleframework.xml.convert.Convert;
import org.simpleframework.xml.convert.XmppJingleDescriptionConverter;
import org.simpleframework.xml.convert.XmppJingleTransportConverter;

/**
 * {@code <iq/>} for a Jingle request.
 * @since 0.1
 */
@Root(name = "iq")
public class JingleInfoQuery extends BasicInfoQuery {

  /**
   * {@code <jingle/>} element.
   */
  @Namespace(reference = Jingle.XMLNS)
  public static final class Jingle {

    /**
     * Jingle action.
     *
     * @see <a href="https://xmpp.org/extensions/xep-0166.html#def-action">
     *      XEP-0166</a>
     */
    public enum Action {

      /**
       * Accepting a {@link Action#CONTENT_ADD}.
       */
      CONTENT_ACCEPT,

      /**
       * Adding a new {@link Content} to the current
       * {@link chat.viska.xmpp.jingle.Session}.
       */
      CONTENT_ADD,

      /**
       * Changing the direction of an existing {@link Content} through
       * modification of its {@code senders} property.
       */
      CONTENT_MODIFY,

      /**
       * Rejecting a {@link Action#CONTENT_ADD}.
       */
      CONTENT_REJECT,

      /**
       * Removing one or more {@link Content} from the current
       * {@link chat.viska.xmpp.jingle.Session}.
       */
      CONTENT_REMOVE,

      /**
       * Sending informational hints about parameters related to the
       * {@link Content.Description}, such as the suggested height and width of
       * a video display area or suggested configuration for an audio stream.
       */
      DESCRIPTION_INFO,

      /**
       * Sending information related to establishment or maintenance of security
       * preconditions.
       */
      SECURITY_INFO,

      /**
       * Definitively accepting a session negotiation.
       */
      SESSION_ACCEPT,

      /**
       * Sending session-level information, such as a session ping or (for
       * Jingle RTP sessions) a ringing message.
       */
      SESSION_INFO,

      /**
       * Requesting negotiation of a new {@link chat.viska.xmpp.jingle.Session}.
       */
      SESSION_INITIATE,

      /**
       * Ending the current {@link chat.viska.xmpp.jingle.Session}.
       */
      SESSION_TERMINATE,

      /**
       * Accepting a {@link Action#TRANSPORT_REPLACE}.
       */
      TRANSPORT_ACCEPT,

      /**
       * Exchanging transport candidates; it is mainly used in Jingle ICE-UDP
       * but might be used in other transport specifications.
       */
      TRANSPORT_INFO,

      /**
       * Rejecting a {@link Action#TRANSPORT_REPLACE}.
       */
      TRANSPORT_REJECT,

      /**
       * Redefining a transport method, typically for fallback to a different
       * method (e.g., changing from ICE-UDP to Raw UDP for a datagram
       * transport, or changing from
       * <a href="http://xmpp.org/extensions/xep-0065.html">SOCKS5 Bytestreams
       * (XEP-0065)</a> to <a href="http://xmpp.org/extensions/xep-0047.html">
       * In-Band Bytestreams (XEP-0047)</a> for a streaming transport).
       */
      TRANSPORT_REPLACE;

      /**
       * Parse the value of a stanza attribute as an instance of this enum.
       * @return {@code null} if the argument is {@code null}.
       */
      public static Action of(String name) {
        return Enum.valueOf(Action.class, name.toUpperCase().replace('-', '_'));
      }

      /**
       * Returns the name of this enum compatible with the XMPP standards
       * and suitable for being written into a stanza.
       */
      @Override
      public String toString() {
        return name().toLowerCase().replace('_', '-');
      }
    }

    /**
     * {@code <content/>} element.
     * <p>
     *   Represents a type of the data being transferred during a
     *   {@link chat.viska.xmpp.jingle.Session}, i.e. a data channel. One
     *   {@link chat.viska.xmpp.jingle.Session} may transfer multiple different
     *   {@link Content}, e.g. an audio channel and a video channel during a
     *   video chat session.
     * </p>
     */
    public static final class Content {

      /**
       * Indicates which party originally generated the {@link Content}.
       */
      public enum Creator {

        /**
         * Indicates the {@link chat.viska.xmpp.jingle.Session} initiator
         * generated the {@link Content}.
         */
        INITIATOR,

        /**
         * Indicates the {@link chat.viska.xmpp.jingle.Session} responder
         * generated the {@link Content}.
         */
        RESPONDER;

        /**
         * Parse the hash from stanza attribute as an instance of this enum.
         * @return {@code null} if the argument is {@code null}.
         */
        public static Creator of(String value) {
          return (value == null) ? null : Enum.valueOf(
            Creator.class, value.toUpperCase()
          );
        }

        /**
         * Returns the name of this enum compatible with the XMPP standards
         * and suitable for being written into a stanza.
         */
        @Override
        public String toString() {
          return this.name().toLowerCase();
        }
      }

      /**
       * Indicates which party will be generating content in a
       * {@link chat.viska.xmpp.jingle.Session}.
       */
      public enum Senders {

        /**
         * Both party will be generating content.
         */
        BOTH,

        /**
         * Only the initiator will be generating content.
         */
        INITIATOR,

        /**
         * No party will be generating content.
         */
        NONE,

        /**
         * Only the responder will be generating content.
         */
        RESPONDER;

        /**
         * Parse the hash from stanza attribute as an instance of this enum.
         * @return {@code null} if the argument is {@code null}.
         */
        public static Senders of(String value) {
          return (value == null) ? null : Enum.valueOf(
            Senders.class, value.toUpperCase()
          );
        }

        /**
         * Returns the name of this enum compatible with the XMPP standards
         * and suitable for being written into a stanza.
         */
        @Override
        public String toString() {
          return this.name().toLowerCase();
        }
      }

      /**
       * {@code <description/>} element.
       * <p>
       *   This class describes what a data channel represented by a
       *   {@link Content} is transferring.
       * </p>
       * <p>
       *   This interface does not have any members and is only for
       *   categorizing all classes representing a {@code <description/>}
       *   element.
       * </p>
       */
      public interface Description {}

      /**
       * {@code <description/>} for an RTP channel in a
       * {@link chat.viska.xmpp.jingle.Session}.
       * @see <a href="https://xmpp.org/extensions/xep-0167.html">Jingle RTP
       *      Session</a>
       */
      @Root(name = "description")
      @Namespace(reference = RtpDescription.XMLNS)
      public static final class RtpDescription implements Description {

        /**
         * {@code <payload-type/>} element.
         * <p>
         *   Represents an encoding of an RTP stream.
         * </p>
         */
        @Root(name = "payload-type")
        public static final class PayloadType {

          public static final class Parameter {

            @Attribute(required = false)
            private String name;

            @Attribute(required = false)
            private String value;

            /**
             * Exists only for Simple XML.
             */
            private Parameter() {}

            public Parameter(String name, String value) {
              if (name == null) {
                throw new NullPointerException("name");
              }
              if (value == null) {
                throw new NullPointerException("hash");
              }
              this.name = name;
              this.value = value;
            }

            public String getName() {
              return name;
            }

            public String getValue() {
              return value;
            }
          }

          @Attribute(required = false)
          private Short id;

          @Attribute(required = false)
          private String name;

          @Attribute(required = false)
          private Byte channels = 1;

          @Attribute(required = false)
          private Long clockrate;

          @Attribute(required = false)
          private Long maxptime;

          @Attribute(required = false)
          private Long ptime;

          @ElementList(entry = "parameter", inline = true, required = false)
          List<? extends Parameter> parameters;

          /**
           * Exists only for Simple XML.
           */
          private PayloadType() {}

          /**
           * Constructs a {@link PayloadType} with all fields specified.
           * @param id See {@link PayloadType#getId()}. This argument is
           *           mandatory.
           * @param channels See {@link PayloadType#getNumberOfChannels()}
           * @param clockrate See {@link PayloadType#getClockrate()}.
           * @param maxptime See {@link PayloadType#getMaxPacketTime()}.
           * @param name See {@link PayloadType#getName()}.
           * @param ptime See {@link PayloadType#getPacketTime()}.
           * @param parameters See {@link PayloadType#getParameters()} )}
           */
          public PayloadType(short id,
                             String name,
                             Byte channels,
                             Long clockrate,
                             Long maxptime,
                             Long ptime,
                             Iterable<? extends Parameter> parameters) {
            this.channels = channels;
            this.clockrate = clockrate;
            this.id = id;
            this.maxptime = maxptime;
            this.name = name;
            this.ptime = ptime;
            List<Parameter> parametersList = new ArrayList<>();
            for (Parameter it : this.parameters) {
              parametersList.add(it);
            }
            this.parameters = parametersList;
          }

          /**
           * Returns the number of channels. If it returns {@code null}, it must
           * be assumed to contain 1 channel.
           */
          public Byte getNumberOfChannels() {
            return channels;
          }

          /**
           * Returns the sampling frequency in Hertz.
           */
          public Long getClockrate() {
            return clockrate;
          }

          /**
           * Returns the payload identifier.
           */
          public Short getId() {
            return id;
          }

          /**
           * Returns the maximum amount of media that can be encapsulated in
           * in each packet, expressed as time in milliseconds.
           * @see <a href="https://tools.ietf.org/html/rfc4566#section-6">SDP
           *      Attributes</a>
           */
          public Long getMaxPacketTime() {
            return maxptime;
          }

          /**
           * Returns the appropriate subtype of the MIME type.
           * @see <a href="https://www.iana.org/assignments/media-types/media-types.xhtml">
           *      IANA MIME Media Types</a>
           */
          public String getName() {
            return name;
          }

          /**
           * Returns the length of time in milliseconds represented by the media
           * in a packet.
           * @see <a href="https://tools.ietf.org/html/rfc4566#section-6">SDP
           *      Attributes</a>
           */
          public Long getPacketTime() {
            return ptime;
          }

          public List<? extends Parameter> getParameters() {
            List<Parameter> parameters = new ArrayList<>();
            for (Parameter it : this.parameters) {
              parameters.add(it);
            }
            return parameters;
          }
        }

        public static class Encryption {

          @Root(name = "crypto")
          public static final class SrtpCrypto {

            @Attribute(name = "crypto-suite", required = false)
            private String cryptoSuite;

            @Attribute(name = "key-params", required = false)
            private String keyParams;

            @Attribute(required = false)
            private Integer tag;

            @Attribute(name = "session-params", required = false)
            private String sessionParams;

            /**
             * Exists only for Simple XML.
             */
            private SrtpCrypto() {}

            public SrtpCrypto(String cryptoSuite, String keyParams, String sessionParams, Integer tag) {
              this.cryptoSuite = cryptoSuite;
              this.keyParams = keyParams;
              this.sessionParams = sessionParams;
              this.tag = tag;
            }

            public String getCryptoSuite() {
              return cryptoSuite;
            }

            public String getKeyParams() {
              return keyParams;
            }

            public String getSessionParams() {
              return sessionParams;
            }

            public Integer getTag() {
              return tag;
            }
          }

          @Root(name = "zrtp-hash")
          @Namespace(reference = ZrtpHash.XMLNS)
          public static final class ZrtpHash {

            public static final String XMLNS = "urn:xmpp:jingle:apps:rtp:zrtp:1";

            public static final String SUPPORTED_VERSION = "1.10";

            @Attribute(required = false)
            private String version;

            @Text(required = false)
            private String hash;

            private ZrtpHash() {}

            public ZrtpHash(String hash, String version) {
              if (hash == null) {
                throw new NullPointerException("hash");
              }
              if (hash.isEmpty()) {
                throw new IllegalArgumentException("Empty hash.");
              }
              if (version == null) {
                version = SUPPORTED_VERSION;
              }
              this.version = version;
            }

            public ZrtpHash(String hash) {
              this(hash, SUPPORTED_VERSION);
            }

            @Override
            public String toString() {
              return hash.trim();
            }

            public String getHash() {
              return (hash == null) ? null : hash.trim();
            }
          }

          @Attribute(required = false)
          private String required;

          @ElementList(entry = "crypto", inline = true, required = false)
          List<? extends SrtpCrypto> cryptos;

          @Element(name = "zrtp-hash", required = false)
          private ZrtpHash zrtpHash;

          private Encryption() {}

          public Encryption(boolean required,
                            List<? extends SrtpCrypto> cryptos) {
            this.required = Boolean.toString(required);
            this.cryptos = new ArrayList<>(cryptos);
          }

          public Encryption(boolean required, ZrtpHash zrtpHash) {
            if (zrtpHash == null) {
              throw new NullPointerException("zrtpHash");
            }
            this.required = (required) ? "true" : "false";
            this.zrtpHash = zrtpHash;
          }

          public Encryption(boolean required, String zrtpHash) {
            if (zrtpHash == null) {
              throw new NullPointerException("zrtpHash");
            }
            this.required = (required) ? "true" : "false";
            this.zrtpHash = new ZrtpHash(zrtpHash);

          }

          public Boolean required() {
            if (required == null) {
              return null;
            }
            if (required.equals("true") || required.equals("1")) {
              return true;
            }
            if (required.equals("false") || required.equals("0")) {
              return false;
            }
            throw new IllegalArgumentException();
          }
        }

        /**
         * The XML namespace of this element.
         */
        public static final String XMLNS = "urn:xmpp:jingle:apps:rtp:1";

        @Attribute(required = false)
        private String media;

        @Attribute(name = "type", required = false)
        @Path("bandwidth")
        private SdpBandwidthType bandwidthType;

        @Text(required = false)
        @Path("bandwidth")
        private String bandwidth;

        @Element(required = false)
        private Encryption encryption;

        @ElementList(entry = "payload-type", inline = true, required = false)
        private List<? extends PayloadType> payloadTypes;

        /**
         * Exists only for Simple XML.
         */
        private RtpDescription() {}

        public RtpDescription(String media,
                              SdpBandwidthType bandwidthType,
                              Integer bandwidth,
                              PayloadType[] payloadTypes) {
          if (payloadTypes == null) {
            throw new NullPointerException();
          }
          this.media = media;
          this.bandwidthType = bandwidthType;
          this.bandwidth = bandwidth.toString();
          this.payloadTypes = Arrays.asList(payloadTypes);
        }

        public String getMedia() {
          return media;
        }

        public SdpBandwidthType getBandwidthType() {
          return bandwidthType;
        }

        public Integer getBandwidth() {
          return new Integer(bandwidth);
        }

        /**
         * Returns the encodings available for the RTP stream. The list of
         * {@code <payload-types/>} is provided in order of preference by
         * placing the most preferred payload type at the first position and the
         * least preferred at the last.
         */
        public PayloadType[] getPayloadTypes() {
          return payloadTypes.toArray(new PayloadType[payloadTypes.size()]);
        }
      }

      /**
       * {@code <description/>} for a file transferring channel in a
       * {@link chat.viska.xmpp.jingle.Session}.
       * @see <a href="https://xmpp.org/extensions/xep-0234.html">Jingle File
       *      Transfer</a>
       */
      @Root(name = "description")
      @Namespace(reference = FileTransferDescription.XMLNS)
      public static final class FileTransferDescription implements Description {

        public static final String XMLNS = "urn:xmpp:jingle:apps:file-transfer:4";
      }

      /**
       * {@code <transport/>} element.
       * <p>
       *   This class describes the transport method a data channel represented
       *   by a {@link Content}.
       * </p>
       * <p>
       *   This interface does not have any members and is only for
       *   categorizing all classes representing a {@code <description/>}
       *   element.
       * </p>
       */
      public interface Transport {}

      @Root(name = "transport")
      @Namespace(reference = IceUdpTransport.XMLNS)
      public static final class IceUdpTransport implements Transport {

        public static final class Candidate {

          @Attribute(required = false)
          private Short component;

          @Attribute(required = false)
          private Short foundation;

          @Attribute(required = false)
          private Short generation;

          @Attribute(required = false)
          private String id;

          @Attribute(required = false)
          private String ip;

          @Attribute(required = false)
          private Short network;

          @Attribute(required = false)
          private Integer port;

          @Attribute(required = false)
          private Long priority;

          @Attribute(required = false)
          private String protocol;

          @Attribute(name = "rel-addr", required = false)
          private String relAddr;

          @Attribute(name = "rel-port", required = false)
          private Integer relPort;

          @Attribute(required = false)
          private String type;

          /**
           * Exists only for Simple XML.
           */
          private Candidate() {}

          /**
           * Desfault constructor.
           * @param component See {@link Candidate#getComponent()}.
           * @param foundation See {@link Candidate#getFoundation()}.
           * @param generation See {@link Candidate#getGeneration()}.
           * @param id See {@link Candidate#getId()}.
           * @param ip See {@link Candidate#getIp()}.
           * @param network See {@link Candidate#getNetwork()}.
           * @param port See {@link Candidate#getPort()}.
           * @param priority See {@link Candidate#getPriority()}.
           * @param protocol See {@link Candidate#getProtocol()}.
           * @param relAddr See {@link Candidate#getRelAddr()}.
           * @param relPort See {@link Candidate#getRelPort()}.
           * @param type See {@link Candidate#getType()}.
           */
          public Candidate(Short component,
                           Short foundation,
                           Short generation,
                           String id,
                           String ip,
                           Short network,
                           Integer port,
                           Long priority,
                           String protocol,
                           String relAddr,
                           Integer relPort,
                           CandidateType type) {
            this.component = component;
            this.foundation = foundation;
            this.generation = generation;
            this.id = id;
            this.ip = ip;
            this.network = network;
            this.port = port;
            this.priority = priority;
            this.protocol = protocol;
            this.relAddr = relAddr;
            this.relPort = relPort;
            this.type = type.toString();
          }

          public Short getComponent() {
            return component;
          }

          public Short getFoundation() {
            return foundation;
          }

          public Short getGeneration() {
            return generation;
          }

          public String getId() {
            return id;
          }

          public String getIp() {
            return ip;
          }

          public Short getNetwork() {
            return network;
          }

          public Integer getPort() {
            return port;
          }

          public Long getPriority() {
            return priority;
          }

          public String getProtocol() {
            return protocol;
          }

          public String getRelAddr() {
            return relAddr;
          }

          public Integer getRelPort() {
            return relPort;
          }

          public CandidateType getType() {
            return CandidateType.parse(type);
          }
        }

        public static final class RemoteCandidate {

          @Attribute(required = false)
          private Short component;

          @Attribute(required = false)
          private String ip;

          @Attribute(required = false)
          private Integer port;

          /**
           * Exists only for Simple XML.
           */
          private RemoteCandidate() {}

          public RemoteCandidate(Short component, String ip, Integer port) {
            this.component = component;
            this.ip = ip;
            this.port = port;
          }

          public Short getComponent() {
            return component;
          }

          public String getIp() {
            return ip;
          }

          public Integer getPort() {
            return port;
          }
        }

        public static final String XMLNS = "urn:xmpp:jingle:transports:ice-udp:1";

        @Attribute(required = false)
        private String pwd;

        @Attribute(required = false)
        private String ufrag;

        @ElementList(entry = "candidate", inline = true, required = false)
        private List<Candidate> candidates;

        @Element(name = "remote-candidate",required = false)
        private RemoteCandidate remoteCandidate;

        /**
         * Exists only for Simple XML.
         */
        private IceUdpTransport() {}

        public IceUdpTransport(List<Candidate> candidates,
                               RemoteCandidate remoteCandidate,
                               String pwd,
                               String ufrag) {
          this.pwd = pwd;
          this.ufrag = ufrag;
          this.remoteCandidate = remoteCandidate;
          this.candidates = new ArrayList<>(candidates);
        }

        public List<Candidate> getCandidates() {
          return new ArrayList<>(candidates);
        }

        public RemoteCandidate getRemoteCandidate() {
          return remoteCandidate;
        }
      }

      @Root(name = "transport")
      @Namespace(reference = RawUdpTransport.XMLNS)
      public static final class RawUdpTransport implements Transport {

        public static final String XMLNS = "urn:xmpp:jingle:transports:raw-udp:1";
      }

      @Attribute(required = false)
      private String creator;

      @Attribute(required = false)
      private String name;

      @Attribute(required = false)
      private String disposition;

      @Attribute(required = false)
      private String senders;

      @Element(required = false)
      @Convert(XmppJingleDescriptionConverter.class)
      private Description description;

      @Element(required = false)
      @Convert(XmppJingleTransportConverter.class)
      private Transport transport;

      /**
       * Exists only for Simple XML
       */
      private Content() {}

      public Content(Creator creator,
                     String name,
                     String disposition,
                     Senders senders,
                     Description description,
                     Transport transport) {
        this.creator = creator.toString();
        this.name = name;
        this.disposition = disposition;
        this.senders = senders.toString();
        this.description = description;
        this.transport = transport;
      }

      /**
       * See {@link Creator}.
       * @throws IllegalArgumentException If its hash in the original stanza is
       *                                  invalid.
       */
      public Creator getCreator() {
        return Creator.of(creator);
      }

      public String getName() {
        return name;
      }

      public String getDisposition() {
        return disposition;
      }

      public Senders getSenders() {
        return Senders.of(senders);
      }
    }

    /**
     * {@code <reason/>} element.
     * <p>
     *   Represents a reason behind a Jingle request.
     * </p>
     */
    public static final class Reason {

      /**
       * Actual reason of a {@code <reason/>}.
       */
      public enum ReasonType {

        /**
         * Indicates that the party prefers to use an existing session with the
         * peer rather than initiate a new session. The ID of the preferred
         * session can be retrieved by {@link Reason#getAlternativeSessionId()}.
         */
        ALTERNATIVE_SESSION,
        BUSY,
        CANCEL,
        CONNECTIVITY_ERROR,
        DECLINE,
        EXPIRED,
        FAILED_APPLICATION,
        FAILED_TRANSPORT,
        GENERAL_ERROR,
        GONE,
        INCOMPATIBLE_PARAMETERS,
        MEDIA_ERROR,
        SECURITY_ERROR,
        SUCCESS,
        TIMEOUT,
        UNSUPPORTED_APPLICATIONS,
        UNSUPPORTED_TRANSPORTS;

        /**
         * Parse the hash from stanza attribute as an instance of this enum.
         * @return {@code null} if the argument is {@code null}.
         */
        public static ReasonType of(String value) {
          return (value == null) ? null : Enum.valueOf(
            ReasonType.class, value.toUpperCase().replace('-', '_')
          );
        }

        /**
         * Returns the name of this enum compatible with the XMPP standards
         * and suitable for being written into a stanza.
         */
        @Override
        public String toString() {
          return this.name().toLowerCase().replace('_', '-');
        }
      }

      private static final class AlternateSession {

        @Element(required = false)
        public String sid;
      }

      @Root(name = "busy")
      private static final class Busy {}

      @Root(name = "cancel")
      private static final class Cancel {}

      @Root(name = "connectivity-error")
      private static final class ConnectivityError {}

      @Root(name = "decline")
      private static final class Decline {}

      @Root(name = "expired")
      private static final class Expired {}

      @Root(name = "failed-application")
      private static final class FailedApplication {}

      @Root(name = "failed-transport")
      private static final class FailedTransport {}

      @Root(name = "general-error")
      private static final class GeneralError {}

      @Root(name = "gone")
      private static final class Gone {}

      @Root(name = "incompatible-parameters")
      private static final class IncompatibleParameters {}

      @Root(name = "media-error")
      private static final class MediaError {}

      @Root(name = "security-error")
      private static final class SecurityError {}

      @Root(name = "success")
      private static final class Success {}

      @Root(name = "timeout")
      private static final class Timeout {}

      @Root(name = "unsupported-applications")
      private static final class UnsupportedApplications {}

      @Root(name = "unsupported-transports")
      private static final class UnsupportedTransports {}

      @Element(name = "alternative-session", required = false)
      private AlternateSession alternateSession;

      @Element(required = false)
      private Busy busy;

      @Element(required = false)
      private Cancel cancel;

      @Element(name = "connectivity-error", required = false)
      private ConnectivityError connectivityError;

      @Element(required = false)
      private Decline decline;

      @Element(required = false)
      private Expired expired;

      @Element(name = "failed-application", required = false)
      private FailedApplication failedApplication;

      @Element(name = "failed-transport", required = false)
      private FailedTransport failedTransport;

      @Element(name = "general-error", required = false)
      private GeneralError generalError;

      @Element(required = false)
      private Gone gone;

      @Element(name = "incompatible-parameters", required = false)
      private IncompatibleParameters incompatibleParameters;

      @Element(name = "media-error", required = false)
      private MediaError mediaError;

      @Element(name = "security-error", required = false)
      private SecurityError securityError;

      @Element(required = false)
      private Success success;

      @Element(required = false)
      private Timeout timeout;

      @Element(name = "unsupported-applications", required = false)
      private UnsupportedApplications unsupportedApplications;

      @Element(name = "unsupported-transports", required = false)
      private UnsupportedTransports unsupportedTransports;

      @Element(required = false)
      private String text;

      private ReasonType reason;

      /**
       * Exists only for Simple XML.
       */
      private Reason() {}

      /**
       * Default constructor.
       * @param reason See {@link Reason#getReason()}.
       * @param alternativeSid reason See {@link Reason#getAlternativeSessionId()}.
       * @param text reason See {@link Reason#getText()}.
       */
      public Reason(ReasonType reason, String alternativeSid, String text) {
        if (reason != ReasonType.ALTERNATIVE_SESSION && alternativeSid != null) {
          throw new IllegalArgumentException();
        }
        this.text = text;
        this.reason = reason;
        if (reason == ReasonType.ALTERNATIVE_SESSION) {
          alternateSession = new AlternateSession();
          alternateSession.sid = alternativeSid;
        }
      }

      /**
       * Returns the additional reason provided by the {@link Stanza} sender.
       */
      public String getText() {
        return text;
      }

      /**
       * Returns the alternative session ID if the given reason is
       * {@link ReasonType#ALTERNATIVE_SESSION}.
       * @return {@code null} if the given reason is not
       *         {@link ReasonType#ALTERNATIVE_SESSION} or the altrenative
       *         session ID is not provided.
       */
      public String getAlternativeSessionId() {
        return alternateSession.sid;
      }

      /**
       * Returns the actual reason of this element.
       */
      public ReasonType getReason() {
        if (reason != null) {
          return reason;
        }
        if (alternateSession != null) {
          reason = ReasonType.ALTERNATIVE_SESSION;
        } else if (busy != null) {
          reason = ReasonType.BUSY;
        } else if (cancel != null) {
          reason = ReasonType.CANCEL;
        } else if (connectivityError != null) {
          reason = ReasonType.CONNECTIVITY_ERROR;
        } else if (decline != null) {
          reason = ReasonType.DECLINE;
        } else if (expired != null) {
          reason = ReasonType.EXPIRED;
        } else if (failedApplication != null) {
          reason = ReasonType.FAILED_APPLICATION;
        } else if (failedTransport != null) {
          reason = ReasonType.FAILED_TRANSPORT;
        } else if (generalError != null) {
          reason = ReasonType.GENERAL_ERROR;
        } else if (gone != null) {
          reason = ReasonType.GONE;
        } else if (incompatibleParameters != null) {
          reason = ReasonType.INCOMPATIBLE_PARAMETERS;
        } else if (mediaError != null) {
          reason = ReasonType.MEDIA_ERROR;
        } else if (securityError != null) {
          reason = ReasonType.SECURITY_ERROR;
        } else if (success != null) {
          reason = ReasonType.SUCCESS;
        } else if (timeout != null) {
          reason = ReasonType.TIMEOUT;
        } else if (unsupportedApplications != null) {
          reason = ReasonType.UNSUPPORTED_APPLICATIONS;
        } else if (unsupportedTransports != null) {
          reason = ReasonType.UNSUPPORTED_TRANSPORTS;
        }
        return reason;
      }
    }

    public static final String XMLNS = "urn:xmpp:jingle:1";

    @Attribute(required = false)
    private String initiator;

    @Attribute(required = false)
    private String responder;

    @Attribute
    private String sid;

    @Attribute
    private String action;

    @ElementList(entry = "content", inline = true, required = false)
    private List<Content> contents;

    @Element(required = false)
    private Reason reason;

    /**
     * Exists only for Simple XML.
     */
    private Jingle() {}

    /**
     * Construct a {@link Jingle} with every field specified.
     * @param sid See {@link Jingle#getSessionId()}, mandatory.
     * @param action See {@link Jingle#getAction()}, mandatory.
     * @param initiator See {@link Jingle#getInitiator()}.
     * @param responder See {@link Jingle#getResponder()}.
     * @param contents See {@link Jingle#getContent()}.
     * @param reason See {@link Jingle#getReason()}.
     * @throws NullPointerException If the mandatory arguments are {@code null}.
     */
    public Jingle(String sid,
                  Action action,
                  Jid initiator,
                  Jid responder,
                  Iterable<? extends Content> contents,
                  Reason reason) {
      if (sid == null) {
        throw new NullPointerException("sid");
      }
      this.initiator = initiator.toString();
      this.responder = responder.toString();
      this.sid = sid;
      this.action = action.toString();
      this.reason = reason;
      List<Content> contentCollection = new ArrayList<>();
      for (Content it : contents) {
        contentCollection.add(it);
      }
      this.contents = contentCollection;
    }

    /**
     * Returns the action of this Jingle request.
     * <p>
     *   This property represents the {@code action} attribute.
     * </p>
     * @return {@code null} if the attribute does not exist.
     */
    public Action getAction() {
      return (action == null) ? null : Action.of(action);
    }

    /**
     * Returns the entity that has initiated the
     * {@link chat.viska.xmpp.jingle.Session}.
     * @return {@code null} if the attribute is not present.
     * @throws InvalidJidSyntaxException If the value of this attribute is not a
     *         valid {@link Jid}.
     */
    public Jid getInitiator() {
      return new Jid(Jid.parseJidParts(initiator));
    }

    /**
     * Returns the entity that has replied to the
     * {@link chat.viska.xmpp.jingle.Session} initiation.
     * @return {@code null} if the attribute is not present.
     * @throws InvalidJidSyntaxException If the value of this attribute is not a
     *         valid {@link Jid}.
     */
    public Jid getResponder() {
      return new Jid(Jid.parseJidParts(responder));
    }

    /**
     * Returns the session identifier generated by the initiator.
     */
    public String getSessionId() {
      return sid;
    }


    public List<Content> getContent() {
      List<Content> contents = new ArrayList<>();
      for (Content it : this.contents) {
        contents.add(it);
      }
      return contents;
    }

    public Reason getReason() {
      return reason;
    }
  }

  @Element
  private Jingle jingle;

  private JingleInfoQuery(@Attribute(name = "id") String id,
                          @Attribute(name = "type") String type,
                          @Attribute(name = "from") Jid sender,
                          @Attribute(name = "to") Jid recipient,
                          @Element(name = "jingle") Jingle jingle) {
    super(id, Enum.valueOf(Type.class, type.toUpperCase()), sender, recipient);
    this.jingle = jingle;
  }

  public JingleInfoQuery(String id,
                         Type type,
                         Jid sender,
                         Jid recipient,
                         Jingle jingle) {
    super(id, Enum.valueOf(Type.class, type.name().toLowerCase()), sender, recipient);
    this.jingle = jingle;
  }

  @Override
  public boolean needsAcknowledgement() {
    return true;
  }

  /**
   * Returns the {@code <jingle/>} element.
   */
  public Jingle getJingle() {
    return jingle;
  }
}