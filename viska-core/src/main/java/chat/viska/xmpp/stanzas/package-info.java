/**
 * Provides classes modeling XMPP stanzas.
 * <p>
 *   The classes of this package merely represent XMPP stanzas and do not have
 *   any functionality. Therefore all classes in this package are immutable
 *   types and can be considered thread safe.
 * </p>
 * <p>
 *   When XML documents are being transformed as objects of classes in this
 *   package, a simple validation, which means checking if an absolutely
 *   mandatory attribute or element is provided, is performed.
 * </p>
 * <p>
 *   The constructors of all classes in this package also perform the simple
 *   validation mentioned above. If any absolutely required attributes or
 *   element is not provided in the parameters, an
 *   {@link java.lang.NullPointerException} or
 *   {@link java.lang.IllegalArgumentException} will be thrown and an XPath of
 *   that mandatory attribute or element will be provided via
 *   {@link java.lang.Exception#getMessage()}.
 * </p>
 * @since 0.1
 */
package chat.viska.xmpp.stanzas;