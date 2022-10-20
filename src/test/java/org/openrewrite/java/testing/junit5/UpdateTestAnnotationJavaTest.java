/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class UpdateTestAnnotationJavaTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new UpdateTestAnnotation())
          .parser(JavaParser.fromJavaVersion().classpath("junit"))
          .executionContext(new InMemoryExecutionContext());
    }

    @Test
    void markUnrefactorableReferences() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              public class MyTest {
                  Object o = Test.class;
              }
              """,
            """
              /*~~(This import should have been removed by this recipe.)~~>*/import org.junit.Test;
              public class MyTest {
                  Object o = /*~~(This still has a type of `org.junit.Test`)~~>*/Test.class;
              }
              """
          )
        );
    }

    @SuppressWarnings("JavadocReference")
    @Test
    void usedInJavadoc() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              /** @see org.junit.Test */
              public class MyTest {
                  @Test
                  public void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
                            
              /**  */
              public class MyTest {
                  @Test
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void fullyQualified() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Test;
              public class MyTest {
                  @org.junit.Test
                  public void feature1() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
                            
              public class MyTest {
                  @org.junit.jupiter.api.Test
                  void feature1() {
                  }
              }
              """
          )
        );
    }

    @Test
    void mixedFullyQualifiedAndNot() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Test;
              public class MyTest {
                  @org.junit.Test
                  public void feature1() {
                  }
                  
                  @Test
                  void feature2() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
                            
              public class MyTest {
                  @org.junit.jupiter.api.Test
                  void feature1() {
                  }
                  
                  @Test
                  void feature2() {
                  }
              }
              """
          )
        );
    }
}
