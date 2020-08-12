## Increment methods

Annotate a DAO method with [@Increment] to generate a query that updates a counter table using the
fields of an entity:

```java
// CREATE TABLE votes(article_id int PRIMARY KEY, up_votes counter, down_votes counter);

@Entity
public class Votes {
  @PartitionKey private int articleId;
  private long upVotes;
  private long downVotes;

  ... // constructor(s), getters and setters, etc.
}

@Dao
public interface VotesDao {
  @Increment
  void increment(Votes deltas);
  
  @Select
  Votes findById(int articleId);
}
```

### Counter specificities

#### Increments, not updates

[@Increment] works differently than update methods: the entity's fields represent **deltas** to be
applied to the counter columns, not the new totals. For example, this is how you would add one new
upvote:

```java
Votes deltas = new Votes(articleId,
    1,   // upVotes
    0);  // downVotes
votesDao.increment(deltas);
```

In contrast, selecting an entity returns the counters' current values. Do not try to update that
object and "write it back" to the database: 

```java
Votes totals = votesDao.findById(articleId);

// DON'T DO THIS:
totals.setUpVotes(totals.getUpVotes() + 1);
votesDao.increment(totals);
// It would increment all the counters by their current value in addition to the +1
```

This is consistent with the intended use of Cassandra counters: you only update them by increments,
not by read-modify-write.

#### Tables with multiple counters

The mapper-generated code prepares a single UPDATE statement that increments all counter columns:

```
UPDATE votes SET up_votes = up_votes + ?, down_votes = down_votes + ? ...
```

This means that if some of the entity's fields are left to 0, the executed query will look like
`up_votes = up_votes + 1, down_votes = down_votes + 0`. The Cassandra server-side code does not
special-case 0-increments, so even though there shouldn't be any significant overhead, they will
still incur a bit of unnecessary processing. If you want to avoid that, there are a couple of
approaches:

* if you are using Cassandra 2.2 or above, or DSE 5.0 or above, you can declare your entity fields
  as `java.lang.Long` and leave the unused fields to `null` instead of 0. Increment methods use
  the `DO_NOT_SET` [null saving strategy](../null_saving), and will therefore ignore them.
* if you are using Cassandra 2.1, the above won't work because the server doesn't support unset
  values. What you can do instead is create a dedicated "increment entity" for each counter column:
  
    ```java
    @Entity public class UpVotes {
        @PartitionKey private int articleId;
        @CqlName("up_votes") private long upVotesIncrement;
    }
    @Entity public class DownVotes {
        @PartitionKey private int articleId;
        @CqlName("down_votes") private long downVotesIncrement;
    }
    @Dao public interface VotesDao {
      @Increment void incrementUpVotes(UpVotes delta);
      @Increment void incrementDownVotes(DownVotes delta);
      @Select Votes findById(int articleId);
    }
    ```

### Parameters

The first parameter must be an entity instance. All of its non-PK properties will be interpreted as
increments for the corresponding counter columns.

A `Function<BoundStatementBuilder, BoundStatementBuilder>` or `UnaryOperator<BoundStatementBuilder>`
can be added as the **second** parameter. It will be applied to the statement before execution. This
allows you to customize certain aspects of the request (page size, timeout, etc) at runtime. See
[statement attributes](../statement_attributes/).

### Return type

The method can return `void`, a void [CompletionStage] or [CompletableFuture], or a
[ReactiveResultSet].

### Target keyspace and table

If a keyspace was specified [when creating the DAO](../../mapper/#dao-factory-methods), then the
generated query targets that keyspace. Otherwise, it doesn't specify a keyspace, and will only work
if the mapper was built from a session that has a [default keyspace] set.

If a table was specified when creating the DAO, then the generated query targets that table.
Otherwise, it uses the default table name for the entity (which is determined by the name of the
entity class and the naming convention).

[@Increment]:        https://docs.datastax.com/en/drivers/java/4.8/com/datastax/oss/driver/api/mapper/annotations/Increment.html
[ReactiveResultSet]: https://docs.datastax.com/en/drivers/java/4.8/com/datastax/dse/driver/api/core/cql/reactive/ReactiveResultSet.html
[default keyspace]:  https://docs.datastax.com/en/drivers/java/4.8/com/datastax/oss/driver/api/core/session/SessionBuilder.html#withKeyspace-com.datastax.oss.driver.api.core.CqlIdentifier-

[CompletionStage]:   https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html
[CompletableFuture]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html
