/*
 * Copyright (C) 2017 Kai-Chung Yan (殷啟聰)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

/**
 * Provides fundamental classes for building an XMPP client.
 * {@link chat.viska.xmpp.AbstractSession} is the main entry point.
 *
 * <h1>To {@code null} or Not to {@code null}?</h1>
 *
 * All method parameters and method return types are annotated with a
 * {@literal @}{@link io.reactivex.annotations.Nullable} or
 * {@literal @}{@link io.reactivex.annotations.NonNull}. If a {@code null}
 * passed to a {@link io.reactivex.annotations.NonNull} parameter, a
 * {@link java.lang.NullPointerException} is guaranteed to be thrown. If a
 * method returns a {@link io.reactivex.annotations.Nullable} value, it is
 * guaranteed to explained why in the documentations.
 */
package chat.viska.xmpp;