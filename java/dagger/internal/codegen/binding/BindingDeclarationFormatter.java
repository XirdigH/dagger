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

package dagger.internal.codegen.binding;

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static com.google.common.collect.Sets.immutableEnumSet;
import static dagger.internal.codegen.base.DiagnosticFormatting.stripCommonTypePrefixes;
import static dagger.internal.codegen.base.ElementFormatter.elementToString;
import static javax.lang.model.element.ElementKind.PARAMETER;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.EXECUTABLE;

import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.compat.XConverters;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.base.Formatter;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeKind;

/**
 * Formats a {@link BindingDeclaration} into a {@link String} suitable for use in error messages.
 */
public final class BindingDeclarationFormatter extends Formatter<BindingDeclaration> {
  private static final ImmutableSet<TypeKind> FORMATTABLE_ELEMENT_TYPE_KINDS =
      immutableEnumSet(EXECUTABLE, DECLARED);

  private final MethodSignatureFormatter methodSignatureFormatter;

  @Inject
  BindingDeclarationFormatter(MethodSignatureFormatter methodSignatureFormatter) {
    this.methodSignatureFormatter = methodSignatureFormatter;
  }

  /**
   * Returns {@code true} for declarations that this formatter can format. Specifically bindings
   * from subcomponent declarations or those with {@linkplain BindingDeclaration#bindingElement()
   * binding elements} that are methods, constructors, or types.
   */
  public boolean canFormat(BindingDeclaration bindingDeclaration) {
    if (bindingDeclaration instanceof SubcomponentDeclaration) {
      return true;
    }
    if (bindingDeclaration.bindingElement().isPresent()) {
      Element bindingElement = toJavac(bindingDeclaration.bindingElement().get());
      return bindingElement.getKind().equals(PARAMETER)
          || FORMATTABLE_ELEMENT_TYPE_KINDS.contains(bindingElement.asType().getKind());
    }
    // TODO(dpb): validate whether what this is doing is correct
    return false;
  }

  @Override
  public String format(BindingDeclaration bindingDeclaration) {
    if (bindingDeclaration instanceof SubcomponentDeclaration) {
      return formatSubcomponentDeclaration((SubcomponentDeclaration) bindingDeclaration);
    }

    if (bindingDeclaration.bindingElement().isPresent()) {
      Element bindingElement = toJavac(bindingDeclaration.bindingElement().get());
      if (bindingElement.getKind().equals(PARAMETER)) {
        return elementToString(bindingElement);
      }

      switch (bindingElement.asType().getKind()) {
        case EXECUTABLE:
          return methodSignatureFormatter.format(
              MoreElements.asExecutable(bindingElement),
              bindingDeclaration
                  .contributingModule()
                  .map(XConverters::toJavac)
                  .map(module -> MoreTypes.asDeclared(module.asType())));

        case DECLARED:
          return stripCommonTypePrefixes(bindingElement.asType().toString());

        default:
          throw new IllegalArgumentException(
              "Formatting unsupported for element: " + bindingElement);
      }
    }

    return String.format(
        "Dagger-generated binding for %s",
        stripCommonTypePrefixes(bindingDeclaration.key().toString()));
  }

  private String formatSubcomponentDeclaration(SubcomponentDeclaration subcomponentDeclaration) {
    ImmutableList<XTypeElement> moduleSubcomponents =
        subcomponentDeclaration.moduleAnnotation().subcomponents();
    int index = moduleSubcomponents.indexOf(subcomponentDeclaration.subcomponentType());
    StringBuilder annotationValue = new StringBuilder();
    if (moduleSubcomponents.size() != 1) {
      annotationValue.append("{");
    }
    annotationValue.append(
        formatArgumentInList(
            index,
            moduleSubcomponents.size(),
            subcomponentDeclaration.subcomponentType().getQualifiedName() + ".class"));
    if (moduleSubcomponents.size() != 1) {
      annotationValue.append("}");
    }

    return String.format(
        "@%s(subcomponents = %s) for %s",
        subcomponentDeclaration.moduleAnnotation().simpleName(),
        annotationValue,
        subcomponentDeclaration.contributingModule().get());
  }
}
