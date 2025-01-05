/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.jmockit;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class JMockitNewBlockToMockito extends Recipe {
    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "My JMockit Rewriter";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "Rewrites JMockit expectation blocks.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(JMockitBlockType.Expectations.getFqn(), false),
                new JMockitNewBlockVisitor());
    }

    private static class JMockitNewBlockVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final String[] imports = new String[]{
                "org.mockito.Mockito.*",
                "org.mockito.ArgumentMatchers.*"
        };

        @Override
        public J.Block visitBlock(J.Block block, ExecutionContext executionContext) {
            block = super.visitBlock(block, executionContext);

            Cursor cursor = getCursor();
            if (null == cursor.getParent()) {
                return block;
            }

            Object value = cursor.getParent().getValue();
            // If inside a function call, or inside a lambda like `assertThrows(IllegalStateException.class, () -> {}`
            if (!(value instanceof J.MethodDeclaration) && !(value instanceof J.Lambda)) {
                return block;
            }

            // then rewrite the statements.
            List<Statement> statements = block.getStatements();
            if (statements.isEmpty()) {
                return block;
            }

            // confirm if we really need rewrite.
            boolean needRewrite = false;
            for (Statement stmt : statements) {
                if (stmt instanceof J.NewClass) {
                    TypeTree clazz = ((J.NewClass) stmt).getClazz();
                    if (null != clazz && TypeUtils.isOfClassType(clazz.getType(),
                            JMockitBlockType.Expectations.getFqn())) {
                        needRewrite = true;
                    }
                }
            }
            if (!needRewrite) {
                return block;
            }

            this.maybeRemoveImport(JMockitBlockType.Expectations.getFqn());
            this.maybeAddImport("org.mockito.Mockito", "*", false);

            List<Function<JavaCoordinates, J.Block>> verifyStatements = new ArrayList<>();
            block = block.withStatements(Collections.emptyList());
            updateCursor(block);
            List<Statement> updatedStatements = block.getStatements();
            JavaCoordinates coordinates = block.getCoordinates().firstStatement();

            ArgumentMatchersRewriter amr = new ArgumentMatchersRewriter(this, executionContext, block);
            J.MethodInvocation preMockedStub = null;

            // record test statements. If there is no, means it is an old setup/util functions. Need manual changes.
            int testingStatements = 0;
            for (Statement stmt : statements) {
                // if Expectation block
                if (stmt instanceof J.NewClass) {
                    J.Block body = ((J.NewClass) stmt).getBody();
                    TypeTree clazz = ((J.NewClass) stmt).getClazz();
                    if (null == body || null == clazz || !TypeUtils.isOfClassType(clazz.getType(),
                            JMockitBlockType.Expectations.getFqn())) {
                        updatedStatements.add(stmt);
                        coordinates = stmt.getCoordinates().after();
                        continue;
                    }

                    // expand the inner statements, merge the `{{x;x;x;}}` case and `{{x}{x}{x}}` case.
                    List<Statement> expStatements = body.getStatements().stream().flatMap(
                            bl -> ((J.Block) bl).getStatements().stream()).collect(
                            Collectors.toList());

                    // find out result variables if any
                    Set<String> resultVars = new HashSet<>();
                    expStatements.forEach(s -> {
                        if (s instanceof J.Assignment) {
                            J.Assignment as = (J.Assignment) s;
                            String varName = as.getVariable().toString();
                            Expression obj = as.getAssignment();
                            if ("result".equals(varName) && !(obj instanceof J.Literal)) {
                                resultVars.add(obj.toString());
                            }
                        }
                    });

                    List<Expression> results = new ArrayList<>();
                    Integer times = null;
                    Integer minTimes = null;
                    J.MethodInvocation stub = null;
                    for (Statement expStatement : expStatements) {
                        // now handle each statement.
                        if (expStatement instanceof J.MethodInvocation) {
                            J.MethodInvocation mi = (J.MethodInvocation) expStatement;
                            Expression select = mi.getSelect();
                            if (null != select && resultVars.contains(select.toString())) {
                                // operations on result variables
                                expStatement = expStatement.withPrefix(stmt.getPrefix());
                                updatedStatements.add(expStatement);
                                coordinates = expStatement.getCoordinates().after();
                            } else {
                                // Have operations to mock.

                                // if mocking the same method, need to finish previous verify.
                                if (null != preMockedStub && preMockedStub.toString().equalsIgnoreCase(mi.toString()) && !verifyStatements.isEmpty()) {
                                    block = addVerifications(block, updatedStatements, verifyStatements, coordinates);
                                    updateCursor(block);
                                    coordinates = block.getCoordinates().lastStatement();
                                }

                                block = doMocking(executionContext, stub, results, coordinates, times, minTimes,
                                        verifyStatements).orElse(block);
                                updatedStatements = block.getStatements();
                                coordinates = block.getCoordinates().lastStatement();

                                // now record and start a new stub.
                                preMockedStub = stub;
                                stub = amr.rewriteMethodInvocation(mi);
                                times = null;
                                minTimes = null;
                                results.clear();
                            }
                        } else if (expStatement instanceof J.Assignment) {
                            J.Assignment as = (J.Assignment) expStatement;
                            String varName = as.getVariable().toString();
                            // handle this.time/this.result
                            if (varName.contains(".")) {
                                String[] split = varName.split("\\.");
                                varName = split[split.length - 1];
                            }
                            if ("result".equalsIgnoreCase(varName)) {
                                results.add(as.getAssignment());
                            } else if ("times".equalsIgnoreCase(varName)) {
                                times = Integer.parseInt(as.getAssignment().toString());
                            } else if ("minTimes".equalsIgnoreCase(varName)) {
                                minTimes = Integer.parseInt(as.getAssignment().toString());
                            } else {
                                expStatement = expStatement.withPrefix(stmt.getPrefix());
                                updatedStatements.add(expStatement);
                                coordinates = expStatement.getCoordinates().after();
                            }
                        } else {
                            expStatement = expStatement.withPrefix(stmt.getPrefix());
                            updatedStatements.add(expStatement);
                            coordinates = expStatement.getCoordinates().after();
                        }
                    }

                    block = block.withStatements(updatedStatements);
                    updateCursor(block);

                    block = doMocking(executionContext, stub, results, coordinates, times, minTimes,
                            verifyStatements).orElse(block);
                    updatedStatements = block.getStatements();
                    coordinates = block.getCoordinates().lastStatement();
                    preMockedStub = stub;
                } else {
                    updatedStatements.add(stmt);
                    coordinates = stmt.getCoordinates().after();
                    testingStatements++;
                }
            }


            block = addVerifications(block, updatedStatements, verifyStatements, coordinates);
            if (0 == testingStatements) {
                List<Statement> st = block.getStatements();
                Statement last = st.get(st.size() - 1);
                List<Comment> comments = new ArrayList<>(last.getComments());
                Space prefix = last.getPrefix();
                comments.add(new TextComment(false, "TODO: testing", prefix.getWhitespace(), Markers.EMPTY));
                last = last.withComments(comments);
                st.set(st.size() - 1, last);
                block = block.withStatements(st);
            }

            return block;
        }

        private J.@NotNull Block addVerifications(J.Block block, List<Statement> updatedStatements,
                                          List<Function<JavaCoordinates, J.Block>> verifyStatements,
                                          JavaCoordinates coordinates) {
            // Add back all verification.
            block = block.withStatements(updatedStatements);
            updateCursor(block);
            for (Function<JavaCoordinates, J.Block> verifyStatement : verifyStatements) {
                block = verifyStatement.apply(coordinates);
                coordinates = block.getCoordinates().lastStatement();
                updateCursor(block);
            }
            verifyStatements.clear();
            return block;
        }

        private Optional<J.Block> doMocking(ExecutionContext executionContext, @Nullable J.MethodInvocation stub, List<Expression> resultList,
                                           JavaCoordinates coordinates, @Nullable Integer times, @Nullable Integer minTimes,
                                           List<Function<JavaCoordinates, J.Block>> verifyStatements) {
            if (null == stub || null == stub.getSelect()) {
                return Optional.empty();
            }

            // This is not the first mock sections. End the previous stub.
            List<Object> templateParams = new ArrayList<>();
            StringBuilder template = new StringBuilder();
            if (resultList.isEmpty()) {
                // There is no result specified, Need to analyse the stub to create the right mock.
                // not complete yet, refer https://javadoc.io/doc/org.jmockit/jmockit/latest/index.html result section for more details.
                JavaType.Method methodType = stub.getMethodType();
                if (null == methodType || "void".equalsIgnoreCase(methodType.getReturnType().toString())) {
                    template.append("doAnswer(invocation -> null)");
                } else {
                    template.append("doReturn(null)");
                }
            } else {
                // If more than 1 result, it must be doReturn mock.
                if (resultList.size() > 1) {
                    template.append(resultList.stream().map(r -> {
                        if (r instanceof J.Literal) {
                            return ((J.Literal) r).getValueSource();
                        } else {
                            templateParams.add(r);
                            return "#{any()}";
                        }
                    }).collect(Collectors.joining(", ", "doReturn(", ")")));
                } else {
                    Expression res = resultList.get(0);
                    JavaType resType = res.getType();
                    if (TypeUtils.isAssignableTo(Throwable.class.getName(), resType)) {
                        template.append("doThrow(#{any()})");
                        templateParams.add(res);
                    } else {
                        template.append("doReturn(#{any()})");
                        templateParams.add(res);
                    }
                }
            }

            Expression select = stub.getSelect();
            String methodName = stub.getName().getSimpleName();
            templateParams.add(select);
            templateParams.add(methodName);
            ArrayList<Object> args = new ArrayList<>();
            String argumentsTemplate = stub.getArguments().stream().filter(a -> !(a instanceof J.Empty))
                    .map(a -> {
                        if (a instanceof J.Literal) {
                            return ((J.Literal) a).getValueSource();
                        } else {
                            args.add(a);
                            return "#{any()}";
                        }
                    }).collect(Collectors.joining(", ", "(", ");"));

            templateParams.addAll(args);

            J.Block bl = JavaTemplate.builder(template.append(".when(#{any()}).#{}").append(argumentsTemplate).toString())
                    .staticImports(imports)
                    .javaParser(JMockitUtils.getJavaParser(executionContext))
                    .build()
                    .apply(getCursor(), coordinates, templateParams.toArray());
            updateCursor(bl);

            // handle verifications
            if (null == times && null == minTimes) {
                // default behavior
                minTimes = 1;
            }

            templateParams.clear();
            templateParams.add(select);
            templateParams.add(methodName);
            templateParams.addAll(args);
            if (null != times && times > 0) {
                templateParams.add(1, times);
                verifyStatements.add(co -> JavaTemplate.builder("verify(#{any()}, times(#{})).#{}" + argumentsTemplate)
                        .staticImports(imports)
                        .javaParser(JMockitUtils.getJavaParser(executionContext))
                        .build()
                        .apply(getCursor(), co, templateParams.toArray()));
            } else if (null != minTimes && minTimes > 0) {
                templateParams.add(1, minTimes);
                verifyStatements.add(co -> JavaTemplate.builder("verify(#{any()}, atLeast(#{})).#{}" + argumentsTemplate)
                        .staticImports(imports)
                        .javaParser(JMockitUtils.getJavaParser(executionContext))
                        .build()
                        .apply(getCursor(), co, templateParams.toArray()));
            }

            return Optional.of(bl);
        }
    }
}
