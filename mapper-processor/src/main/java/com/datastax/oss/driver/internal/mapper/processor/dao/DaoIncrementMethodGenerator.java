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
package com.datastax.oss.driver.internal.mapper.processor.dao;

import static com.datastax.oss.driver.internal.mapper.processor.dao.DefaultDaoReturnTypeKind.FUTURE_OF_VOID;
import static com.datastax.oss.driver.internal.mapper.processor.dao.DefaultDaoReturnTypeKind.REACTIVE_RESULT_SET;
import static com.datastax.oss.driver.internal.mapper.processor.dao.DefaultDaoReturnTypeKind.VOID;

import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.mapper.annotations.Increment;
import com.datastax.oss.driver.api.mapper.entity.saving.NullSavingStrategy;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.relation.Relation;
import com.datastax.oss.driver.internal.mapper.processor.ProcessorContext;
import com.datastax.oss.driver.internal.mapper.processor.entity.EntityDefinition;
import com.datastax.oss.driver.internal.mapper.processor.entity.PropertyDefinition;
import com.datastax.oss.driver.shaded.guava.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

public class DaoIncrementMethodGenerator extends DaoMethodGenerator {

  public DaoIncrementMethodGenerator(
      ExecutableElement methodElement,
      Map<Name, TypeElement> typeParameters,
      TypeElement processedType,
      DaoImplementationSharedCode enclosingClass,
      ProcessorContext context) {
    super(methodElement, typeParameters, processedType, enclosingClass, context);
  }

  protected Set<DaoReturnTypeKind> getSupportedReturnTypes() {
    return ImmutableSet.of(VOID, FUTURE_OF_VOID, REACTIVE_RESULT_SET);
  }

  @Override
  public boolean requiresReactive() {
    // Validate the return type:
    DaoReturnType returnType =
        parseAndValidateReturnType(getSupportedReturnTypes(), Increment.class.getSimpleName());
    if (returnType == null) {
      return false;
    }
    return returnType.requiresReactive();
  }

  @Override
  public Optional<MethodSpec> generate() {
    // Validate the parameters:
    // - the first one must be the entity.
    // - a Function<BoundStatementBuilder, BoundStatementBuilder> can be added in second position.
    List<? extends VariableElement> parameters = methodElement.getParameters();
    VariableElement boundStatementFunction = findBoundStatementFunction(methodElement);
    if (boundStatementFunction != null) {
      parameters = parameters.subList(0, parameters.size() - 1);
    }
    TypeElement entityElement =
        parameters.isEmpty()
            ? null
            : EntityUtils.asEntityElement(parameters.get(0), typeParameters);
    if (entityElement == null) {
      context
          .getMessager()
          .error(
              methodElement,
              processedType,
              "%s methods must take the entity to update as the first parameter",
              Increment.class.getSimpleName());
      return Optional.empty();
    }
    warnIfCqlNamePresent(parameters.subList(0, 1));
    EntityDefinition entityDefinition = context.getEntityFactory().getDefinition(entityElement);

    // Validate the return type:
    DaoReturnType returnType =
        parseAndValidateReturnType(getSupportedReturnTypes(), Increment.class.getSimpleName());
    if (returnType == null) {
      return Optional.empty();
    }

    // Generate the method:
    String helperFieldName = enclosingClass.addEntityHelperField(ClassName.get(entityElement));
    String statementName =
        enclosingClass.addPreparedStatement(
            methodElement,
            (methodBuilder, requestName) ->
                generatePrepareRequest(
                    methodBuilder, requestName, entityDefinition, helperFieldName));

    CodeBlock.Builder updateStatementBlock = CodeBlock.builder();

    updateStatementBlock.addStatement(
        "$T boundStatementBuilder = $L.boundStatementBuilder()",
        BoundStatementBuilder.class,
        statementName);

    populateBuilderWithStatementAttributes(updateStatementBlock, methodElement);
    populateBuilderWithFunction(updateStatementBlock, boundStatementFunction);

    String entityParameterName = parameters.get(0).getSimpleName().toString();

    // All entity properties are bound in the generated request: primary key columns in the WHERE
    // clause, and other columns in the SET clause.
    updateStatementBlock.addStatement(
        "$1L.set($2L, boundStatementBuilder, isProtocolVersionV3 ? $3T.$4L : $3T.$5L)",
        helperFieldName,
        entityParameterName,
        NullSavingStrategy.class,
        NullSavingStrategy.SET_TO_NULL,
        NullSavingStrategy.DO_NOT_SET);

    updateStatementBlock
        .add("\n")
        .addStatement("$T boundStatement = boundStatementBuilder.build()", BoundStatement.class);

    return crudMethod(updateStatementBlock, returnType, helperFieldName);
  }

  private void generatePrepareRequest(
      MethodSpec.Builder methodBuilder,
      String requestName,
      EntityDefinition entityDefinition,
      String helperFieldName) {

    // The entity helper does not provide any building block for a counter update query, generate it
    // from scratch.

    if (!entityDefinition.getRegularColumns().iterator().hasNext()) {
      context
          .getMessager()
          .error(
              methodElement,
              processedType,
              "Entity %s does not have any non PK columns. %s is not possible",
              entityDefinition.getClassName().simpleName(),
              Increment.class.getSimpleName());
    } else {
      methodBuilder
          .addStatement("$L.throwIfKeyspaceMissing()", helperFieldName)
          .addCode(
              "$[$1T $2L = (($3L.getKeyspaceId() == null)\n"
                  + "? $4T.update($3L.getTableId())\n"
                  + ": $4T.update($3L.getKeyspaceId(), $3L.getTableId()))",
              SimpleStatement.class,
              requestName,
              helperFieldName,
              QueryBuilder.class);

      for (PropertyDefinition property : entityDefinition.getRegularColumns()) {
        // We use `append` to generate "c=c+?". QueryBuilder also has `increment` that produces
        // "c+=?", but that doesn't work with Cassandra 2.1.
        methodBuilder.addCode(
            "\n.append($1L, $2T.bindMarker($1L))", property.getCqlName(), QueryBuilder.class);
      }

      for (PropertyDefinition property : entityDefinition.getPrimaryKey()) {
        methodBuilder.addCode(
            "\n.where($1T.column($2L).isEqualTo($3T.bindMarker($2L)))",
            Relation.class,
            property.getCqlName(),
            QueryBuilder.class);
      }

      methodBuilder.addCode("\n.build()$];\n");
    }
  }
}
