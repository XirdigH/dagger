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

package dagger.functional.assisted.kotlin;

import static com.google.common.truth.Truth.assertThat;

import dagger.Component;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// This is a regression test for https://github.com/google/dagger/issues/2299
@RunWith(JUnit4.class)
public final class KotlinAssistedInjectionTest {
  @Component
  interface TestComponent {
    FooFactory fooFactory();

    FooDataFactory fooDataFactory();

    BarManager.Factory barManagerFactory();
  }

  @Test
  public void testFooFactory() {
    FooFactory fooFactory = DaggerKotlinAssistedInjectionTest_TestComponent.create().fooFactory();
    AssistedDep assistedDep = new AssistedDep();
    Foo foo = fooFactory.create(assistedDep);
    assertThat(foo.getAssistedDep()).isEqualTo(assistedDep);
  }

  @Test
  public void testFooDataFactory() {
    FooDataFactory fooDataFactory =
        DaggerKotlinAssistedInjectionTest_TestComponent.create().fooDataFactory();
    AssistedDep assistedDep = new AssistedDep();
    FooData fooData = fooDataFactory.create(assistedDep);
    assertThat(fooData.getAssistedDep()).isEqualTo(assistedDep);
  }

  @Test
  public void testBarManager() {
    BarManager.Factory barManagerFactory =
        DaggerKotlinAssistedInjectionTest_TestComponent.create().barManagerFactory();
    Bar bar = new Bar();
    String name = "someName";
    BarManager barManager = barManagerFactory.invoke(bar, name);
    assertThat(barManager.getBar()).isEqualTo(bar);
    assertThat(barManager.getName()).isEqualTo(name);
  }
}
