package chat.viska.xmpp.stanzas;

import chat.viska.xmpp.Jid;
import chat.viska.xmpp.jingle.SdpBandwidthType;
import java.util.ArrayList;
import java.util.List;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementUnion;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.convert.Convert;
import org.simpleframework.xml.convert.JingleDescriptionConverter;
import org.simpleframework.xml.convert.JingleReasonTypeConverter;

/**
 * @author Kai-Chung Yan (殷啟聰)
 * @since 0.1
 */
@Root(name = "iq")
public class JingleInfoQuery extends BasicInfoQuery {

  @Namespace(reference = Jingle.XMLNS)
  public static final class Jingle {

    public enum Action {
      CONTENT_ACCEPT,
      CONTENT_ADD,
      CONTENT_MODIFY,
      CONTENT_REJECT,
      CONTENT_REMOVE,
      DESCRIPTION_INFO,
      SECURITY_INFO,
      SESSION_ACCEPT,
      SESSION_INFO,
      SESSION_INITIATE,
      SESSION_TERMINATE,
      TRANSPORT_ACCEPT,
      TRANSPORT_INFO,
      TRANSPORT_REJECT,
      TRANSPORT_REPLACE
    }

    public static class Content {

      public enum Creator {
        INITIATOR,
        RESPONDER
      }

      public enum Senders {
        BOTH,
        INITIATOR,
        NONE,
        RESPONDER
      }

      /**
       * A {@code <description/>} element in a Jingle {@code <iq/>} stanza.
       * <p>
       *   This interface does not have any members and is only for
       *   categorizing all classes representing a {@code <description/>}
       *   element.
       * </p>
       */
      public interface Description {}

      @Namespace(reference = RtpDescription.XMLNS)
      public static final class RtpDescription implements Description {

        public static class PayloadType {

          @Attribute(required = false)
          private Byte channels = 1;

          @Attribute(required = false)
          private Integer clockrate;

          @Attribute
          private byte id;

          @Attribute(required = false)
          private Integer maxptime;

          @Attribute(required = false)
          private String name;

          @Attribute(required = false)
          private Integer ptime;

          /**
           * Constructs a {@link PayloadType} with all fields specified.
           * @param channels The {@code channels} attribute, optional.
           * @param clockrate The {@code clockrate} attribute, optional.
           * @param id The {@code id} attribute, mandatory.
           * @param maxptime The {@code maxptime} attribute, optional.
           * @param name The {@code name} attribute, optional.
           * @param ptime The {@code ptime} attribute, optional.
           */
          public PayloadType(@Attribute(name = "channels", required = false)
                             Byte channels,
                             @Attribute(name = "clockrate", required = false)
                             Integer clockrate,
                             @Attribute(name = "id")
                             byte id,
                             @Attribute(name = "maxptime", required = false)
                             Integer maxptime,
                             @Attribute(name = "name", required = false)
                             String name,
                             @Attribute(name = "ptime", required = false)
                             Integer ptime) {
            this.channels = channels;
            this.clockrate = clockrate;
            this.id = id;
            this.maxptime = maxptime;
            this.name = name;
            this.ptime = ptime;
          }

          public Byte getChannels() {
            return channels;
          }

          public Integer getClockrate() {
            return clockrate;
          }

          public byte getId() {
            return id;
          }

          public Integer getMaxptime() {
            return maxptime;
          }

          public String getName() {
            return name;
          }

          public Integer getPtime() {
            return ptime;
          }
        }

        public static class Encryption {

        }

        public static final String XMLNS = "urn:xmpp:jingle:apps:rtp:1";

        @Attribute
        private String media;

        @Attribute(name = "type", required = false)
        @Path("bandwidth")
        private SdpBandwidthType bandwidthType;

        @Text(required = false)
        @Path("bandwidth")
        private Integer bandwidth;

        @ElementList(entry = "payload-type", inline = true)
        private List<PayloadType> payloadTypes;

