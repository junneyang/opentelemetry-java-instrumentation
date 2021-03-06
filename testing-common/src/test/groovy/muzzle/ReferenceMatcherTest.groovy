/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package muzzle

import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.ABSTRACT
import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.INTERFACE
import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.NON_INTERFACE
import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.NON_STATIC
import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.PRIVATE_OR_HIGHER
import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.PROTECTED_OR_HIGHER
import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.STATIC
import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Mismatch.MissingClass
import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Mismatch.MissingField
import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Mismatch.MissingFlag
import static io.opentelemetry.javaagent.tooling.muzzle.Reference.Mismatch.MissingMethod
import static muzzle.TestClasses.MethodBodyAdvice

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.ClasspathUtils
import io.opentelemetry.instrumentation.TestHelperClasses
import io.opentelemetry.javaagent.tooling.muzzle.Reference
import io.opentelemetry.javaagent.tooling.muzzle.Reference.Source
import io.opentelemetry.javaagent.tooling.muzzle.ReferenceCreator
import io.opentelemetry.javaagent.tooling.muzzle.ReferenceMatcher
import net.bytebuddy.jar.asm.Type
import spock.lang.Shared

class ReferenceMatcherTest extends AgentTestRunner {

  @Shared
  ClassLoader safeClasspath = new URLClassLoader([ClasspathUtils.createJarWithClasses(MethodBodyAdvice.A,
    MethodBodyAdvice.B,
    MethodBodyAdvice.SomeInterface,
    MethodBodyAdvice.SomeImplementation)] as URL[],
    (ClassLoader) null)

  @Shared
  ClassLoader unsafeClasspath = new URLClassLoader([ClasspathUtils.createJarWithClasses(MethodBodyAdvice.A,
    MethodBodyAdvice.SomeInterface,
    MethodBodyAdvice.SomeImplementation)] as URL[],
    (ClassLoader) null)

  def "match safe classpaths"() {
    setup:
    Reference[] refs = ReferenceCreator.createReferencesFrom(MethodBodyAdvice.name, this.class.classLoader)
      .values()
      .toArray(new Reference[0])
    def refMatcher = new ReferenceMatcher(refs)

    expect:
    getMismatchClassSet(refMatcher.getMismatchedReferenceSources(safeClasspath)).empty
    getMismatchClassSet(refMatcher.getMismatchedReferenceSources(unsafeClasspath)) == [MissingClass] as Set
  }

  def "matching does not hold a strong reference to classloaders"() {
    expect:
    MuzzleWeakReferenceTest.classLoaderRefIsGarbageCollected()
  }

  private static class CountingClassLoader extends URLClassLoader {
    int count = 0

    CountingClassLoader(URL[] urls, ClassLoader parent) {
      super(urls, (ClassLoader) parent)
    }

    @Override
    URL getResource(String name) {
      count++
      return super.getResource(name)
    }
  }

  def "muzzle type pool caches"() {
    setup:
    def cl = new CountingClassLoader(
      [ClasspathUtils.createJarWithClasses(MethodBodyAdvice.A,
        MethodBodyAdvice.B,
        MethodBodyAdvice.SomeInterface,
        MethodBodyAdvice.SomeImplementation)] as URL[],
      (ClassLoader) null)
    Reference[] refs = ReferenceCreator.createReferencesFrom(MethodBodyAdvice.name, this.class.classLoader)
      .values()
      .toArray(new Reference[0])
    def refMatcher1 = new ReferenceMatcher(refs)
    def refMatcher2 = new ReferenceMatcher(refs)
    assert getMismatchClassSet(refMatcher1.getMismatchedReferenceSources(cl)).empty
    int countAfterFirstMatch = cl.count
    // the second matcher should be able to used cached type descriptions from the first
    assert getMismatchClassSet(refMatcher2.getMismatchedReferenceSources(cl)).empty

    expect:
    cl.count == countAfterFirstMatch
  }

