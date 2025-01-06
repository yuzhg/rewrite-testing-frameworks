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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class JMockitAnnotationFix extends Recipe {
    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "Annotation fix";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "This recipe will change autowired to spy bean if the field is mocked.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JMockitAnnotationFixRewrite();
    }

    private static class JMockitAnnotationFixRewrite extends JavaIsoVisitor<ExecutionContext> {

        private static final String KEY_OBJ_LIST = "objList";

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl,
                                                        ExecutionContext executionContext) {
            final J.ClassDeclaration[] classDeclaration = {super.visitClassDeclaration(classDecl, executionContext)};
            Set<Object> objList = getCursor().getMessage(KEY_OBJ_LIST, Collections.emptySet());
            if (!objList.isEmpty()) {

                classDeclaration[0].getBody().getStatements().forEach(st -> {
                    if (st instanceof J.VariableDeclarations) {
                        J.VariableDeclarations vd = (J.VariableDeclarations) st;
                        if (objList.contains(vd.getVariables().get(0).getSimpleName())) {
                            vd.getLeadingAnnotations().forEach(ann -> {
                                if ("Autowired".equals(ann.getAnnotationType().toString())) {
                                    classDeclaration[0] = JavaTemplate.builder("@SpyBean")
                                            .imports("org.springframework.boot.test.mock.mockito.SpyBean")
                                            .build()
                                            .apply(getCursor(), ann.getCoordinates().replace());
                                    this.maybeRemoveImport("org.springframework.beans.factory.annotation.Autowired");
                                    this.maybeAddImport("org.springframework.boot.test.mock.mockito.SpyBean", false);
                                }
                            });
                        }
                    }
                });
            }
            return classDeclaration[0];
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, executionContext);
            if ("when".equals(method.getName().toString())) {
                String mockObj = mi.getArguments().get(0).toString();
                Cursor clazzCursor = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance);
                Set<String> objList = clazzCursor.getMessage(KEY_OBJ_LIST, new HashSet<>());
                objList.add(mockObj);
                clazzCursor.putMessage(KEY_OBJ_LIST, objList);
            }
            return mi;
        }
    }
}
