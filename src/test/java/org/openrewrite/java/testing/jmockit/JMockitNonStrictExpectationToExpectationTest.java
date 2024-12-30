package org.openrewrite.java.testing.jmockit;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.testing.jmockit.JMockitTestUtils.*;

class JMockitNonStrictExpectationToExpectationTest implements RewriteTest {
    private static final String LEGACY_JMOCKIT_DEPENDENCY = "jmockit-1.8";

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
                                MOCKITO_JUPITER_DEPENDENCY,
                                LEGACY_JMOCKIT_DEPENDENCY
                        )
        ).recipe(new JMockitNonStrictExpectationToExpectation());
    }

    @Test
    void whenNullResult() {
        //language=java
        rewriteRun(
                java(
                        """
                          class MyObject {
                              public String getSomeField() {
                                  return "X";
                              }
                          }
                          """
                ),
                java(
                        """
                          import mockit.NonStrictExpectations;
                          import mockit.Mocked;
                          import mockit.integration.junit4.JMockit;
                          import org.junit.runner.RunWith;
                          import static org.junit.Assert.assertNull;

                          @RunWith(JMockit.class)
                          class MyTest {
                              @Mocked
                              MyObject myObject;

                              void test() {
                                  new NonStrictExpectations() {{
                                      myObject.getSomeField();
                                      result = null;
                                      myObject.getSomeField();
                                      result = null;
                                  }};
                                  assertNull(myObject.getSomeField());
                              }
                          }
                          """,
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit4.JMockit;
                          import org.junit.runner.RunWith;
                          import static org.junit.Assert.assertNull;

                          @RunWith(JMockit.class)
                          class MyTest {
                              @Mocked
                              MyObject myObject;

                              void test() {
                                  new Expectations() {{
                                      myObject.getSomeField();
                                      result = null;
                                      minTimes = 0;
                                      myObject.getSomeField();
                                      result = null;
                                      minTimes = 0;
                                  }};
                                  assertNull(myObject.getSomeField());
                              }
                          }
                          """
                )
        );
    }
}
