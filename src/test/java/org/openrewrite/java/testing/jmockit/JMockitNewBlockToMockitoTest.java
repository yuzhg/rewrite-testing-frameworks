package org.openrewrite.java.testing.jmockit;

import org.junit.Ignore;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.testing.jmockit.JMockitTestUtils.JMOCKIT_DEPENDENCY;
import static org.openrewrite.java.testing.jmockit.JMockitTestUtils.JUNIT_4_DEPENDENCY;
import static org.openrewrite.java.testing.jmockit.JMockitTestUtils.JUNIT_5_JUPITER_DEPENDENCY;
import static org.openrewrite.java.testing.jmockit.JMockitTestUtils.MOCKITO_CORE_DEPENDENCY;
import static org.openrewrite.java.testing.jmockit.JMockitTestUtils.MOCKITO_JUPITER_DEPENDENCY;

@SuppressWarnings({"SpellCheckingInspection", "ResultOfMethodCallIgnored", "EmptyClassInitializer"})
class JMockitNewBlockToMockitoTest implements RewriteTest {

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
                .recipeFromResources("org.openrewrite.java.testing.jmockit.JNewMockitToMockito");
    }

    @Test
    void whenTimesAndResult() {
        //language=java
        rewriteRun(
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  new Expectations() {{
                                      myObject.toString();
                                      result = "foo";
                                      times = 2;
                                  }};
                                  assertEquals("foo", myObject.toString());
                                  assertEquals("foo", myObject.toString());
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.mockito.Mockito.*;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
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

    @Test
    void whenTimesAndResultWithInThrow() {
        //language=java
        rewriteRun(
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertThrows;
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  assertThrows(IllegalStateException.class, () -> {
                                      new Expectations() {{
                                          myObject.toString();
                                          result = "foo";
                                          times = 2;
                                      }};
                                      assertEquals("foo", myObject.toString());
                                      assertEquals("foo", myObject.toString());
                                  });
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertThrows;
                          import static org.mockito.Mockito.*;
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  assertThrows(IllegalStateException.class, () -> {
                                      doReturn("foo").when(myObject).toString();
                                      assertEquals("foo", myObject.toString());
                                      assertEquals("foo", myObject.toString());
                                      verify(myObject, times(2)).toString();
                                  });
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenTimes() {
        //language=java
        rewriteRun(
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.junit.jupiter.api.Assertions.assertNull;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  new Expectations() {{
                                      myObject.toString();
                                      times = 2;
                                  }};
                                  assertNull(myObject.toString());
                                  assertNull(myObject.toString());
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.junit.jupiter.api.Assertions.assertNull;
                          import static org.mockito.Mockito.*;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  doReturn(null).when(myObject).toString();
                                  assertNull(myObject.toString());
                                  assertNull(myObject.toString());
                                  verify(myObject, times(2)).toString();
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenNoTimesNoResult() {
        //language=java
        rewriteRun(
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.junit.jupiter.api.Assertions.assertNull;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  new Expectations() {{
                                      myObject.toString();
                                  }};
                                  assertNull(myObject.toString());
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.junit.jupiter.api.Assertions.assertNull;
                          import static org.mockito.Mockito.*;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  doReturn(null).when(myObject).toString();
                                  assertNull(myObject.toString());
                                  verify(myObject, atLeast(1)).toString();
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenResult() {
        //language=java
        rewriteRun(
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertThrows;
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  assertThrows(IllegalStateException.class, () -> {
                                      new Expectations() {{
                                          myObject.toString();
                                          result = "foo";
                                      }};
                                      assertEquals("foo", myObject.toString());
                                  });
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertThrows;
                          import static org.mockito.Mockito.*;
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  assertThrows(IllegalStateException.class, () -> {
                                      doReturn("foo").when(myObject).toString();
                                      assertEquals("foo", myObject.toString());
                                      verify(myObject, atLeast(1)).toString();
                                  });
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenThrow() {
        //language=java
        rewriteRun(
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertThrows;
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  assertThrows(IllegalStateException.class, () -> {
                                      new Expectations() {{
                                          myObject.toString();
                                          result = new IllegalStateException("foo");
                                      }};
                                      assertEquals("foo", myObject.toString());
                                  });
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertThrows;
                          import static org.mockito.Mockito.*;
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  assertThrows(IllegalStateException.class, () -> {
                                      doThrow(new IllegalStateException("foo")).when(myObject).toString();
                                      assertEquals("foo", myObject.toString());
                                      verify(myObject, atLeast(1)).toString();
                                  });
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenMultiResults() {
        //language=java
        rewriteRun(
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertThrows;
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  assertThrows(IllegalStateException.class, () -> {
                                      new Expectations() {{
                                          myObject.toString();
                                          result = "foo";
                                          result = "foo1";
                                          result = "foo2";
                                          times = 3;
                                      }};
                                      assertEquals("foo", myObject.toString());
                                      assertEquals("foo1", myObject.toString());
                                      assertEquals("foo2", myObject.toString());
                                  });
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertThrows;
                          import static org.mockito.Mockito.*;
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  assertThrows(IllegalStateException.class, () -> {
                                      doReturn("foo", "foo1", "foo2").when(myObject).toString();
                                      assertEquals("foo", myObject.toString());
                                      assertEquals("foo1", myObject.toString());
                                      assertEquals("foo2", myObject.toString());
                                      verify(myObject, times(3)).toString();
                                  });
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenMultiNonLiteralResults() {
        //language=java
        rewriteRun(
                java(
                        """
                          import java.math.BigInteger;
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertThrows;
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  assertThrows(IllegalStateException.class, () -> {
                                      new Expectations() {{
                                          myObject.toString();
                                          result = BigInteger.valueOf(30 * 3).add(BigInteger.ONE).toString();
                                          result = BigInteger.valueOf(30 * 4).add(BigInteger.ONE).toString();
                                          result = BigInteger.valueOf(30 * 5).add(BigInteger.ONE).toString();
                                          times = 3;
                                      }};
                                      assertEquals("foo", myObject.toString());
                                      assertEquals("foo1", myObject.toString());
                                      assertEquals("foo2", myObject.toString());
                                  });
                              }
                          }
                          """,
                        """
                          import java.math.BigInteger;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertThrows;
                          import static org.mockito.Mockito.*;
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  assertThrows(IllegalStateException.class, () -> {
                                      doReturn(BigInteger.valueOf(30 * 3).add(BigInteger.ONE).toString(), BigInteger.valueOf(30 * 4).add(BigInteger.ONE).toString(), BigInteger.valueOf(30 * 5).add(BigInteger.ONE).toString()).when(myObject).toString();
                                      assertEquals("foo", myObject.toString());
                                      assertEquals("foo1", myObject.toString());
                                      assertEquals("foo2", myObject.toString());
                                      verify(myObject, times(3)).toString();
                                  });
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenTimesAndResultComplexType() {
        //language=java
        rewriteRun(
                java(
                        """
                          import java.math.BigInteger;
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  new Expectations() {{
                                      myObject.toString();
                                      result = BigInteger.valueOf(1000 * 3).add(BigInteger.ONE);
                                      times = 2;
                                  }};
                                  assertEquals("foo", myObject.toString());
                                  assertEquals("foo", myObject.toString());
                              }
                          }
                          """,
                        """
                          import java.math.BigInteger;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.mockito.Mockito.*;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  doReturn(BigInteger.valueOf(1000 * 3).add(BigInteger.ONE)).when(myObject).toString();
                                  assertEquals("foo", myObject.toString());
                                  assertEquals("foo", myObject.toString());
                                  verify(myObject, times(2)).toString();
                              }
                          }
                          """
                )
        );
    }

    @Test
    void whenIntResult() {
        //language=java
        rewriteRun(
                java(
                        """
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              Object myObject;
                          
                              void test() {
                                  new Expectations() {{
                                      myObject.toString();
                                      result = 10;
                                  }};
                                  assertEquals(10, myObject.toString());
                                  new Expectations() {{
                                      myObject.toString();
                                      this.result = 100;
                                  }};
                                  assertEquals(100, myObject.toString());
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.mockito.Mockito.*;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              Object myObject;
                          
                              void test() {
                                  doReturn(10).when(myObject).toString();
                                  assertEquals(10, myObject.toString());
                                  verify(myObject, atLeast(1)).toString();
                                  doReturn(100).when(myObject).toString();
                                  assertEquals(100, myObject.toString());
                                  verify(myObject, atLeast(1)).toString();
                              }
                          }
                          """
                )
        );
    }

    // my case don't have returns, not handle it now.
    @Ignore
    void whenReturnsInOneLine() {
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
                          import mockit.Expectations;
                          import mockit.Mocked;
                          import mockit.integration.junit5.JMockitExtension;
                          import org.junit.jupiter.api.extension.ExtendWith;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          
                          @ExtendWith(JMockitExtension.class)
                          class MyTest {
                              @Mocked
                              MyObject myObject;
                          
                              void test() throws RuntimeException {
                                  new Expectations() {{
                                      myObject.getSomeField();
                                      returns("foo", "bar");
                                  }};
                                  assertEquals("foo", myObject.getSomeField());
                                  assertEquals("bar", myObject.getSomeField());
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.extension.ExtendWith;
                          import org.mockito.Mock;
                          import org.mockito.junit.jupiter.MockitoExtension;
                          
                          import static org.junit.jupiter.api.Assertions.assertEquals;
                          import static org.mockito.Mockito.when;
                          import static org.mockito.Mockito.atLeast;
                          
                          @ExtendWith(MockitoExtension.class)
                          class MyTest {
                              @Mock
                              MyObject myObject;
                          
                              void test() throws RuntimeException {
                                  doReturn("foo", "bar").when(myObject).getSomeField();
                                  assertEquals("foo", myObject.getSomeField());
                                  assertEquals("bar", myObject.getSomeField());
                                  verify(myObject, atLeast(1)).getSomeField();
                              }
                          }
                          """
                )
        );
    }
}