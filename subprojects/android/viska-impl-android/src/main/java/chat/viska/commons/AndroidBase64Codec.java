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

import android.util.Base64;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class AndroidBase64Codec extends Base64Codec {

  @Override
  @Nonnull
  public String encode(@Nullable final byte[] data) {
    return data == null ? "" : Base64.encodeToString(data, Base64.NO_WRAP);
  }

  @Override
  @Nonnull
  public byte[] decode(@Nullable final String base64) {
    return base64 == null ? new byte[0] : Base64.decode(base64, Base64.NO_WRAP);
  }
}