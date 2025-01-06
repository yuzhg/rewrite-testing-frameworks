package org.openrewrite.java.testing.jmockit;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.testing.jmockit.JMockitTestUtils.JMOCKIT_DEPENDENCY;
import static org.openrewrite.java.testing.jmockit.JMockitTestUtils.JUNIT_4_DEPENDENCY;
import static org.openrewrite.java.testing.jmockit.JMockitTestUtils.JUNIT_5_JUPITER_DEPENDENCY;
import static org.openrewrite.java.testing.jmockit.JMockitTestUtils.MOCKITO_CORE_DEPENDENCY;
import static org.openrewrite.java.testing.jmockit.JMockitTestUtils.MOCKITO_JUPITER_DEPENDENCY;

class JMockitAnnotationFixTest  implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(
                        JavaParser.fromJavaVersion()
                                .logCompilationWarningsAndErrors(true)
                                .classpathFromResources(
                                        new InMemoryExecutionContext(),
                                        JUNIT_4_DEPENDENCY,
                                        JUNIT_5_JUPITER_DEPENDENCY,
                                        JMOCKIT_DEPENDENCY,
                                        MOCKITO_CORE_DEPENDENCY,
                                        MOCKITO_JUPITER_DEPENDENCY
                                )
                )
                .typeValidationOptions(TypeValidation.none())
                .recipe(new JMockitAnnotationFix());
    }

    @Test
    void tst() {
        //language=java
        rewriteRun(
                java(
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.springframework.beans.factory.annotation.Autowired;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.mockito.Mockito.*;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Autowired
                              Object myObject;
                          
                              void test() {
                                  doReturn("foo").when(myObject).toString();
                                  assertEquals("foo", myObject.toString());
                                  assertEquals("foo", myObject.toString());
                                  verify(myObject, times(2)).toString();
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          import org.springframework.boot.test.mock.mockito.SpyBean;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.mockito.Mockito.*;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @SpyBean
                              Object myObject;
                          
                              void test() {
                                  doReturn("foo").when(myObject).toString();
                                  assertEquals("foo", myObject.toString());
                                  assertEquals("foo", myObject.toString());
                                  verify(myObject, times(2)).toString();
                              }
                          }
                          """
                )
        );
    }
}