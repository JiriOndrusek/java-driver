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

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet;
import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.session.Session;
import com.datastax.oss.driver.api.core.session.SessionBuilder;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Annotates a {@link Dao} method that increments a counter table using an instance of an {@link
 * Entity}-annotated class.
 *
 * <p>Example:
 *
 * <pre>
 * &#64;Dao
 * public interface ProductDao {
 *   &#64;Increment
 *   void increment(ProductLike productLike);
 * }
 * </pre>
 *
 * <h3>Parameters</h3>
 *
 * <p>The first parameter must be an entity instance. All of its non-PK properties will be
 * interpreted as increments for the corresponding counter columns. Negative values are allowed.
 * <b>Note that these are relative increments, not negative values</b>: if you pass an entity with a
 * value of 10, the counter will be incremented by 10, not set to 10.
 *
 * <p>A {@link Function Function&lt;BoundStatementBuilder, BoundStatementBuilder&gt;} or {@link
 * UnaryOperator UnaryOperator&lt;BoundStatementBuilder&gt;} can be added as the <b>second</b>
 * parameter. It will be applied to the statement before execution. This allows you to customize
 * certain aspects of the request (page size, timeout, etc) at runtime.
 *
 * <h3>Return type</h3>
 *
 * <p>The method can return {@code void}, a void {@link CompletionStage} or {@link
 * CompletableFuture}, or a {@link ReactiveResultSet}.
 *
 * <h3>Target keyspace and table</h3>
 *
 * <p>If a keyspace was specified when creating the DAO (see {@link DaoFactory}), then the generated
 * query targets that keyspace. Otherwise, it doesn't specify a keyspace, and will only work if the
 * mapper was built from a {@link Session} that has a {@linkplain
 * SessionBuilder#withKeyspace(CqlIdentifier) default keyspace} set.
 *
 * <p>If a table was specified when creating the DAO, then the generated query targets that table.
 * Otherwise, it uses the default table name for the entity (which is determined by the name of the
 * entity class and the naming convention).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Increment {}
