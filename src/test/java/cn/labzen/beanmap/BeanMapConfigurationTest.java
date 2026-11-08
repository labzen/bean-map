/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.convert.support.GenericConversionService;

import static cn.labzen.beanmap.TestFixtures.User;
import static cn.labzen.beanmap.TestFixtures.UserDto;
import static cn.labzen.beanmap.TestFixtures.payload;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BeanMapConfigurationTest {

  public static class NameSrc {
    private String name = "from-src";
  }

  public static class OtherSrc {
    private String name = "from-other";
  }

  public static class Target {
    private String name;
    private Integer age;

    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public Integer getAge() { return age; }
    public void setAge(Integer v) { age = v; }
  }

  @Test
  void defaultsMatchDocumentedValues() {
    BeanMapConfiguration defaults = BeanMapConfiguration.defaults();

    assertNotNull(defaults.conversionService());
    assertNotNull(defaults.abortHandler());
    assertNotNull(defaults.instantiation());
    assertEquals(ConflictPolicy.LAST_WINS, defaults.conflictPolicy());
  }

  @Test
  void beanMapperUsesConfiguredConversionService() {
    GenericConversionService custom = new GenericConversionService();
    DefaultConversionService.addDefaultConverters(custom);
    custom.addConverter(String.class, Integer.class, s -> 42);
    BeanMapConfiguration config = BeanMapConfiguration.builder().conversionService(custom).build();

    User user = new User();
    BeanMapper.of(user, new MapAccessor(UserDto.class, payload("age", "7")))
            .withConfiguration(config).map("age", User::setAge).apply();

    assertEquals(42, user.getAge());
  }

  @Test
  void beanMapperUsesConfiguredAbortHandler() {
    BeanMapConfiguration config = BeanMapConfiguration.builder()
            .abortHandler((code, message) -> new IllegalStateException(code.name()))
            .build();

    IllegalStateException e = assertThrows(IllegalStateException.class, () ->
            BeanMapper.of(new User(), new MapAccessor(UserDto.class, payload()))
                    .withConfiguration(config).requireAnyProvided().mapAll().apply());

    assertEquals("NO_CHANGE", e.getMessage());
  }

  @Test
  void beanComposerUsesConfiguredConflictPolicy() {
    BeanMapConfiguration config = BeanMapConfiguration.builder()
            .conflict(ConflictPolicy.firstWins()).build();

    Target target = BeanComposer.compose(new Target())
            .withConfiguration(config)
            .from(new JavaBeanAccessor(new NameSrc()))
            .from(new JavaBeanAccessor(new OtherSrc()))
            .apply();

    assertEquals("from-src", target.getName());
  }

  @Test
  void laterDeclarationOverridesConfiguration() {
    BeanMapConfiguration config = BeanMapConfiguration.builder()
            .conflict(ConflictPolicy.firstWins()).build();

    BeanMapAbortException e = assertThrows(BeanMapAbortException.class, () ->
            BeanComposer.compose(new Target())
                    .withConfiguration(config)
                    .from(new JavaBeanAccessor(new NameSrc()))
                    .from(new JavaBeanAccessor(new OtherSrc()))
                    .conflict(ConflictPolicy.throwOnConflict())
                    .apply());

    assertEquals(AbortCode.FIELD_CONFLICT, e.getAbortCode());
  }
}