  def "matching ref #referenceName #referenceFlags against #classToCheck produces #expectedMismatches"() {
    setup:
    def ref = new Reference.Builder(referenceName)
      .withFlags(referenceFlags)
      .build()

    when:
    def mismatches = new ReferenceMatcher(ref).getMismatchedReferenceSources(this.class.classLoader)

    then:
    getMismatchClassSet(mismatches) == expectedMismatches as Set

    where:
    referenceName           | referenceFlags  | classToCheck       | expectedMismatches
    MethodBodyAdvice.B.name | [NON_INTERFACE] | MethodBodyAdvice.B | []
    MethodBodyAdvice.B.name | [INTERFACE]     | MethodBodyAdvice.B | [MissingFlag]
  }

  def "method match #methodTestDesc"() {
    setup:
    def methodType = Type.getMethodType(methodDesc)
    def reference = new Reference.Builder(classToCheck.name)
      .withMethod(new Source[0], methodFlags as Reference.Flag[], methodName, methodType.returnType, methodType.argumentTypes)
      .build()

    when:
    def mismatches = new ReferenceMatcher(reference)
      .getMismatchedReferenceSources(this.class.classLoader)

    then:
    getMismatchClassSet(mismatches) == expectedMismatches as Set

    where:
    methodName      | methodDesc                               | methodFlags           | classToCheck                   | expectedMismatches | methodTestDesc
    "aMethod"       | "(Ljava/lang/String;)Ljava/lang/String;" | []                    | MethodBodyAdvice.B             | []                 | "match method declared in class"
    "hashCode"      | "()I"                                    | []                    | MethodBodyAdvice.B             | []                 | "match method declared in superclass"
    "someMethod"    | "()V"                                    | []                    | MethodBodyAdvice.SomeInterface | []                 | "match method declared in interface"
    "privateStuff"  | "()V"                                    | [PRIVATE_OR_HIGHER]   | MethodBodyAdvice.B             | []                 | "match private method"
    "privateStuff"  | "()V"                                    | [PROTECTED_OR_HIGHER] | MethodBodyAdvice.B2            | [MissingFlag]      | "fail match private in supertype"
    "aStaticMethod" | "()V"                                    | [NON_STATIC]          | MethodBodyAdvice.B             | [MissingFlag]      | "static method mismatch"
    "missingMethod" | "()V"                                    | []                    | MethodBodyAdvice.B             | [MissingMethod]    | "missing method mismatch"
  }

  def "field match #fieldTestDesc"() {
    setup:
    def reference = new Reference.Builder(classToCheck.name)
      .withField(new Source[0], fieldFlags as Reference.Flag[], fieldName, Type.getType(fieldType))
      .build()

    when:
    def mismatches = new ReferenceMatcher(reference)
      .getMismatchedReferenceSources(this.class.classLoader)

    then:
    getMismatchClassSet(mismatches) == expectedMismatches as Set

    where:
    fieldName        | fieldType                                        | fieldFlags                    | classToCheck        | expectedMismatches | fieldTestDesc
    "missingField"   | "Ljava/lang/String;"                             | []                            | MethodBodyAdvice.A  | [MissingField]     | "mismatch missing field"
    "privateField"   | "Ljava/lang/String;"                             | []                            | MethodBodyAdvice.A  | [MissingField]     | "mismatch field type signature"
    "privateField"   | "Ljava/lang/Object;"                             | [PRIVATE_OR_HIGHER]           | MethodBodyAdvice.A  | []                 | "match private field"
    "privateField"   | "Ljava/lang/Object;"                             | [PROTECTED_OR_HIGHER]         | MethodBodyAdvice.A2 | [MissingFlag]      | "mismatch private field in supertype"
    "protectedField" | "Ljava/lang/Object;"                             | [STATIC]                      | MethodBodyAdvice.A  | [MissingFlag]      | "mismatch static field"
    "staticB"        | Type.getType(MethodBodyAdvice.B).getDescriptor() | [STATIC, PROTECTED_OR_HIGHER] | MethodBodyAdvice.A  | []                 | "match static field"
  }

