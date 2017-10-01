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

import java.security.NoSuchProviderException;
import java.util.ServiceLoader;
import javax.annotation.Nullable;

public abstract class Base64Codec {

  public static Base64Codec getInstance() throws NoSuchProviderException {
    for (Base64Codec it : ServiceLoader.load(Base64Codec.class)) {
      return it;
    }
    throw new NoSuchProviderException();
  }

  public abstract String encode(@Nullable final byte[] data);

  public abstract byte[] decode(@Nullable final String base64);
}