/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.driver.api.mapper.annotations;

import com.datastax.oss.driver.api.mapper.entity.naming.GetterStyle;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates an {@link Entity} to customize certain aspects of the introspection process that
 * determines which methods are considered as properties, and how new instances will be created.
 *
 * <p>Example:
 *
 * <pre>
 * &#64;Entity
 * &#64;PropertyStrategy(getterStyle = SHORT)
 * public class Account {
 *   ...
 * }
 * </pre>
 *
 * This annotation can be inherited from an interface or parent class.
 *
 * <p>When neither the entity class nor any of its parent is explicitly annotated, the mapper will
 * assume context-dependent defaults:
 *
 * <ul>
 *   <li>for a Scala case class: {@code mutable = false} and {@code getterStyle = SHORT}. The mapper
 *       detects this case by checking if the entity implements {@code scala.Product}.
 *   <li>for a Kotlin data class: {@code mutable = false} and {@code getterStyle = JAVABEANS}. The
 *       mapper detects this case by checking if the entity is annotated with {@code
 *       kotlin.Metadata}, and if it has any method named {@code component1} (both of these are
 *       added automatically by the Kotlin compiler).
 *   <li>any other case: {@code mutable = true} and {@code getterStyle = JAVABEANS}
 * </ul>
 *
 * Not that this only applies if the annotation is completely absent. If it is present with only
 * some of its attributes, the remaining attributes will get the default declared by the annotation,
 * not the context-dependent default above (for example, if a Kotlin data class is annotated with
 * {@code @PropertyStrategy(getterStyle = SHORT)}, it will be mutable).
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PropertyStrategy {

  /** The style of getter. See {@link GetterStyle} and its constants for more explanations. */
  GetterStyle getterStyle() default GetterStyle.JAVABEANS;

  /**
   * Whether the entity is mutable.
   *
   * <p>If this is set to false:
   *
   * <ul>
   *   <li>the mapper won't try to discover setters for the properties;
   *   <li>it will assume that the entity class has a visible constructor that takes all the
   *       non-transient properties as arguments.
   * </ul>
   */
  boolean mutable() default true;
}
