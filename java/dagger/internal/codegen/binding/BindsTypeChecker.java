/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.internal.codegen.binding;

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static com.google.common.collect.Iterables.getOnlyElement;

import androidx.room.compiler.processing.XType;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Checks the assignability of one type to another, given a {@link ContributionType} context. This
 * is used by {@link dagger.internal.codegen.validation.BindsMethodValidator} to validate that the
 * right-hand- side of a {@link dagger.Binds} method is valid, as well as in {@link
 * dagger.internal.codegen.writing.DelegateRequestRepresentation} when the right-hand-side in
 * generated code might be an erased type due to accessibility.
 */
public final class BindsTypeChecker {
  private final DaggerTypes types;
  private final DaggerElements elements;

  // TODO(bcorso): Make this pkg-private. Used by DelegateRequestRepresentation.
  @Inject
  public BindsTypeChecker(DaggerTypes types, DaggerElements elements) {
    this.types = types;
    this.elements = elements;
  }

  /**
   * Checks the assignability of {@code rightHandSide} to {@code leftHandSide} given a {@link
   * ContributionType} context.
   */
  public boolean isAssignable(
      XType rightHandSide, XType leftHandSide, ContributionType contributionType) {
    return isAssignable(toJavac(rightHandSide), toJavac(leftHandSide), contributionType);
  }

  /**
   * Checks the assignability of {@code rightHandSide} to {@code leftHandSide} given a {@link
   * ContributionType} context.
   */
  public boolean isAssignable(
      TypeMirror rightHandSide, TypeMirror leftHandSide, ContributionType contributionType) {
    return types.isAssignable(rightHandSide, desiredAssignableType(leftHandSide, contributionType));
  }

  private TypeMirror desiredAssignableType(
      TypeMirror leftHandSide, ContributionType contributionType) {
    switch (contributionType) {
      case UNIQUE:
        return leftHandSide;
      case SET:
        DeclaredType parameterizedSetType = types.getDeclaredType(setElement(), leftHandSide);
        return methodParameterType(parameterizedSetType, "add");
      case SET_VALUES:
        return methodParameterType(MoreTypes.asDeclared(leftHandSide), "addAll");
      case MAP:
        DeclaredType parameterizedMapType =
            types.getDeclaredType(mapElement(), unboundedWildcard(), leftHandSide);
        return methodParameterTypes(parameterizedMapType, "put").get(1);
    }
    throw new AssertionError("Unknown contribution type: " + contributionType);
  }

  private ImmutableList<TypeMirror> methodParameterTypes(DeclaredType type, String methodName) {
    ImmutableList.Builder<ExecutableElement> methodsForName = ImmutableList.builder();
    for (ExecutableElement method :
        // type.asElement().getEnclosedElements() is not used because some non-standard JDKs (e.g.
        // J2CL) don't redefine Set.add() (whose only purpose of being redefined in the standard JDK
        // is documentation, and J2CL's implementation doesn't declare docs for JDK types).
        // getLocalAndInheritedMethods ensures that the method will always be present.
        elements.getLocalAndInheritedMethods(MoreTypes.asTypeElement(type))) {
      if (method.getSimpleName().contentEquals(methodName)) {
        methodsForName.add(method);
      }
    }
    ExecutableElement method = getOnlyElement(methodsForName.build());
    return ImmutableList.copyOf(
        MoreTypes.asExecutable(types.asMemberOf(type, method)).getParameterTypes());
  }

  private TypeMirror methodParameterType(DeclaredType type, String methodName) {
    return getOnlyElement(methodParameterTypes(type, methodName));
  }

  private TypeElement setElement() {
    return elements.getTypeElement(TypeNames.SET);
  }

  private TypeElement mapElement() {
    return elements.getTypeElement(TypeNames.MAP);
  }

  private TypeMirror unboundedWildcard() {
    return types.getWildcardType(null, null);
  }
}
