package chat.viska.xmpp.jingle;

/**
 * @see <a href="https://www.iana.org/assignments/sdp-parameters/sdp-parameters.xhtml#sdp-parameters-3">
 *      bwtype in the SDP specification</a>
 * @author Kai-Chung Yan (殷啟聰)
 * @since 0.1
 */
public enum SdpBandwidthType {
  AS,
  CT,
  RR,
  RS,
  TIAS
}