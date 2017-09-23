package chat.viska.commons;

import chat.viska.commons.Base64Codec;
import javax.annotation.Nonnull;

public class JdkBase64Codec extends Base64Codec {

  @Override
  public String encode(@Nonnull final byte[] data) {
    return java.util.Base64.getEncoder().encodeToString(data);
  }

  @Override
  public byte[] decode(@Nonnull final String base64) {
    return java.util.Base64.getDecoder().decode(base64);
  }
}