        public RtpDescription(@Attribute(name = "media")
                              String media,
                              @Attribute(name = "type", required = false)
                              @Path("bandwidth")
                              SdpBandwidthType bandwidthType,
                              @Text(required = false)
                              @Path("bandwidth")
                              Integer bandwidth,
                              @ElementList(entry = "payload-type", inline = true)
                              Iterable<PayloadType> payloadTypes) {
          this.media = media;
          this.bandwidthType = bandwidthType;
          this.bandwidth = bandwidth;
          this.payloadTypes = new ArrayList<PayloadType>();
          for (PayloadType it : payloadTypes) {
            this.payloadTypes.add(it);
          }
        }
      }

      @Namespace(reference = FileTransferDescription.XMLNS)
      public static final class FileTransferDescription implements Description {

        public static final String XMLNS = "urn:xmpp:jingle:apps:file-transfer:4";
      }

      @Attribute
      private String creator;

      @Attribute
      private String name;

      @Attribute(required = false)
      private String disposition;

      @Attribute(required = false)
      private String senders;

      @ElementList(entry = "description", inline = true)
      @Convert(JingleDescriptionConverter.class)
      private List<?> descriptions;

      public Content(@Attribute(name = "creator")
                     String creator,
                     @Attribute(name = "name")
                     String name,
                     @Attribute(name = "disposition", required = false)
                     String disposition,
                     @Attribute(name = "senders", required = false)
                     String senders) {
        this.creator = creator;
        this.name = name;
        this.disposition = disposition;
        this.senders = senders;
      }

      public Creator getCreator() {
        return Enum.valueOf(Creator.class, creator.toUpperCase());
      }

      public String getName() {
        return name;
      }

      public String getDisposition() {
        return disposition;
      }

      public Senders getSenders() {
        return Enum.valueOf(Senders.class, senders.toUpperCase());
      }
    }

    public static final class Reason {

      public enum ReasonType {
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
        UNSUPPORTED_TRANSPORTS
      }

      @ElementUnion({
          @Element(name = "alternative-session", required = false),
          @Element(name = "busy", required = false),
          @Element(name = "cancel", required = false),
          @Element(name = "connectivity-error", required = false),
          @Element(name = "decline", required = false),
          @Element(name = "expired", required = false),
          @Element(name = "failed-application", required = false),
          @Element(name = "failed-transport", required = false),
          @Element(name = "general-error", required = false),
          @Element(name = "gone", required = false),
          @Element(name = "incompatible-parameters", required = false),
          @Element(name = "media-error", required = false),
          @Element(name = "security-error", required = false),
          @Element(name = "success", required = false),
          @Element(name = "timeout", required = false),
          @Element(name = "unsupported-applications", required = false),
          @Element(name = "unsupported-transports", required = false)
      })
      @Convert(JingleReasonTypeConverter.class)
      private ReasonType reasonType;

      @Text(required = false)
      @Path("alternative-session")
      private String sid;

      @Text(required = false)
      private String text;

      public String getText() {
        return text;
      }

      public String getAlternativeSessionId() {
        return sid;
      }

      public ReasonType getReasonType() {
        return reasonType;
      }
    }

    public static final String XMLNS = "urn:xmpp:jingle:1";

    @Attribute(required = false)
    private Jid initiator;

    @Attribute(required = false)
    private Jid responder;

    @Attribute
    private String sid;

    @Attribute
    private String action;

    @Element(required = false)
    private Content content;

    @Element(required = false)
    private Reason reason;

    public Jingle(@Attribute(name = "initiator", required = false)
                  Jid initiator,
                  @Attribute(name = "responder", required = false)
                  Jid responder,
                  @Attribute(name = "sid")
                  String sid,
                  @Attribute(name = "action")
                  String action,
                  @Element(name = "content", required = false)
                  Content content,
                  @Element(name = "reason", required = false)
                  Reason reason) {
      this.initiator = initiator;
      this.responder = responder;
      this.sid = sid;
      this.action = action;
      this.content = content;
      this.reason = reason;
    }

    public Action getAction() {
      return Enum.valueOf(Action.class, action.toUpperCase().replace('-', '_'));
    }

    public Jid getInitiator() {
      return initiator;
    }

    public Jid getResponder() {
      return responder;
    }

    public String getSid() {
      return sid;
    }

    public Content getContent() {
      return content;
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
    super(id, Enum.valueOf(Type.class, type), sender, recipient);
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

  /**
   * Returns the {@code <jingle/>} element.
   */
  public Jingle getJingle() {
    return jingle;
  }
}