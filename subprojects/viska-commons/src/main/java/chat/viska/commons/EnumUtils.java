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

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import org.apache.commons.lang3.StringUtils;

public class EnumUtils {

  public static <T extends Enum<T>> T
  fromXmlValue(@NonNull final Class<T> type,
               @Nullable final String value) throws IllegalArgumentException {
    if (StringUtils.isBlank(value)) {
      return null;
    } else {
      return Enum.valueOf(type, value.replace('-', '_').toUpperCase());
    }
  }

  public static <T extends Enum<T>> String
  toXmlValue(@NonNull final T value) {
    return value.name().replace('_', '-').toLowerCase();
  }
}