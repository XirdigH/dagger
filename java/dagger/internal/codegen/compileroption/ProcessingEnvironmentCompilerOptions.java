/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.internal.codegen.compileroption;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Sets.immutableEnumSet;
import static dagger.internal.codegen.compileroption.FeatureStatus.DISABLED;
import static dagger.internal.codegen.compileroption.FeatureStatus.ENABLED;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Feature.EXPERIMENTAL_AHEAD_OF_TIME_SUBCOMPONENTS;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Feature.EXPERIMENTAL_ANDROID_MODE;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Feature.EXPERIMENTAL_DAGGER_ERROR_MESSAGES;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Feature.FAST_INIT;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Feature.FLOATING_BINDS_METHODS;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Feature.FORMAT_GENERATED_SOURCE;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Feature.IGNORE_PRIVATE_AND_STATIC_INJECTION_FOR_COMPONENT;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Feature.PLUGINS_VISIT_FULL_BINDING_GRAPHS;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Feature.STRICT_MULTIBINDING_VALIDATION;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Feature.VALIDATE_TRANSITIVE_COMPONENT_DEPENDENCIES;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Feature.WARN_IF_INJECTION_FACTORY_NOT_GENERATED_UPSTREAM;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Feature.WRITE_PRODUCER_NAME_IN_TOKEN;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.KeyOnlyOption.HEADER_COMPILATION;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.KeyOnlyOption.USE_GRADLE_INCREMENTAL_PROCESSING;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Validation.DISABLE_INTER_COMPONENT_SCOPE_VALIDATION;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Validation.EXPLICIT_BINDING_CONFLICTS_WITH_INJECT;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Validation.FULL_BINDING_GRAPH_VALIDATION;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Validation.MODULE_HAS_DIFFERENT_SCOPES_VALIDATION;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Validation.NULLABLE_VALIDATION;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Validation.PRIVATE_MEMBER_VALIDATION;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Validation.STATIC_MEMBER_VALIDATION;
import static dagger.internal.codegen.compileroption.ValidationType.ERROR;
import static dagger.internal.codegen.compileroption.ValidationType.NONE;
import static dagger.internal.codegen.compileroption.ValidationType.WARNING;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.concat;

import androidx.room.compiler.processing.XMessager;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.langmodel.DaggerElements;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/** {@link CompilerOptions} for the given processor. */
public final class ProcessingEnvironmentCompilerOptions extends CompilerOptions {
  // EnumOption<T> doesn't support integer inputs so just doing this as a 1-off for now.
  private static final String KEYS_PER_COMPONENT_SHARD = "dagger.keysPerComponentShard";

  private final XMessager messager;
  private final Map<String, String> options;
  private final DaggerElements elements;
  private final Map<EnumOption<?>, Object> enumOptions = new HashMap<>();
  private final Map<EnumOption<?>, ImmutableMap<String, ? extends Enum<?>>> allCommandLineOptions =
      new HashMap<>();

  @Inject
  ProcessingEnvironmentCompilerOptions(
      XMessager messager, @ProcessingOptions Map<String, String> options, DaggerElements elements) {
    this.messager = messager;
    this.options = options;
    this.elements = elements;
    checkValid();
  }

  @Override
  public boolean usesProducers() {
    return elements.getTypeElement(TypeNames.PRODUCES) != null;
  }

  @Override
  public boolean headerCompilation() {
    return isEnabled(HEADER_COMPILATION);
  }

  @Override
  public boolean experimentalMergedMode(TypeElement component) {
    boolean isExperimental = experimentalMergedModeInternal();
    if (isExperimental) {
      checkState(
          !fastInitInternal(component),
          "Both fast init and experimental merged mode were turned on, please specify exactly one"
              + " compilation mode.");
    }
    return isExperimental;
  }

  @Override
  public boolean fastInit(TypeElement component) {
    boolean isFastInit = fastInitInternal(component);
    if (isFastInit) {
      checkState(
          !experimentalMergedModeInternal(),
          "Both fast init and experimental merged mode were turned on, please specify exactly one"
              + " compilation mode.");
    }
    return isFastInit;
  }

  private boolean fastInitInternal(TypeElement component) {
    return isEnabled(FAST_INIT);
  }

  private boolean experimentalMergedModeInternal() {
    return false;
  }

  @Override
  public boolean formatGeneratedSource() {
    return isEnabled(FORMAT_GENERATED_SOURCE);
  }

