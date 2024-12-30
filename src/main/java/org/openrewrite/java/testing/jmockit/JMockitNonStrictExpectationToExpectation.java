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

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
@Slf4j
public class JMockitNonStrictExpectationToExpectation extends Recipe {

    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "Rewrite JMockit for upgrade!";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "Upgrade Jmockit from 1.8 to 1.49.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        val usesTypes = new UsesType[]{new UsesType<>(JMockitBlockType.NonStrictExpectations.getFqn(), false)};
        return Preconditions.check(Preconditions.or(usesTypes), new JmockitUpgradeRewriter());
    }

    private static class JmockitUpgradeRewriter extends JavaIsoVisitor<ExecutionContext> {
        private static final String MSG_KEY_IN_NON_STRICT_EXP = "inNonStrictExpectation";
        public static final String KEYWORDS_RETURNS = "returns";

        @Override
        public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext executionContext) {
            J.NewClass exp = newClass;
            TypeTree clazz = newClass.getClazz();

            if (null == clazz) {
                return super.visitNewClass(newClass, executionContext);
            }

            // skip delegate, don't need to handle it.
            if (clazz instanceof J.ParameterizedType
                    &&  "Delegate".equalsIgnoreCase(((J.Identifier) ((J.ParameterizedType) clazz).getClazz()).getSimpleName())) {
                return newClass;
            }


            // we only care non-strict expectations
            if (!TypeUtils.isAssignableTo("mockit.NonStrictExpectations", clazz.getType())) {
                return super.visitNewClass(newClass, executionContext);
            }

            maybeRemoveImport("mockit.NonStrictExpectations");
            maybeAddImport("mockit.Expectations");

            JavaTemplate templateExpectation = JavaTemplate.builder("new Expectations() {{}}")
                    .imports("mockit.Expectations")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(executionContext, "jmockit-1.49"))
                    .build();

            J.NewClass templateNewClass = templateExpectation.apply(updateCursor(exp), exp.getCoordinates().replace());
            exp = templateNewClass.withId(exp.getId()).withArguments(exp.getArguments()).withBody(exp.getBody());
            updateCursor(exp);

            getCursor().putMessage(MSG_KEY_IN_NON_STRICT_EXP, true);
            exp = super.visitNewClass(exp, executionContext);
            getCursor().putMessage(MSG_KEY_IN_NON_STRICT_EXP, false);

            return exp;
        }

        @Override
        public J.Block visitBlock(J.Block block, ExecutionContext executionContext) {
            J.Block bl = super.visitBlock(block, executionContext);

            // if this is the out block
            if (bl.getStatements().isEmpty() || bl.getStatements().stream().anyMatch(s -> s instanceof J.Block)) {
                return bl;
            }

            if (Boolean.TRUE.equals(getCursor().<Boolean>getNearestMessage(MSG_KEY_IN_NON_STRICT_EXP, false))) {
                JavaTemplate templateMinTimes = JavaTemplate.builder("minTimes = 0;")
                        .contextSensitive()
                        .imports("mockit.Expectations")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(executionContext, "jmockit-1.49"))
                        .build();

                List<Statement> statements = bl.getStatements();
                boolean haveMock = false;
                boolean haveTimes = false;
                for (Statement s : statements) {
                     if (s instanceof J.Assignment) {
                        J.Identifier identifier = ((J.Assignment) s).getVariable().cast();
                        String simpleName = identifier.getSimpleName();
                        if (simpleName.equalsIgnoreCase("times") || simpleName.equalsIgnoreCase("minTimes")) {
                            haveTimes = true;
                            haveMock = false;
                        }
                    } else if (s instanceof J.MethodInvocation) {
                         if (KEYWORDS_RETURNS.equalsIgnoreCase(((J.MethodInvocation) s).getSimpleName())) {
                             JavaTemplate templateResults = JavaTemplate.builder("result = #{any()};")
                                     .contextSensitive()
                                     .imports("mockit.Expectations")
                                     .javaParser(JavaParser.fromJavaVersion().classpathFromResources(executionContext,
                                             "jmockit-1.49"))
                                     .build();
                             bl = templateResults.apply(getCursor(), s.getCoordinates().replace(),
                                     ((J.MethodInvocation) s).getArguments().get(0));
                             updateCursor(bl);
                         } else {
                             if (haveMock) {
                                 // create new statement of minTimes and insert.
                                 bl = templateMinTimes.apply(updateCursor(bl), s.getCoordinates().before());
                             }
                             haveMock = true;
                             haveTimes = false;
                         }
                     }
                    // we don't care other statements
                }

                if (!haveTimes) {
                    bl = templateMinTimes.apply(updateCursor(bl), bl.getStatements().get(bl.getStatements().size() - 1).getCoordinates().after());
                }
            }
            return bl;
        }

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
            J.CompilationUnit compilationUnit = super.visitCompilationUnit(cu, executionContext);
            return compilationUnit;
        }
    }
}
