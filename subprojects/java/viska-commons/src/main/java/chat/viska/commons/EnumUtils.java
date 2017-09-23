/*
 * Copyright 2017 Kai-Chung Yan (殷啟聰)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chat.viska.commons;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

/**
 * Provides utility functions for working with {@link Enum}s.
 */
public class EnumUtils {

  /**
   * Gets an enum value based on its value appearing in an XML document.
   * @param type Type of the enum.
   * @param value XML value.
   * @param <T> Tyle of the enum.
   * @return {@code null} if {@code value} is {@code null}.
   * @throws IllegalArgumentException If the conversion cannot be done.
   */
  @Nullable
  public static <T extends Enum<T>> T
  fromXmlValue(@Nonnull final Class<T> type,
               @Nullable final String value) throws IllegalArgumentException {
    if (StringUtils.isBlank(value)) {
      return null;
    } else {
      return Enum.valueOf(type, value.replace('-', '_').toUpperCase());
    }
  }

  /**
   * Converts an enum to a {@link String} representation for being used in an
   * XML document.
   * @param value The enum.
   * @param <T> Type of the enum.
   */
  @Nonnull
  public static <T extends Enum<T>> String
  toXmlValue(@Nonnull final T value) {
    return value.name().replace('_', '-').toLowerCase();
  }
}