package chat.viska.rtp;

import zorg.platform.RtpPacket;

/**
 * @since 0.1
 */
public class Packet implements RtpPacket {

  @Override
  public int getHeaderLength() {
    return 0;
  }

  @Override
  public int getLength() {
    return 0;
  }

  @Override
  public byte[] getPacket() {
    return new byte[0];
  }

  @Override
  public int getPayloadLength() {
    return 0;
  }

  @Override
  public int getSequenceNumber() {
    return 0;
  }

  @Override
  public int getSscr() {
    return 0;
  }

  @Override
  public void setPayloadLength(int length) {

  }
}