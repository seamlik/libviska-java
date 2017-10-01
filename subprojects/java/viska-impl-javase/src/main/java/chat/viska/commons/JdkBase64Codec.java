package chat.viska.commons;

import java.util.Base64;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class JdkBase64Codec extends Base64Codec {

  @Override
  @Nonnull
  public String encode(@Nullable final byte[] data) {
    return data == null ? "" : Base64.getEncoder().encodeToString(data);
  }

  @Override
  @Nonnull
  public byte[] decode(@Nullable final String base64) {
    return base64 == null ? new byte[0] : Base64.getDecoder().decode(base64);
  }
}
