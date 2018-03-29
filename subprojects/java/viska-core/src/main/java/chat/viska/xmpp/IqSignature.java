package chat.viska.xmpp;

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

public class IqSignature {

  private final String namesapce;
  private final String name;

  public IqSignature(String namesapce, String name) {
    this.namesapce = namesapce;
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public String getNamesapce() {
    return namesapce;
  }

  public boolean equals(final String namesapce, final String name) {
    return this.namesapce.equals(namesapce) && this.name.equals(name);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    IqSignature that = (IqSignature) obj;
    return Objects.equals(namesapce, that.namesapce) && Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(namesapce, name);
  }
}