  def "should ignore helper classes from third-party packages"() {
    given:
    def emptyClassLoader = new URLClassLoader(new URL[0], (ClassLoader) null)
    def reference = new Reference.Builder("com.google.common.base.Strings")
      .build()

    when:
    def mismatches = new ReferenceMatcher([reference.className] as String[], [reference] as Reference[])
      .getMismatchedReferenceSources(emptyClassLoader)

    then:
    mismatches.empty
  }

  def "should not check abstract helper classes"() {
    given:
    def reference = new Reference.Builder("io.opentelemetry.instrumentation.Helper")
      .withSuperName(TestHelperClasses.HelperSuperClass.name)
      .withFlag(ABSTRACT)
      .withMethod(new Source[0], [ABSTRACT] as Reference.Flag[], "unimplemented", Type.VOID_TYPE)
      .build()

    when:
    def mismatches = new ReferenceMatcher([reference.className] as String[], [reference] as Reference[])
      .getMismatchedReferenceSources(this.class.classLoader)

    then:
    mismatches.empty
  }

  def "should not check helper classes with no supertypes"() {
    given:
    def reference = new Reference.Builder("io.opentelemetry.instrumentation.Helper")
      .withSuperName(Object.name)
      .withMethod(new Source[0], [] as Reference.Flag[], "someMethod", Type.VOID_TYPE)
      .build()

    when:
    def mismatches = new ReferenceMatcher([reference.className] as String[], [reference] as Reference[])
      .getMismatchedReferenceSources(this.class.classLoader)

    then:
    mismatches.empty
  }

  def "should fail helper classes that does not implement all abstract methods"() {
    given:
    def reference = new Reference.Builder("io.opentelemetry.instrumentation.Helper")
      .withSuperName(TestHelperClasses.HelperSuperClass.name)
      .withMethod(new Source[0], [] as Reference.Flag[], "someMethod", Type.VOID_TYPE)
      .build()

    when:
    def mismatches = new ReferenceMatcher([reference.className] as String[], [reference] as Reference[])
      .getMismatchedReferenceSources(this.class.classLoader)

    then:
    getMismatchClassSet(mismatches) == [MissingMethod] as Set
  }

  def "should fail helper classes that does not implement all abstract methods - even if emtpy abstract class reference exists"() {
    given:
    def emptySuperClassRef = new Reference.Builder(TestHelperClasses.HelperSuperClass.name)
      .build()
    def reference = new Reference.Builder("io.opentelemetry.instrumentation.Helper")
      .withSuperName(TestHelperClasses.HelperSuperClass.name)
      .withMethod(new Source[0], [] as Reference.Flag[], "someMethod", Type.VOID_TYPE)
      .build()

    when:
    def mismatches = new ReferenceMatcher([reference.className] as String[], [reference, emptySuperClassRef] as Reference[])
      .getMismatchedReferenceSources(this.class.classLoader)

    then:
    getMismatchClassSet(mismatches) == [MissingMethod] as Set
  }

  def "should check whether interface methods are implemented in the super class"() {
    given:
    def baseHelper = new Reference.Builder("io.opentelemetry.instrumentation.BaseHelper")
      .withSuperName(Object.name)
      .withInterface(TestHelperClasses.HelperInterface.name)
      .withMethod(new Source[0], [] as Reference.Flag[], "foo", Type.VOID_TYPE)
      .build()
    // abstract HelperInterface#foo() is implemented by BaseHelper
    def helper = new Reference.Builder("io.opentelemetry.instrumentation.Helper")
      .withSuperName(baseHelper.className)
      .withInterface(TestHelperClasses.AnotherHelperInterface.name)
      .withMethod(new Source[0], [] as Reference.Flag[], "bar", Type.VOID_TYPE)
      .build()

    when:
    def mismatches = new ReferenceMatcher([helper.className, baseHelper] as String[], [helper, baseHelper] as Reference[])
      .getMismatchedReferenceSources(this.class.classLoader)

    then:
    mismatches.empty
  }

  private static Set<Class> getMismatchClassSet(List<Reference.Mismatch> mismatches) {
    Set<Class> mismatchClasses = new HashSet<>(mismatches.size())
    for (Reference.Mismatch mismatch : mismatches) {
      mismatchClasses.add(mismatch.class)
    }
    return mismatchClasses
  }
}
