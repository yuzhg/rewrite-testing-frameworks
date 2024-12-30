package org.openrewrite.java.testing.jmockit;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

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
                )
//                .typeValidationOptions(TypeValidation.none())
                .recipe(new JMockitNonStrictExpectationToExpectation());
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

    @Test
    void whenNullResultAndTimes() {
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
                                      times = 1;
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
                                      times = 1;
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

    @Test
    void whenNullResultAndTimesAndParam() {
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
                                  new NonStrictExpectations(myObject) {{
                                      myObject.getSomeField();
                                      result = null;
                                      times = 1;
                                  }
                                  {
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
                                  new Expectations(myObject) {{
                                      myObject.getSomeField();
                                      result = null;
                                      times = 1;
                                  }
                                  {
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

    @Test
    void whenReturnAndTimesAndParam() {
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
                                  new NonStrictExpectations(myObject) {{
                                      myObject.getSomeField();
                                      times = 1;
                                      returns(null, null, new Object[0]);
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
                                  new Expectations(myObject) {{
                                      myObject.getSomeField();
                                      times = 1;
                                      result = null;
                                  }};
                                  assertNull(myObject.getSomeField());
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenWithComments() {
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
                                      result = null; // this is a comment
                                      myObject.getSomeField();
                                      result = null; // this is a comment
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
                                      result = null; // this is a comment
                                      minTimes = 0;
                                      myObject.getSomeField();
                                      result = null; // this is a comment
                                      minTimes = 0;
                                  }};
                                  assertNull(myObject.getSomeField());
                              }
                          }
                          """
                )
        );
    }


    @Test
    void whenDelegate() {
        //language=java
        rewriteRun(
                java(
                        """
                          class MyObject {
                              public String getSomeField() {
                                  return "X";
                              }
                              public void setSomeField(String s ) {
                              }
                          }
                          """
                ),
                java(
                        """
                          import mockit.NonStrictExpectations;
                          import mockit.Delegate;
                          import mockit.Mocked;
                          import mockit.integration.junit4.JMockit;
                          import org.junit.runner.RunWith;
                          import static org.junit.Assert.assertNull;

                          @RunWith(JMockit.class)
                          class MyTest {
                              @Mocked
                              MyObject myObject;

                              void test() {
                                  new NonStrictExpectations(myObject) {{
                                      myObject.getSomeField();
                                      result = new Delegate<Object>() {
                                         @SuppressWarnings("unused")
                                         void getSomeField(){
                                             myObject.setSomeField("X");
                                         }
                                      };
                                  }};
                                  assertNull(myObject.getSomeField());
                              }
                          }
                          """,
                        """
                          import mockit.Delegate;
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
                                  new Expectations(myObject) {{
                                      myObject.getSomeField();
                                      result = new Delegate<Object>() {
                                         @SuppressWarnings("unused")
                                         void getSomeField(){
                                             myObject.setSomeField("X");
                                         }
                                      };
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