  @Override
  public boolean writeProducerNameInToken() {
    return isEnabled(WRITE_PRODUCER_NAME_IN_TOKEN);
  }

  @Override
  public Diagnostic.Kind nullableValidationKind() {
    return diagnosticKind(NULLABLE_VALIDATION);
  }

  @Override
  public Diagnostic.Kind privateMemberValidationKind() {
    return diagnosticKind(PRIVATE_MEMBER_VALIDATION);
  }

  @Override
  public Diagnostic.Kind staticMemberValidationKind() {
    return diagnosticKind(STATIC_MEMBER_VALIDATION);
  }

  @Override
  public boolean ignorePrivateAndStaticInjectionForComponent() {
    return isEnabled(IGNORE_PRIVATE_AND_STATIC_INJECTION_FOR_COMPONENT);
  }

  @Override
  public ValidationType scopeCycleValidationType() {
    return parseOption(DISABLE_INTER_COMPONENT_SCOPE_VALIDATION);
  }

  @Override
  public boolean validateTransitiveComponentDependencies() {
    return isEnabled(VALIDATE_TRANSITIVE_COMPONENT_DEPENDENCIES);
  }

  @Override
  public boolean warnIfInjectionFactoryNotGeneratedUpstream() {
    return isEnabled(WARN_IF_INJECTION_FACTORY_NOT_GENERATED_UPSTREAM);
  }

  @Override
  public ValidationType fullBindingGraphValidationType() {
    return parseOption(FULL_BINDING_GRAPH_VALIDATION);
  }

  @Override
  public boolean pluginsVisitFullBindingGraphs(TypeElement component) {
    return isEnabled(PLUGINS_VISIT_FULL_BINDING_GRAPHS);
  }

  @Override
  public Diagnostic.Kind moduleHasDifferentScopesDiagnosticKind() {
    return diagnosticKind(MODULE_HAS_DIFFERENT_SCOPES_VALIDATION);
  }

  @Override
  public ValidationType explicitBindingConflictsWithInjectValidationType() {
    return parseOption(EXPLICIT_BINDING_CONFLICTS_WITH_INJECT);
  }

  @Override
  public boolean experimentalDaggerErrorMessages() {
    return isEnabled(EXPERIMENTAL_DAGGER_ERROR_MESSAGES);
  }

  @Override
  public boolean strictMultibindingValidation() {
    return isEnabled(STRICT_MULTIBINDING_VALIDATION);
  }

  @Override
  public int keysPerComponentShard(TypeElement component) {
    if (options.containsKey(KEYS_PER_COMPONENT_SHARD)) {
      checkArgument(
          com.google.auto.common.MoreElements.getPackage(component)
              .getQualifiedName().toString().startsWith("dagger."),
          "Cannot set %s. It is only meant for internal testing.", KEYS_PER_COMPONENT_SHARD);
      return Integer.parseInt(options.get(KEYS_PER_COMPONENT_SHARD));
    }
    return super.keysPerComponentShard(component);
  }

  private boolean isEnabled(KeyOnlyOption keyOnlyOption) {
    return options.containsKey(keyOnlyOption.toString());
  }

  private boolean isEnabled(Feature feature) {
    return parseOption(feature).equals(ENABLED);
  }

  private Diagnostic.Kind diagnosticKind(Validation validation) {
    return parseOption(validation).diagnosticKind().get();
  }

  @SuppressWarnings("CheckReturnValue")
  private ProcessingEnvironmentCompilerOptions checkValid() {
    for (KeyOnlyOption keyOnlyOption : KeyOnlyOption.values()) {
      isEnabled(keyOnlyOption);
    }
    for (Feature feature : Feature.values()) {
      parseOption(feature);
    }
    for (Validation validation : Validation.values()) {
      parseOption(validation);
    }
    noLongerRecognized(EXPERIMENTAL_ANDROID_MODE);
    noLongerRecognized(FLOATING_BINDS_METHODS);
    noLongerRecognized(EXPERIMENTAL_AHEAD_OF_TIME_SUBCOMPONENTS);
    noLongerRecognized(USE_GRADLE_INCREMENTAL_PROCESSING);
    return this;
  }

  private void noLongerRecognized(CommandLineOption commandLineOption) {
    if (options.containsKey(commandLineOption.toString())) {
      messager.printMessage(
          Diagnostic.Kind.WARNING, commandLineOption + " is no longer recognized by Dagger");
    }
  }

  private interface CommandLineOption {
    /** The key of the option (appears after "-A"). */
    @Override
    String toString();

