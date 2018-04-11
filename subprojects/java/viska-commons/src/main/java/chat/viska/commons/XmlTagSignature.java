package chat.viska.commons;

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Signature of an XML tag, consisting of a namespace and a "local name" (i.e. without a prefix).
 */
public class XmlTagSignature {

  private final String namesapce;
  private final String name;

  /**
   * Default constructor.
   */
  public XmlTagSignature(final String namesapce, final String name) {
    this.namesapce = namesapce;
    this.name = name;
  }

  /**
   * Gets the name.
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the namespace.
   */
  public String getNamesapce() {
    return namesapce;
  }

  /**
   * Compares the signature to a pair of namespace and name.
   */
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
    XmlTagSignature that = (XmlTagSignature) obj;
    return Objects.equals(namesapce, that.namesapce) && Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(namesapce, name);
  }
}