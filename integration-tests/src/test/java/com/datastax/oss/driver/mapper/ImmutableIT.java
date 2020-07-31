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
package com.datastax.oss.driver.mapper;

import static com.datastax.oss.driver.api.mapper.entity.naming.GetterStyle.SHORT;

import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.IntrospectionStrategy;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import java.util.UUID;

public class ImmutableIT extends InventoryITBase {

  @Entity
  @IntrospectionStrategy(getterStyle = SHORT, mutable = false)
  public static class ImmutableProduct {
    @PartitionKey private final UUID id;
    private final String description;
    private final Dimensions dimensions;

    public ImmutableProduct(UUID id, String description, Dimensions dimensions) {
      this.id = id;
      this.description = description;
      this.dimensions = dimensions;
    }

    public UUID id() {
      return id;
    }

    public String description() {
      return description;
    }

    public Dimensions dimensions() {
      return dimensions;
    }
  }
}