    /**
     * Returns all aliases besides {@link #toString()}, such as old names for an option, in order of
     * precedence.
     */
    default ImmutableList<String> aliases() {
      return ImmutableList.of();
    }

    /** All the command-line names for this option, in order of precedence. */
    default Stream<String> allNames() {
      return concat(Stream.of(toString()), aliases().stream());
    }
  }

  /** An option that can be set on the command line. */
  private interface EnumOption<E extends Enum<E>> extends CommandLineOption {
    /** The default value for this option. */
    E defaultValue();

    /** The valid values for this option. */
    Set<E> validValues();
  }

  enum KeyOnlyOption implements CommandLineOption {
    HEADER_COMPILATION {
      @Override
      public String toString() {
        return "experimental_turbine_hjar";
      }
    },

    USE_GRADLE_INCREMENTAL_PROCESSING {
      @Override
      public String toString() {
        return "dagger.gradle.incremental";
      }
    },
  }

  /**
   * A feature that can be enabled or disabled on the command line by setting {@code -Akey=ENABLED}
   * or {@code -Akey=DISABLED}.
   */
  enum Feature implements EnumOption<FeatureStatus> {
    FAST_INIT,

    EXPERIMENTAL_ANDROID_MODE,

    FORMAT_GENERATED_SOURCE,

    WRITE_PRODUCER_NAME_IN_TOKEN,

    WARN_IF_INJECTION_FACTORY_NOT_GENERATED_UPSTREAM,

    IGNORE_PRIVATE_AND_STATIC_INJECTION_FOR_COMPONENT,

    EXPERIMENTAL_AHEAD_OF_TIME_SUBCOMPONENTS,

    FORCE_USE_SERIALIZED_COMPONENT_IMPLEMENTATIONS,

    EMIT_MODIFIABLE_METADATA_ANNOTATIONS(ENABLED),

    PLUGINS_VISIT_FULL_BINDING_GRAPHS,

    FLOATING_BINDS_METHODS,

    EXPERIMENTAL_DAGGER_ERROR_MESSAGES,

    STRICT_MULTIBINDING_VALIDATION,

    VALIDATE_TRANSITIVE_COMPONENT_DEPENDENCIES(ENABLED)
    ;

    final FeatureStatus defaultValue;

    Feature() {
      this(DISABLED);
    }

    Feature(FeatureStatus defaultValue) {
      this.defaultValue = defaultValue;
    }

    @Override
    public FeatureStatus defaultValue() {
      return defaultValue;
    }

    @Override
    public Set<FeatureStatus> validValues() {
      return EnumSet.allOf(FeatureStatus.class);
    }

    @Override
    public String toString() {
      return optionName(this);
    }
  }

  /** The diagnostic kind or validation type for a kind of validation. */
  enum Validation implements EnumOption<ValidationType> {
    DISABLE_INTER_COMPONENT_SCOPE_VALIDATION(),

    NULLABLE_VALIDATION(ERROR, WARNING),

    PRIVATE_MEMBER_VALIDATION(ERROR, WARNING),

    STATIC_MEMBER_VALIDATION(ERROR, WARNING),

    /** Whether to validate full binding graphs for components, subcomponents, and modules. */
    FULL_BINDING_GRAPH_VALIDATION(NONE, ERROR, WARNING) {
      @Override
      public ImmutableList<String> aliases() {
        return ImmutableList.of("dagger.moduleBindingValidation");
      }
    },

    /**
     * How to report conflicting scoped bindings when validating partial binding graphs associated
     * with modules.
     */
    MODULE_HAS_DIFFERENT_SCOPES_VALIDATION(ERROR, WARNING),

    /**
     * How to report that an explicit binding in a subcomponent conflicts with an {@code @Inject}
     * constructor used in an ancestor component.
     */
    EXPLICIT_BINDING_CONFLICTS_WITH_INJECT(WARNING, ERROR, NONE),
    ;

    final ValidationType defaultType;
    final ImmutableSet<ValidationType> validTypes;

    Validation() {
      this(ERROR, WARNING, NONE);
    }

    Validation(ValidationType defaultType, ValidationType... moreValidTypes) {
      this.defaultType = defaultType;
      this.validTypes = immutableEnumSet(defaultType, moreValidTypes);
    }

    @Override
    public ValidationType defaultValue() {
      return defaultType;
    }

    @Override
    public Set<ValidationType> validValues() {
      return validTypes;
    }

