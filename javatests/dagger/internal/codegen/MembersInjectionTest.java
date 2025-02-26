/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.internal.codegen;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.CompilerMode.FAST_INIT_MODE;
import static dagger.internal.codegen.Compilers.compilerWithOptions;
import static javax.tools.StandardLocation.CLASS_OUTPUT;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MembersInjectionTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public MembersInjectionTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void parentClass_noInjectedMembers() {
    JavaFileObject childFile = JavaFileObjects.forSourceLines("test.Child",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "public final class Child extends Parent {",
        "  @Inject Child() {}",
        "}");
    JavaFileObject parentFile = JavaFileObjects.forSourceLines("test.Parent",
        "package test;",
        "",
        "public abstract class Parent {}");

    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  Child child();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            GeneratedLines.generatedAnnotations(),
            "final class DaggerTestComponent implements TestComponent {",
            "  @Override",
            "  public Child child() {",
            "    return new Child();",
            "  }",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(childFile, parentFile, componentFile);

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void parentClass_injectedMembersInSupertype() {
    JavaFileObject childFile = JavaFileObjects.forSourceLines("test.Child",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "public final class Child extends Parent {",
        "  @Inject Child() {}",
        "}");
    JavaFileObject parentFile = JavaFileObjects.forSourceLines("test.Parent",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "public abstract class Parent {",
        "  @Inject Dep dep;",
        "}");
    JavaFileObject depFile = JavaFileObjects.forSourceLines("test.Dep",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class Dep {",
        "  @Inject Dep() {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  Child child();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            GeneratedLines.generatedImports(
                "import com.google.errorprone.annotations.CanIgnoreReturnValue;"),
            "",
            GeneratedLines.generatedAnnotations(),
            "final class DaggerTestComponent implements TestComponent {",
            "  @Override",
            "  public Child child() {",
            "    return injectChild(Child_Factory.newInstance());",
            "  }",
            "",
            "  @CanIgnoreReturnValue",
            "  private Child injectChild(Child instance) {",
            "    Parent_MembersInjector.injectDep(instance, new Dep());",
            "    return instance;",
            "  }",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(childFile, parentFile, depFile, componentFile);

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test public void fieldAndMethodGenerics() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.GenericClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class GenericClass<A, B> {",
        "  @Inject A a;",
        "",
        "  @Inject GenericClass() {}",
        "",
        " @Inject void register(B b) {}",
        "}");
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.GenericClass_MembersInjector",
            "package test;",
            "",
            GeneratedLines.generatedImports(
                "import dagger.MembersInjector;",
                "import dagger.internal.InjectedFieldSignature;",
                "import javax.inject.Provider;"),
            "",
            GeneratedLines.generatedAnnotations(),
            "public final class GenericClass_MembersInjector<A, B>",
            "    implements MembersInjector<GenericClass<A, B>> {",
            "  private final Provider<A> aProvider;",
            "  private final Provider<B> bProvider;",
            "",
            "  public GenericClass_MembersInjector(Provider<A> aProvider, Provider<B> bProvider) {",
            "    this.aProvider = aProvider;",
            "    this.bProvider = bProvider;",
            "  }",
            "",
            "  public static <A, B> MembersInjector<GenericClass<A, B>> create(",
            "      Provider<A> aProvider, Provider<B> bProvider) {",
            "    return new GenericClass_MembersInjector<A, B>(aProvider, bProvider);",
            "  }",
            "",
            "  @Override",
            "  public void injectMembers(GenericClass<A, B> instance) {",
            "    injectA(instance, aProvider.get());",
            "    injectRegister(instance, bProvider.get());",
            "  }",
            "",
            "  @InjectedFieldSignature(\"test.GenericClass.a\")",
            "  public static <A, B> void injectA(Object instance, A a) {",
            "    ((GenericClass<A, B>) instance).a = a;",
            "  }",
            "",
            "  public static <A, B> void injectRegister(Object instance, B b) {",
            "    ((GenericClass<A, B>) instance).register(b);",
            "  }",
            "}");
    assertAbout(javaSource())
        .that(file)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(expected);
  }

  @Test public void subclassedGenericMembersInjectors() {
    JavaFileObject a = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class A {",
        "  @Inject A() {}",
        "}");
    JavaFileObject a2 = JavaFileObjects.forSourceLines("test.A2",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class A2 {",
        "  @Inject A2() {}",
        "}");
    JavaFileObject parent = JavaFileObjects.forSourceLines("test.Parent",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class Parent<X, Y> {",
        "  @Inject X x;",
        "  @Inject Y y;",
        "  @Inject A2 a2;",
        "",
        "  @Inject Parent() {}",
        "}");
    JavaFileObject child = JavaFileObjects.forSourceLines("test.Child",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class Child<T> extends Parent<T, A> {",
        "  @Inject A a;",
        "  @Inject T t;",
        "",
        "  @Inject Child() {}",
        "}");
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.Child_MembersInjector",
            "package test;",
            "",
            GeneratedLines.generatedImports(
                "import dagger.MembersInjector;",
                "import dagger.internal.InjectedFieldSignature;",
                "import javax.inject.Provider;"),
            "",
            GeneratedLines.generatedAnnotations(),
            "public final class Child_MembersInjector<T>",
            "    implements MembersInjector<Child<T>> {",
            "  private final Provider<T> xProvider;",
            "  private final Provider<A> yProvider;",
            "  private final Provider<A2> a2Provider;",
            "  private final Provider<A> aProvider;",
            "  private final Provider<T> tProvider;",
            "",
            "  public Child_MembersInjector(",
            "      Provider<T> xProvider,",
            "      Provider<A> yProvider,",
            "      Provider<A2> a2Provider,",
            "      Provider<A> aProvider,",
            "      Provider<T> tProvider) {",
            "    this.xProvider = xProvider;",
            "    this.yProvider = yProvider;",
            "    this.a2Provider = a2Provider;",
            "    this.aProvider = aProvider;",
            "    this.tProvider = tProvider;",
            "  }",
            "",
            "  public static <T> MembersInjector<Child<T>> create(",
            "      Provider<T> xProvider,",
            "      Provider<A> yProvider,",
            "      Provider<A2> a2Provider,",
            "      Provider<A> aProvider,",
            "      Provider<T> tProvider) {",
            "    return new Child_MembersInjector<T>(xProvider, yProvider, a2Provider, aProvider,"
                + " tProvider);",
            "}",
            "",
            "  @Override",
            "  public void injectMembers(Child<T> instance) {",
            "    Parent_MembersInjector.injectX(instance, xProvider.get());",
            "    Parent_MembersInjector.injectY(instance, yProvider.get());",
            "    Parent_MembersInjector.injectA2(instance, a2Provider.get());",
            "    injectA(instance, aProvider.get());",
            "    injectT(instance, tProvider.get());",
            "  }",
            "",
            "  @InjectedFieldSignature(\"test.Child.a\")",
            "  public static <T> void injectA(Object instance, Object a) {",
            "    ((Child<T>) instance).a = (A) a;",
            "  }",
            "",
            "  @InjectedFieldSignature(\"test.Child.t\")",
            "  public static <T> void injectT(Object instance, T t) {",
            "    ((Child<T>) instance).t = t;",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(a, a2, parent, child))
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(expected);
  }

  @Test public void fieldInjection() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.FieldInjection",
        "package test;",
        "",
        "import dagger.Lazy;",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "",
        "class FieldInjection {",
        "  @Inject String string;",
        "  @Inject Lazy<String> lazyString;",
        "  @Inject Provider<String> stringProvider;",
        "}");
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.FieldInjection_MembersInjector",
            "package test;",
            "",
            GeneratedLines.generatedImports(
                "import dagger.Lazy;",
                "import dagger.MembersInjector;",
                "import dagger.internal.DoubleCheck;",
                "import dagger.internal.InjectedFieldSignature;",
                "import javax.inject.Provider;"),
            "",
            GeneratedLines.generatedAnnotations(),
            "public final class FieldInjection_MembersInjector",
            "    implements MembersInjector<FieldInjection> {",
            "  private final Provider<String> stringProvider;",
            "  private final Provider<String> stringProvider2;",
            "  private final Provider<String> stringProvider3;",
            "",
            "  public FieldInjection_MembersInjector(Provider<String> stringProvider,",
            "      Provider<String> stringProvider2, Provider<String> stringProvider3) {",
            "    this.stringProvider = stringProvider;",
            "    this.stringProvider2 = stringProvider2;",
            "    this.stringProvider3 = stringProvider3;",
            "  }",
            "",
            "  public static MembersInjector<FieldInjection> create(",
            "      Provider<String> stringProvider,",
            "      Provider<String> stringProvider2,",
            "      Provider<String> stringProvider3) {",
            "    return new FieldInjection_MembersInjector(",
            "        stringProvider, stringProvider2, stringProvider3);",
            "  }",
            "",
            "  @Override",
            "  public void injectMembers(FieldInjection instance) {",
            "    injectString(instance, stringProvider.get());",
            "    injectLazyString(instance, DoubleCheck.lazy(stringProvider2));",
            "    injectStringProvider(instance, stringProvider3);",
            "  }",
            "",
            "  @InjectedFieldSignature(\"test.FieldInjection.string\")",
            "  public static void injectString(Object instance, String string) {",
            "    ((FieldInjection) instance).string = string;",
            "  }",
            "",
            "  @InjectedFieldSignature(\"test.FieldInjection.lazyString\")",
            "  public static void injectLazyString(Object instance, Lazy<String> lazyString) {",
            "    ((FieldInjection) instance).lazyString = lazyString;",
            "  }",
            "",
            "  @InjectedFieldSignature(\"test.FieldInjection.stringProvider\")",
            "  public static void injectStringProvider(",
            "      Object instance, Provider<String> stringProvider) {",
            "    ((FieldInjection) instance).stringProvider = stringProvider;",
            "  }",
            "}");
    assertAbout(javaSource())
        .that(file)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(expected);
  }

  @Test
  public void fieldInjectionWithQualifier() {
    JavaFileObject file =
        JavaFileObjects.forSourceLines(
            "test.FieldInjectionWithQualifier",
            "package test;",
            "",
            "import dagger.Lazy;",
            "import javax.inject.Inject;",
            "import javax.inject.Named;",
            "import javax.inject.Provider;",
            "",
            "class FieldInjectionWithQualifier {",
            "  @Inject @Named(\"A\") String a;",
            "  @Inject @Named(\"B\") String b;",
            "}");
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.FieldInjectionWithQualifier_MembersInjector",
            "package test;",
            "",
            GeneratedLines.generatedImports(
                "import dagger.MembersInjector;",
                "import dagger.internal.InjectedFieldSignature;",
                "import javax.inject.Named;",
                "import javax.inject.Provider;"),
            "",
            GeneratedLines.generatedAnnotations(),
            "public final class FieldInjectionWithQualifier_MembersInjector",
            "    implements MembersInjector<FieldInjectionWithQualifier> {",
            "  private final Provider<String> aProvider;",
            "  private final Provider<String> bProvider;",
            "",
            "  public FieldInjectionWithQualifier_MembersInjector(Provider<String> aProvider,",
            "      Provider<String> bProvider) {",
            "    this.aProvider = aProvider;",
            "    this.bProvider = bProvider;",
            "  }",
            "",
            "  public static MembersInjector<FieldInjectionWithQualifier> create(",
            "    Provider<String> aProvider, Provider<String> bProvider) {",
            "    return new FieldInjectionWithQualifier_MembersInjector(aProvider, bProvider);",
            "  }",
            "",
            "@Override",
            "  public void injectMembers(FieldInjectionWithQualifier instance) {",
            "    injectA(instance, aProvider.get());",
            "    injectB(instance, bProvider.get());",
            "}",
            "",
            "  @InjectedFieldSignature(\"test.FieldInjectionWithQualifier.a\")",
            "  @Named(\"A\")",
            "  public static void injectA(Object instance, String a) {",
            "    ((FieldInjectionWithQualifier) instance).a = a;",
            "  }",
            "",
            "  @InjectedFieldSignature(\"test.FieldInjectionWithQualifier.b\")",
            "  @Named(\"B\")",
            "  public static void injectB(Object instance, String b) {",
            "    ((FieldInjectionWithQualifier) instance).b = b;",
            "  }",
            "}");
    assertAbout(javaSource())
        .that(file)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(expected);
  }

  @Test public void methodInjection() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.MethodInjection",
        "package test;",
        "",
        "import dagger.Lazy;",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "",
        "class MethodInjection {",
        "  @Inject void noArgs() {}",
        "  @Inject void oneArg(String string) {}",
        "  @Inject void manyArgs(",
        "      String string, Lazy<String> lazyString, Provider<String> stringProvider) {}",
        "}");
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.MethodInjection_MembersInjector",
            "package test;",
            "",
            GeneratedLines.generatedImports(
                "import dagger.Lazy;",
                "import dagger.MembersInjector;",
                "import dagger.internal.DoubleCheck;",
                "import javax.inject.Provider;"),
            "",
            GeneratedLines.generatedAnnotations(),
            "public final class MethodInjection_MembersInjector",
            "     implements MembersInjector<MethodInjection> {",
            "  private final Provider<String> stringProvider;",
            "  private final Provider<String> stringProvider2;",
            "  private final Provider<String> stringProvider3;",
            "  private final Provider<String> stringProvider4;",
            "",
            "  public MethodInjection_MembersInjector(",
            "      Provider<String> stringProvider,",
            "      Provider<String> stringProvider2,",
            "      Provider<String> stringProvider3,",
            "      Provider<String> stringProvider4) {",
            "    this.stringProvider = stringProvider;",
            "    this.stringProvider2 = stringProvider2;",
            "    this.stringProvider3 = stringProvider3;",
            "    this.stringProvider4 = stringProvider4;",
            "  }",
            "",
            "  public static MembersInjector<MethodInjection> create(",
            "      Provider<String> stringProvider,",
            "      Provider<String> stringProvider2,",
            "      Provider<String> stringProvider3,",
            "      Provider<String> stringProvider4) {",
            "    return new MethodInjection_MembersInjector(",
            "        stringProvider, stringProvider2, stringProvider3, stringProvider4);}",
            "",
            "  @Override",
            "  public void injectMembers(MethodInjection instance) {",
            "    injectNoArgs(instance);",
            "    injectOneArg(instance, stringProvider.get());",
            "    injectManyArgs(",
            "        instance,",
            "        stringProvider2.get(),",
            "        DoubleCheck.lazy(stringProvider3),",
            "        stringProvider4);",
            "  }",
            "",
            "  public static void injectNoArgs(Object instance) {",
            "    ((MethodInjection) instance).noArgs();",
            "  }",
            "",
            "  public static void injectOneArg(Object instance, String string) {",
            "    ((MethodInjection) instance).oneArg(string);",
            "  }",
            "",
            "  public static void injectManyArgs(",
            "      Object instance,",
            "      String string,",
            "      Lazy<String> lazyString,",
            "      Provider<String> stringProvider) {",
            "    ((MethodInjection) instance).manyArgs(string, lazyString, stringProvider);",
            "  }",
            "}");
    assertAbout(javaSource())
        .that(file)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(expected);
  }

  @Test
  public void mixedMemberInjection() {
    JavaFileObject file = JavaFileObjects.forSourceLines(
        "test.MixedMemberInjection",
        "package test;",
        "",
        "import dagger.Lazy;",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "",
        "class MixedMemberInjection {",
        "  @Inject String string;",
        "  @Inject void setString(String s) {}",
        "  @Inject Object object;",
        "  @Inject void setObject(Object o) {}",
        "}");
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.MixedMemberInjection_MembersInjector",
            "package test;",
            "",
            GeneratedLines.generatedImports(
                "import dagger.MembersInjector;",
                "import dagger.internal.InjectedFieldSignature;",
                "import javax.inject.Provider;"),
            "",
            GeneratedLines.generatedAnnotations(),
            "public final class MixedMemberInjection_MembersInjector",
            "    implements MembersInjector<MixedMemberInjection> {",
            "  private final Provider<String> stringProvider;",
            "  private final Provider<Object> objectProvider;",
            "  private final Provider<String> sProvider;",
            "  private final Provider<Object> oProvider;",
            "",
            "  public MixedMemberInjection_MembersInjector(",
            "      Provider<String> stringProvider,",
            "      Provider<Object> objectProvider,",
            "      Provider<String> sProvider,",
            "      Provider<Object> oProvider) {",
            "    this.stringProvider = stringProvider;",
            "    this.objectProvider = objectProvider;",
            "    this.sProvider = sProvider;",
            "    this.oProvider = oProvider;",
            "  }",
            "",
            "  public static MembersInjector<MixedMemberInjection> create(",
            "      Provider<String> stringProvider,",
            "      Provider<Object> objectProvider,",
            "      Provider<String> sProvider,",
            "      Provider<Object> oProvider) {",
            "    return new MixedMemberInjection_MembersInjector(",
            "        stringProvider, objectProvider, sProvider, oProvider);}",
            "",
            "  @Override",
            "  public void injectMembers(MixedMemberInjection instance) {",
            "    injectString(instance, stringProvider.get());",
            "    injectObject(instance, objectProvider.get());",
            "    injectSetString(instance, sProvider.get());",
            "    injectSetObject(instance, oProvider.get());",
            "  }",
            "",
            "  @InjectedFieldSignature(\"test.MixedMemberInjection.string\")",
            "  public static void injectString(Object instance, String string) {",
            "    ((MixedMemberInjection) instance).string = string;",
            "  }",
            "",
            "  @InjectedFieldSignature(\"test.MixedMemberInjection.object\")",
            "  public static void injectObject(Object instance, Object object) {",
            "    ((MixedMemberInjection) instance).object = object;",
            "  }",
            "",
            "  public static void injectSetString(Object instance, String s) {",
            "    ((MixedMemberInjection) instance).setString(s);",
            "  }",
            "",
            "  public static void injectSetObject(Object instance, Object o) {",
            "    ((MixedMemberInjection) instance).setObject(o);",
            "  }",
            "}");
    assertAbout(javaSource())
        .that(file)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(expected);
  }

  @Test public void injectConstructorAndMembersInjection() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.AllInjections",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class AllInjections {",
        "  @Inject String s;",
        "  @Inject AllInjections(String s) {}",
        "  @Inject void s(String s) {}",
        "}");
    JavaFileObject expectedMembersInjector =
        JavaFileObjects.forSourceLines(
            "test.AllInjections_MembersInjector",
            "package test;",
            "",
            GeneratedLines.generatedImports(
                "import dagger.MembersInjector;",
                "import dagger.internal.InjectedFieldSignature;",
                "import javax.inject.Provider;"),
            "",
            GeneratedLines.generatedAnnotations(),
            "public final class AllInjections_MembersInjector ",
            "    implements MembersInjector<AllInjections> {",
            "  private final Provider<String> sProvider;",
            "  private final Provider<String> sProvider2;",
            "",
            "  public AllInjections_MembersInjector(",
            "      Provider<String> sProvider, Provider<String> sProvider2) {",
            "    this.sProvider = sProvider;",
            "    this.sProvider2 = sProvider2;",
            "  }",
            "",
            "  public static MembersInjector<AllInjections> create(",
            "      Provider<String> sProvider, Provider<String> sProvider2) {",
            "    return new AllInjections_MembersInjector(sProvider, sProvider2);}",
            "",
            "  @Override",
            "  public void injectMembers(AllInjections instance) {",
            "    injectS(instance, sProvider.get());",
            "    injectS2(instance, sProvider2.get());",
            "  }",
            "",
            // TODO(b/64477506): now that these all take "object", it would be nice to rename
            // "instance"
            // to the type name
            "  @InjectedFieldSignature(\"test.AllInjections.s\")",
            "  public static void injectS(Object instance, String s) {",
            "    ((AllInjections) instance).s = s;",
            "  }",
            "",
            "  public static void injectS2(Object instance, String s) {",
            "    ((AllInjections) instance).s(s);",
            "  }",
            "",
            "}");
    assertAbout(javaSource())
        .that(file)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(expectedMembersInjector);
  }

  @Test public void supertypeMembersInjection() {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "class A {}");
    JavaFileObject bFile = JavaFileObjects.forSourceLines("test.B",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class B extends A {",
        "  @Inject String s;",
        "}");
    JavaFileObject expectedMembersInjector =
        JavaFileObjects.forSourceLines(
            "test.B_MembersInjector",
            "package test;",
            "",
            GeneratedLines.generatedImports(
                "import dagger.MembersInjector;",
                "import dagger.internal.InjectedFieldSignature;",
                "import javax.inject.Provider;"),
            "",
            GeneratedLines.generatedAnnotations(),
            "public final class B_MembersInjector implements MembersInjector<B> {",
            "  private final Provider<String> sProvider;",
            "",
            "  public B_MembersInjector(Provider<String> sProvider) {",
            "    this.sProvider = sProvider;",
            "  }",
            "",
            "  public static MembersInjector<B> create(Provider<String> sProvider) {",
            "      return new B_MembersInjector(sProvider);",
            "  }",
            "",
            "  @Override",
            "  public void injectMembers(B instance) {",
            "    injectS(instance, sProvider.get());",
            "  }",
            "",
            "  @InjectedFieldSignature(\"test.B.s\")",
            "  public static void injectS(Object instance, String s) {",
            "    ((B) instance).s = s;",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(aFile, bFile))
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(expectedMembersInjector);
  }

  @Test
  public void simpleComponentWithNesting() {
    JavaFileObject nestedTypesFile = JavaFileObjects.forSourceLines(
          "test.OuterType",
          "package test;",
          "",
          "import dagger.Component;",
          "import javax.inject.Inject;",
          "",
          "final class OuterType {",
          "  static class A {",
          "    @Inject A() {}",
          "  }",
          "  static class B {",
          "    @Inject A a;",
          "  }",
          "  @Component interface SimpleComponent {",
          "    A a();",
          "    void inject(B b);",
          "  }",
          "}");
    JavaFileObject bMembersInjector =
        JavaFileObjects.forSourceLines(
            "test.OuterType_B_MembersInjector",
            "package test;",
            "",
            GeneratedLines.generatedImports(
                "import dagger.MembersInjector;",
                "import dagger.internal.InjectedFieldSignature;",
                "import javax.inject.Provider;"),
            "",
            GeneratedLines.generatedAnnotations(),
            "public final class OuterType_B_MembersInjector",
            "    implements MembersInjector<OuterType.B> {",
            "  private final Provider<OuterType.A> aProvider;",
            "",
            "  public OuterType_B_MembersInjector(Provider<OuterType.A> aProvider) {",
            "    this.aProvider = aProvider;",
            "  }",
            "",
            "  public static MembersInjector<OuterType.B> create(",
            "    Provider<OuterType.A> aProvider) {",
            "    return new OuterType_B_MembersInjector(aProvider);",
            "  }",
            "",
            "  @Override",
            "  public void injectMembers(OuterType.B instance) {",
            "    injectA(instance, aProvider.get());",
            "  }",
            "",
            "  @InjectedFieldSignature(\"test.OuterType.B.a\")",
            "  public static void injectA(Object instance, Object a) {",
            "    ((OuterType.B) instance).a = (OuterType.A) a;",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(nestedTypesFile))
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(bMembersInjector);
  }

  @Test
  public void componentWithNestingAndGeneratedType() {
    JavaFileObject nestedTypesFile =
        JavaFileObjects.forSourceLines(
            "test.OuterType",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Inject;",
            "",
            "final class OuterType {",
            "  @Inject GeneratedType generated;",
            "  static class A {",
            "    @Inject A() {}",
            "  }",
            "  static class B {",
            "    @Inject A a;",
            "  }",
            "  @Component interface SimpleComponent {",
            "    A a();",
            "    void inject(B b);",
            "  }",
            "}");
    JavaFileObject bMembersInjector =
        JavaFileObjects.forSourceLines(
            "test.OuterType_B_MembersInjector",
            "package test;",
            "",
            GeneratedLines.generatedImports(
                "import dagger.MembersInjector;",
                "import dagger.internal.InjectedFieldSignature;",
                "import javax.inject.Provider;"),
            "",
            GeneratedLines.generatedAnnotations(),
            "public final class OuterType_B_MembersInjector",
            "    implements MembersInjector<OuterType.B> {",
            "  private final Provider<OuterType.A> aProvider;",
            "",
            "  public OuterType_B_MembersInjector(Provider<OuterType.A> aProvider) {",
            "    this.aProvider = aProvider;",
            "  }",
            "",
            "  public static MembersInjector<OuterType.B> create(",
            "      Provider<OuterType.A> aProvider) {",
            "    return new OuterType_B_MembersInjector(aProvider);",
            "  }",
            "",
            "  @Override",
            "  public void injectMembers(OuterType.B instance) {",
            "    injectA(instance, aProvider.get());",
            "  }",
            "",
            "  @InjectedFieldSignature(\"test.OuterType.B.a\")",
            "  public static void injectA(Object instance, Object a) {",
            "    ((OuterType.B) instance).a = (OuterType.A) a;",
            "  }",
            "}");
    assertAbout(javaSource())
        .that(nestedTypesFile)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(
            new ComponentProcessor(),
            new AbstractProcessor() {
              private boolean done;

              @Override
              public Set<String> getSupportedAnnotationTypes() {
                return ImmutableSet.of("*");
              }

              @Override
              public boolean process(
                  Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                if (!done) {
                  done = true;
                  try (Writer writer =
                      processingEnv
                          .getFiler()
                          .createSourceFile("test.GeneratedType")
                          .openWriter()) {
                    writer.write(
                        Joiner.on('\n')
                            .join(
                                "package test;",
                                "",
                                "import javax.inject.Inject;",
                                "",
                                "class GeneratedType {",
                                "  @Inject GeneratedType() {}",
                                "}"));
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                }
                return false;
              }
            })
        .compilesWithoutError()
        .and()
        .generatesSources(bMembersInjector);
  }

  @Test
  public void lowerCaseNamedMembersInjector_forLowerCaseType() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class foo {",
            "  @Inject String string;",
            "}");
    JavaFileObject fooModule =
        JavaFileObjects.forSourceLines(
            "test.fooModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class fooModule {",
            "  @Provides String string() { return \"foo\"; }",
            "}");
    JavaFileObject fooComponent =
        JavaFileObjects.forSourceLines(
            "test.fooComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = fooModule.class)",
            "interface fooComponent {",
            "  void inject(foo target);",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(foo, fooModule, fooComponent);
    assertThat(compilation).succeeded();
    assertThat(compilation).generatedFile(CLASS_OUTPUT, "test", "foo_MembersInjector.class");
  }

  @Test
  public void fieldInjectionForShadowedMember() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar() {}",
            "}");
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Parent { ",
            "  @Inject Foo object;",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Child extends Parent { ",
            "  @Inject Bar object;",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface C { ",
            "  void inject(Child child);",
            "}");

    JavaFileObject expectedMembersInjector =
        JavaFileObjects.forSourceLines(
            "test.Child_MembersInjector",
            "package test;",
            "",
            GeneratedLines.generatedImports(
                "import dagger.MembersInjector;",
                "import dagger.internal.InjectedFieldSignature;",
                "import javax.inject.Provider;"),
            "",
            GeneratedLines.generatedAnnotations(),
            "public final class Child_MembersInjector implements MembersInjector<Child> {",
            "  private final Provider<Foo> objectProvider;",
            "  private final Provider<Bar> objectProvider2;",
            "",
            "  public Child_MembersInjector(",
            "        Provider<Foo> objectProvider, Provider<Bar> objectProvider2) {",
            "    this.objectProvider = objectProvider;",
            "    this.objectProvider2 = objectProvider2;",
            "  }",
            "",
            "  public static MembersInjector<Child> create(",
            "      Provider<Foo> objectProvider, Provider<Bar> objectProvider2) {",
            "    return new Child_MembersInjector(objectProvider, objectProvider2);",
            "  }",
            "",
            "  @Override",
            "  public void injectMembers(Child instance) {",
            "    Parent_MembersInjector.injectObject(instance, objectProvider.get());",
            "    injectObject(instance, objectProvider2.get());",
            "  }",
            "",
            "  @InjectedFieldSignature(\"test.Child.object\")",
            "  public static void injectObject(Object instance, Object object) {",
            "    ((Child) instance).object = (Bar) object;",
            "  }",
            "}");

    assertAbout(javaSources())
        .that(ImmutableList.of(foo, bar, parent, child, component))
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(expectedMembersInjector);
  }

  @Test public void privateNestedClassError() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.OuterClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class OuterClass {",
        "  private static final class InnerClass {",
        "    @Inject int field;",
        "  }",
        "}");
    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support injection into private classes")
        .inFile(file)
        .onLine(6);
  }

  @Test public void privateNestedClassWarning() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.OuterClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class OuterClass {",
        "  private static final class InnerClass {",
        "    @Inject int field;",
        "  }",
        "}");
    Compilation compilation =
        compilerWithOptions(
                compilerMode.javacopts().append("-Adagger.privateMemberValidation=WARNING"))
            .compile(file);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining("Dagger does not support injection into private classes")
        .inFile(file)
        .onLine(6);
  }

  @Test public void privateSuperclassIsOkIfNotInjectedInto() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.OuterClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class OuterClass {",
        "  private static class BaseClass {}",
        "",
        "  static final class DerivedClass extends BaseClass {",
        "    @Inject int field;",
        "  }",
        "}");
    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(file);
    assertThat(compilation).succeeded();
  }

  @Test
  public void rawFrameworkTypeField() {
    JavaFileObject file =
        JavaFileObjects.forSourceLines(
            "test.RawFrameworkTypes",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "class RawProviderField {",
            "  @Inject Provider fieldWithRawProvider;",
            "}",
            "",
            "@Component",
            "interface C {",
            "  void inject(RawProviderField rawProviderField);",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Provider cannot be provided")
        .inFile(file)
        .onLineContaining("interface C");
  }

  @Test
  public void throwExceptionInjectedMethod() {
    JavaFileObject file =
        JavaFileObjects.forSourceLines(
            "test.",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Inject;",
            "class SomeClass {",
            "@Inject void inject() throws Exception {}",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Methods with @Inject may not throw checked exceptions. "
          + "Please wrap your exceptions in a RuntimeException instead.")
        .inFile(file)
        .onLineContaining("throws Exception");
  }

  @Test
  public void rawFrameworkTypeParameter() {
    JavaFileObject file =
        JavaFileObjects.forSourceLines(
            "test.RawFrameworkTypes",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "class RawProviderParameter {",
            "  @Inject void methodInjection(Provider rawProviderParameter) {}",
            "}",
            "",
            "@Component",
            "interface C {",
            "  void inject(RawProviderParameter rawProviderParameter);",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Provider cannot be provided")
        .inFile(file)
        .onLineContaining("interface C");
  }

  @Test
  public void injectsPrimitive() {
    JavaFileObject injectedType =
        JavaFileObjects.forSourceLines(
            "test.InjectedType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class InjectedType {",
            "  @Inject InjectedType() {}",
            "",
            "  @Inject int primitiveInt;",
            "  @Inject Integer boxedInt;",
            "}");
    JavaFileObject membersInjector =
        JavaFileObjects.forSourceLines(
            "test.InjectedType_MembersInjector",
            "package test;",
            "",
            GeneratedLines.generatedImports(
                "import dagger.MembersInjector;",
                "import dagger.internal.InjectedFieldSignature;",
                "import javax.inject.Provider;"),
            "",
            GeneratedLines.generatedAnnotations(),
            "public final class InjectedType_MembersInjector ",
            "    implements MembersInjector<InjectedType> {",
            "  private final Provider<Integer> primitiveIntProvider;",
            "  private final Provider<Integer> boxedIntProvider;",
            "",
            "  public InjectedType_MembersInjector(",
            "      Provider<Integer> primitiveIntProvider, Provider<Integer> boxedIntProvider) {",
            "    this.primitiveIntProvider = primitiveIntProvider;",
            "    this.boxedIntProvider = boxedIntProvider;",
            "  }",
            "",
            "  public static MembersInjector<InjectedType> create(",
            "      Provider<Integer> primitiveIntProvider, Provider<Integer> boxedIntProvider) {",
            "    return new InjectedType_MembersInjector(primitiveIntProvider, boxedIntProvider);}",
            "",
            "  @Override",
            "  public void injectMembers(InjectedType instance) {",
            "    injectPrimitiveInt(instance, primitiveIntProvider.get());",
            "    injectBoxedInt(instance, boxedIntProvider.get());",
            "  }",
            "",
            "  @InjectedFieldSignature(\"test.InjectedType.primitiveInt\")",
            "  public static void injectPrimitiveInt(Object instance, int primitiveInt) {",
            "    ((InjectedType) instance).primitiveInt = primitiveInt;",
            "  }",
            "",
            "  @InjectedFieldSignature(\"test.InjectedType.boxedInt\")",
            "  public static void injectBoxedInt(Object instance, Integer boxedInt) {",
            "    ((InjectedType) instance).boxedInt = boxedInt;",
            "  }",
            "}");
    JavaFileObject factory =
        JavaFileObjects.forSourceLines(
            "test.InjectedType_Factory",
            "package test;",
            "",
            GeneratedLines.generatedImports(
                "import dagger.internal.Factory;",
                "import javax.inject.Provider;"),
            "",
            GeneratedLines.generatedAnnotations(),
            "public final class InjectedType_Factory implements Factory<InjectedType> {",
            "  private final Provider<Integer> primitiveIntProvider;",
            "",
            "  private final Provider<Integer> boxedIntProvider;",
            "",
            "  public InjectedType_Factory(",
            "      Provider<Integer> primitiveIntProvider, Provider<Integer> boxedIntProvider) {",
            "    this.primitiveIntProvider = primitiveIntProvider;",
            "    this.boxedIntProvider = boxedIntProvider;",
            "  }",
            "",
            "  @Override",
            "  public InjectedType get() {",
            "    InjectedType instance = newInstance();",
            "    InjectedType_MembersInjector.injectPrimitiveInt(",
            "        instance, primitiveIntProvider.get());",
            "    InjectedType_MembersInjector.injectBoxedInt(instance, boxedIntProvider.get());",
            "    return instance;",
            "  }",
            "",
            "  public static InjectedType_Factory create(",
            "      Provider<Integer> primitiveIntProvider, Provider<Integer> boxedIntProvider) {",
            "    return new InjectedType_Factory(primitiveIntProvider, boxedIntProvider);",
            "  }",
            "",
            "  public static InjectedType newInstance() {",
            "    return new InjectedType();",
            "  }",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(injectedType);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.InjectedType_MembersInjector")
        .hasSourceEquivalentTo(membersInjector);
    assertThat(compilation)
        .generatedSourceFile("test.InjectedType_Factory")
        .hasSourceEquivalentTo(factory);
  }

  @Test
  public void accessibility() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "other.Foo",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");
    JavaFileObject inaccessible =
        JavaFileObjects.forSourceLines(
            "other.Inaccessible",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Inaccessible {",
            "  @Inject Inaccessible() {}",
            "  @Inject Foo foo;",
            "  @Inject void method(Foo foo) {}",
            "}");
    JavaFileObject usesInaccessible =
        JavaFileObjects.forSourceLines(
            "other.UsesInaccessible",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "public class UsesInaccessible {",
            "  @Inject UsesInaccessible(Inaccessible inaccessible) {}",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import other.UsesInaccessible;",
            "",
            "@Component",
            "interface TestComponent {",
            "  UsesInaccessible usesInaccessible();",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(foo, inaccessible, usesInaccessible, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("other.Inaccessible_MembersInjector")
        .hasSourceEquivalentTo(
            JavaFileObjects.forSourceLines(
                "other.Inaccessible_MembersInjector",
                "package other;",
                "",
                GeneratedLines.generatedImports(
                    "import dagger.MembersInjector;",
                    "import dagger.internal.InjectedFieldSignature;",
                    "import javax.inject.Provider;"),
                "",
                GeneratedLines.generatedAnnotations(),
                "public final class Inaccessible_MembersInjector",
                "    implements MembersInjector<Inaccessible> {",
                "  private final Provider<Foo> fooProvider;",
                "  private final Provider<Foo> fooProvider2;",
                "",
                "  public Inaccessible_MembersInjector(",
                "      Provider<Foo> fooProvider, Provider<Foo> fooProvider2) {",
                "    this.fooProvider = fooProvider;",
                "    this.fooProvider2 = fooProvider2;",
                "  }",
                "",
                "  public static MembersInjector<Inaccessible> create(",
                "      Provider<Foo> fooProvider, Provider<Foo> fooProvider2) {",
                "    return new Inaccessible_MembersInjector(fooProvider, fooProvider2);}",
                "",
                "  @Override",
                "  public void injectMembers(Inaccessible instance) {",
                "    injectFoo(instance, fooProvider.get());",
                "    injectMethod(instance, fooProvider2.get());",
                "  }",
                "",
                "  @InjectedFieldSignature(\"other.Inaccessible.foo\")",
                "  public static void injectFoo(Object instance, Object foo) {",
                "    ((Inaccessible) instance).foo = (Foo) foo;",
                "  }",
                "",
                "  public static void injectMethod(Object instance, Object foo) {",
                "    ((Inaccessible) instance).method((Foo) foo);",
                "  }",
                "}"));
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            GeneratedLines.generatedImports(
                "import com.google.errorprone.annotations.CanIgnoreReturnValue;",
                "import other.Foo_Factory;",
                "import other.Inaccessible_Factory;",
                "import other.Inaccessible_MembersInjector;",
                "import other.UsesInaccessible;",
                "import other.UsesInaccessible_Factory;"),
            "",
            GeneratedLines.generatedAnnotations(),
            "final class DaggerTestComponent implements TestComponent {",
            "  private Object inaccessible() {",
            "    return injectInaccessible(Inaccessible_Factory.newInstance());",
            "  }",
            "",
            "  @Override",
            "  public UsesInaccessible usesInaccessible() {",
            "    return UsesInaccessible_Factory.newInstance(",
            "        inaccessible());",
            "  }",
            "",
            // TODO(ronshapiro): if possible, it would be great to rename "instance", but we
            // need to make sure that this doesn't conflict with any framework field in this or
            // any parent component
            "  @CanIgnoreReturnValue",
            "  private Object injectInaccessible(Object instance) {",
            "    Inaccessible_MembersInjector.injectFoo(instance, Foo_Factory.newInstance());",
            "    Inaccessible_MembersInjector.injectMethod(instance, Foo_Factory.newInstance());",
            "    return instance;",
            "  }",
            "}");
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void accessibleRawType_ofInaccessibleType() {
    JavaFileObject inaccessible =
        JavaFileObjects.forSourceLines(
            "other.Inaccessible",
            "package other;",
            "",
            "class Inaccessible {}");
    JavaFileObject inaccessiblesModule =
        JavaFileObjects.forSourceLines(
            "other.InaccessiblesModule",
            "package other;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.ArrayList;",
            "import java.util.List;",
            "import javax.inject.Provider;",
            "import javax.inject.Singleton;",
            "",
            "@Module",
            "public class InaccessiblesModule {",
            // force Provider initialization
            "  @Provides @Singleton static List<Inaccessible> inaccessibles() {",
            "    return new ArrayList<>();",
            "  }",
            "}");
    JavaFileObject usesInaccessibles =
        JavaFileObjects.forSourceLines(
            "other.UsesInaccessibles",
            "package other;",
            "",
            "import java.util.List;",
            "import javax.inject.Inject;",
            "",
            "public class UsesInaccessibles {",
            "  @Inject UsesInaccessibles() {}",
            "  @Inject List<Inaccessible> inaccessibles;",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "import other.UsesInaccessibles;",
            "",
            "@Singleton",
            "@Component(modules = other.InaccessiblesModule.class)",
            "interface TestComponent {",
            "  UsesInaccessibles usesInaccessibles();",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(inaccessible, inaccessiblesModule, usesInaccessibles, component);
    assertThat(compilation).succeeded();
    JavaFileObject generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines(
                "package test;",
                "",
                GeneratedLines.generatedAnnotations(),
                "final class DaggerTestComponent implements TestComponent {",
                "  private final DaggerTestComponent testComponent = this;",
                "",
                "  @SuppressWarnings(\"rawtypes\")",
                "  private Provider inaccessiblesProvider;")
            .addLinesIn(
                DEFAULT_MODE,
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.inaccessiblesProvider =",
                "        DoubleCheck.provider(InaccessiblesModule_InaccessiblesFactory.create());",
                "  }")
            .addLinesIn(
                FAST_INIT_MODE,
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.inaccessiblesProvider =",
                "        DoubleCheck.provider(",
                "            new SwitchingProvider<List>(testComponent, 0));",
                "  }")
            .addLines(
                "  @Override",
                "  public UsesInaccessibles usesInaccessibles() {",
                "    return injectUsesInaccessibles(UsesInaccessibles_Factory.newInstance());",
                "  }")
            .addLinesIn(
                FAST_INIT_MODE,
                "  @CanIgnoreReturnValue",
                "  private UsesInaccessibles injectUsesInaccessibles(UsesInaccessibles instance) {",
                "    UsesInaccessibles_MembersInjector",
                "        .injectInaccessibles(instance, (List) inaccessiblesProvider.get());",
                "    return instance;",
                "  }",
                "",
                "  private static final class SwitchingProvider<T> implements Provider<T> {",
                "    @SuppressWarnings(\"unchecked\")",
                "    @Override",
                "    public T get() {",
                "      switch (id) {",
                "        case 0:",
                "          return (T) InaccessiblesModule_InaccessiblesFactory.inaccessibles();",
                "        default: throw new AssertionError(id);",
                "      }",
                "    }",
                "  }",
                "}")
            .build();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void publicSupertypeHiddenSubtype() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "other.Foo",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");
    JavaFileObject supertype =
        JavaFileObjects.forSourceLines(
            "other.Supertype",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "public class Supertype<T> {",
            "  @Inject T t;",
            "}");
    JavaFileObject subtype =
        JavaFileObjects.forSourceLines(
            "other.Subtype",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Subtype extends Supertype<Foo> {",
            "  @Inject Subtype() {}",
            "}");
    JavaFileObject injectsSubtype =
        JavaFileObjects.forSourceLines(
            "other.InjectsSubtype",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "public class InjectsSubtype {",
            "  @Inject InjectsSubtype(Subtype s) {}",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  other.InjectsSubtype injectsSubtype();",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(foo, supertype, subtype, injectsSubtype, component);
    assertThat(compilation).succeeded();
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            GeneratedLines.generatedImports(
                "import com.google.errorprone.annotations.CanIgnoreReturnValue;",
                "import other.Foo_Factory;",
                "import other.InjectsSubtype;",
                "import other.InjectsSubtype_Factory;",
                "import other.Subtype_Factory;",
                "import other.Supertype;",
                "import other.Supertype_MembersInjector;"),
            "",
            GeneratedLines.generatedAnnotations(),
            "final class DaggerTestComponent implements TestComponent {",
            "  private Object subtype() {",
            "    return injectSubtype(Subtype_Factory.newInstance());",
            "  }",
            "",
            "  @Override",
            "  public InjectsSubtype injectsSubtype() {",
            "    return InjectsSubtype_Factory.newInstance(subtype());",
            "  }",
            "",
            "  @CanIgnoreReturnValue",
            "  private Object injectSubtype(Object instance) {",
            "    Supertype_MembersInjector.injectT(",
            "        (Supertype) instance, Foo_Factory.newInstance());",
            "    return instance;",
            "  }",
            "}");

    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }

  // Shows that we shouldn't create a members injector for a type that doesn't have
  // @Inject fields or @Inject constructor even if it extends and is extended by types that do.
  @Test
  public void middleClassNoFieldInjection() {
    JavaFileObject classA =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class A extends B {",
            "  @Inject String valueA;",
            "}");
    JavaFileObject classB =
        JavaFileObjects.forSourceLines(
            "test.B",
            "package test;",
            "",
            "class B extends C {",
            "}");
    JavaFileObject classC =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class C { ",
            "  @Inject String valueC;",
            "}");
    JavaFileObject expectedAMembersInjector =
        JavaFileObjects.forSourceLines(
            "test.A_MembersInjector",
            "package test;",
            "",
            GeneratedLines.generatedImports(
                "import dagger.MembersInjector;",
                "import dagger.internal.InjectedFieldSignature;",
                "import javax.inject.Provider;"),
            "",
            GeneratedLines.generatedAnnotations(),
            "public final class A_MembersInjector implements MembersInjector<A> {",
            "  private final Provider<String> valueCProvider;",
            "  private final Provider<String> valueAProvider;",
            "",
            "  public A_MembersInjector(",
            "        Provider<String> valueCProvider, Provider<String> valueAProvider) {",
            "    this.valueCProvider = valueCProvider;",
            "    this.valueAProvider = valueAProvider;",
            "  }",
            "",
            "  public static MembersInjector<A> create(",
            "      Provider<String> valueCProvider, Provider<String> valueAProvider) {",
            "    return new A_MembersInjector(valueCProvider, valueAProvider);",
            "  }",
            "",
            "  @Override",
            "  public void injectMembers(A instance) {",
            "    C_MembersInjector.injectValueC(instance, valueCProvider.get());",
            "    injectValueA(instance, valueAProvider.get());",
            "  }",
            "",
            "  @InjectedFieldSignature(\"test.A.valueA\")",
            "  public static void injectValueA(Object instance, String valueA) {",
            "    ((A) instance).valueA = valueA;",
            "  }",
            "}");

    JavaFileObject expectedCMembersInjector =
        JavaFileObjects.forSourceLines(
            "test.C_MembersInjector",
            "package test;",
            "",
            GeneratedLines.generatedImports(
                "import dagger.MembersInjector;",
                "import dagger.internal.InjectedFieldSignature;",
                "import javax.inject.Provider;"),
            "",
            GeneratedLines.generatedAnnotations(),
            "public final class C_MembersInjector implements MembersInjector<C> {",
            "  private final Provider<String> valueCProvider;",
            "",
            "  public C_MembersInjector(Provider<String> valueCProvider) {",
            "    this.valueCProvider = valueCProvider;",
            "  }",
            "",
            "  public static MembersInjector<C> create(",
            "      Provider<String> valueCProvider) {",
            "    return new C_MembersInjector(valueCProvider);",
            "  }",
            "",
            "  @Override",
            "  public void injectMembers(C instance) {",
            "    injectValueC(instance, valueCProvider.get());",
            "  }",
            "",
            "  @InjectedFieldSignature(\"test.C.valueC\")",
            "  public static void injectValueC(Object instance, String valueC) {",
            "    ((C) instance).valueC = valueC;",
            "  }",
            "}");


    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(classA, classB, classC);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.A_MembersInjector")
        .hasSourceEquivalentTo(expectedAMembersInjector);
    assertThat(compilation)
        .generatedSourceFile("test.C_MembersInjector")
        .hasSourceEquivalentTo(expectedCMembersInjector);

    try {
      assertThat(compilation).generatedSourceFile("test.B_MembersInjector");
      // Can't throw an assertion error since it would be caught.
      throw new IllegalStateException("Test generated a B_MembersInjector");
    } catch (AssertionError expected) {
    }
  }

  // Shows that we do generate a MembersInjector for a type that has an @Inject
  // constructor and that extends a type with @Inject fields, even if it has no local field
  // injection sites
  // TODO(erichang): Are these even used anymore?
  @Test
  public void testConstructorInjectedFieldInjection() {
    JavaFileObject classA =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class A extends B {",
            "  @Inject A() {}",
            "}");
    JavaFileObject classB =
        JavaFileObjects.forSourceLines(
            "test.B",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class B { ",
            "  @Inject String valueB;",
            "}");
    JavaFileObject expectedAMembersInjector =
        JavaFileObjects.forSourceLines(
            "test.A_MembersInjector",
            "package test;",
            "",
            GeneratedLines.generatedImports(
                "import dagger.MembersInjector;",
                "import javax.inject.Provider;"),
            "",
            GeneratedLines.generatedAnnotations(),
            "public final class A_MembersInjector implements MembersInjector<A> {",
            "  private final Provider<String> valueBProvider;",
            "",
            "  public A_MembersInjector(Provider<String> valueBProvider) {",
            "    this.valueBProvider = valueBProvider;",
            "  }",
            "",
            "  public static MembersInjector<A> create(Provider<String> valueBProvider) {",
            "    return new A_MembersInjector(valueBProvider);",
            "  }",
            "",
            "  @Override",
            "  public void injectMembers(A instance) {",
            "    B_MembersInjector.injectValueB(instance, valueBProvider.get());",
            "  }",
            "}");

    JavaFileObject expectedBMembersInjector =
        JavaFileObjects.forSourceLines(
            "test.B_MembersInjector",
            "package test;",
            "",
            GeneratedLines.generatedImports(
                "import dagger.MembersInjector;",
                "import dagger.internal.InjectedFieldSignature;",
                "import javax.inject.Provider;"),
            "",
            GeneratedLines.generatedAnnotations(),
            "public final class B_MembersInjector implements MembersInjector<B> {",
            "  private final Provider<String> valueBProvider;",
            "",
            "  public B_MembersInjector(Provider<String> valueBProvider) {",
            "    this.valueBProvider = valueBProvider;",
            "  }",
            "",
            "  public static MembersInjector<B> create(",
            "      Provider<String> valueBProvider) {",
            "    return new B_MembersInjector(valueBProvider);",
            "  }",
            "",
            "  @Override",
            "  public void injectMembers(B instance) {",
            "    injectValueB(instance, valueBProvider.get());",
            "  }",
            "",
            "  @InjectedFieldSignature(\"test.B.valueB\")",
            "  public static void injectValueB(Object instance, String valueB) {",
            "    ((B) instance).valueB = valueB;",
            "  }",
            "}");


    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(classA, classB);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.A_MembersInjector")
        .hasSourceEquivalentTo(expectedAMembersInjector);
    assertThat(compilation)
        .generatedSourceFile("test.B_MembersInjector")
        .hasSourceEquivalentTo(expectedBMembersInjector);
  }
}
