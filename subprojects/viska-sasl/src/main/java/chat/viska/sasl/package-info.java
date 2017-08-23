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

/**
 * Provides classes for supporting
 * <a href="https://datatracker.ietf.org/doc/rfc4422">SASL</a>. This package
 * aims to serve as an alternative solution to the one provided by Java SE and
 * be usable in environments like Android where {@link javax.security.sasl} is
 * unavailable.
 *
 * <p>When using the classes of this package, string normalization like
 * {@code stringprep} or
 * <a href="https://datatracker.ietf.org/wg/precis">PRECIS</a> needs to be done
 * by the user manually, and strings like username and password are always
 * assumed already normalized.</p>
 */
package chat.viska.sasl;