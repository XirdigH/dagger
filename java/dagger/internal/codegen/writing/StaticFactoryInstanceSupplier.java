/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.internal.codegen.writing;

import static dagger.spi.model.BindingKind.MULTIBOUND_MAP;
import static dagger.spi.model.BindingKind.MULTIBOUND_SET;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.ProvisionBinding;

/** An object that returns static factory to satisfy framework instance request. */
final class StaticFactoryInstanceSupplier implements FrameworkInstanceSupplier {
  private final FrameworkInstanceSupplier frameworkInstanceSupplier;

  @AssistedInject
  StaticFactoryInstanceSupplier(
      @Assisted ProvisionBinding binding,
      FrameworkInstanceBindingRepresentation.Factory
          frameworkInstanceBindingRepresentationFactory) {
    this.frameworkInstanceSupplier = () -> staticFactoryCreation(binding);
  }

  @Override
  public MemberSelect memberSelect() {
    return frameworkInstanceSupplier.memberSelect();
  }

  /**
   * If {@code resolvedBindings} is an unscoped provision binding with no factory arguments, then we
   * don't need a field to hold its factory. In that case, this method returns the static member
   * select that returns the factory.
   */
  // TODO(wanyingd): no-op members injector is currently handled in
  // `MembersInjectorProviderCreationExpression`, we should inline the logic here so we won't create
  // an extra field for it.
  private MemberSelect staticFactoryCreation(ProvisionBinding binding) {
    switch (binding.kind()) {
      case MULTIBOUND_MAP:
        return StaticMemberSelects.emptyMapFactory(binding);
      case MULTIBOUND_SET:
        return StaticMemberSelects.emptySetFactory(binding);
      case PROVISION:
      case INJECTION:
        return StaticMemberSelects.factoryCreateNoArgumentMethod(binding);
      default:
        throw new AssertionError(String.format("Invalid binding kind: %s", binding.kind()));
    }
  }

  static boolean usesStaticFactoryCreation(ProvisionBinding binding, boolean isFastInit) {
    if (!binding.dependencies().isEmpty() || binding.scope().isPresent()) {
      return false;
    }
    switch (binding.kind()) {
      case MULTIBOUND_MAP:
      case MULTIBOUND_SET:
        return true;
      case PROVISION:
        return !isFastInit && !binding.requiresModuleInstance();
      case INJECTION:
        return !isFastInit;
      default:
        return false;
    }
  }

  @AssistedFactory
  static interface Factory {
    StaticFactoryInstanceSupplier create(ProvisionBinding binding);
  }
}