    @Override
    public String toString() {
      return optionName(this);
    }
  }

  private static String optionName(Enum<? extends EnumOption<?>> option) {
    return "dagger." + UPPER_UNDERSCORE.to(LOWER_CAMEL, option.name());
  }

  /** The supported command-line options. */
  public static ImmutableSet<String> supportedOptions() {
    // need explicit type parameter to avoid a runtime stream error
    return ImmutableSet.<String>builder()
        .addAll(
            Stream.<CommandLineOption[]>of(
                KeyOnlyOption.values(), Feature.values(), Validation.values())
            .flatMap(Arrays::stream)
            .flatMap(CommandLineOption::allNames)
            .collect(toImmutableSet()))
        .add(KEYS_PER_COMPONENT_SHARD)
        .build();
  }

  /**
   * Returns the value for the option as set on the command line by any name, or the default value
   * if not set.
   *
   * <p>If more than one name is used to set the value, but all names specify the same value,
   * reports a warning and returns that value.
   *
   * <p>If more than one name is used to set the value, and not all names specify the same value,
   * reports an error and returns the default value.
   */
  private <T extends Enum<T>> T parseOption(EnumOption<T> option) {
    @SuppressWarnings("unchecked") // we only put covariant values into the map
    T value = (T) enumOptions.computeIfAbsent(option, this::parseOptionUncached);
    return value;
  }

  private boolean isSetOnCommandLine(Feature feature) {
    return getUsedNames(feature).count() > 0;
  }

  private <T extends Enum<T>> T parseOptionUncached(EnumOption<T> option) {
    ImmutableMap<String, T> values = parseOptionWithAllNames(option);

    // If no value is specified, return the default value.
    if (values.isEmpty()) {
      return option.defaultValue();
    }

    // If all names have the same value, return that.
    if (values.asMultimap().inverse().keySet().size() == 1) {
      // Warn if an option was set with more than one name. That would be an error if the values
      // differed.
      if (values.size() > 1) {
        reportUseOfDifferentNamesForOption(Diagnostic.Kind.WARNING, option, values.keySet());
      }
      return values.values().asList().get(0);
    }

    // If different names have different values, report an error and return the default
    // value.
    reportUseOfDifferentNamesForOption(Diagnostic.Kind.ERROR, option, values.keySet());
    return option.defaultValue();
  }

  private void reportUseOfDifferentNamesForOption(
      Diagnostic.Kind diagnosticKind, EnumOption<?> option, ImmutableSet<String> usedNames) {
    messager.printMessage(
        diagnosticKind,
        String.format(
            "Only one of the equivalent options (%s) should be used; prefer -A%s",
            usedNames.stream().map(name -> "-A" + name).collect(joining(", ")), option));
  }

  private <T extends Enum<T>> ImmutableMap<String, T> parseOptionWithAllNames(
      EnumOption<T> option) {
    @SuppressWarnings("unchecked") // map is covariant
    ImmutableMap<String, T> aliasValues =
        (ImmutableMap<String, T>)
            allCommandLineOptions.computeIfAbsent(option, this::parseOptionWithAllNamesUncached);
    return aliasValues;
  }

  private <T extends Enum<T>> ImmutableMap<String, T> parseOptionWithAllNamesUncached(
      EnumOption<T> option) {
    ImmutableMap.Builder<String, T> values = ImmutableMap.builder();
    getUsedNames(option)
        .forEach(
            name -> parseOptionWithName(option, name).ifPresent(value -> values.put(name, value)));
    return values.build();
  }

  private <T extends Enum<T>> Optional<T> parseOptionWithName(EnumOption<T> option, String key) {
    checkArgument(options.containsKey(key), "key %s not found", key);
    String stringValue = options.get(key);
    if (stringValue == null) {
      messager.printMessage(Diagnostic.Kind.ERROR, "Processor option -A" + key + " needs a value");
    } else {
      try {
        T value =
            Enum.valueOf(option.defaultValue().getDeclaringClass(), Ascii.toUpperCase(stringValue));
        if (option.validValues().contains(value)) {
          return Optional.of(value);
        }
      } catch (IllegalArgumentException e) {
        // handled below
      }
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          String.format(
              "Processor option -A%s may only have the values %s (case insensitive), found: %s",
              key, option.validValues(), stringValue));
    }
    return Optional.empty();
  }

  private Stream<String> getUsedNames(CommandLineOption option) {
    return option.allNames().filter(options::containsKey);
  }
}
