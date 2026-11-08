/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

import cn.labzen.beanmap.accessor.*;
import cn.labzen.beanmap.core.*;
import cn.labzen.beanmap.exception.*;
import cn.labzen.beanmap.spi.*;

import org.junit.jupiter.api.Test;

import static cn.labzen.beanmap.TestFixtures.PrimitiveDto;
import static cn.labzen.beanmap.TestFixtures.User;
import static cn.labzen.beanmap.TestFixtures.UserDto;
import static cn.labzen.beanmap.TestFixtures.payload;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanMapperTest {

  private static MapAccessor source(Object... pairs) {
    return new MapAccessor(UserDto.class, payload(pairs));
  }

  @Test
  void absentLeavesDomainUntouched() {
    User user = new User();
    BeanMapResource<User> result = BeanMapper.of(user, source("email", "a@b.c")).mapAll().apply();

    assertEquals("original", user.getName());
    assertEquals("a@b.c", user.getEmail());
    assertTrue(result.anyWritten());
    assertFalse(result.willWrite(user::getName));
  }

  @Test
  void presentValueIsWritten() {
    User user = new User();
    BeanMapper.of(user, source("name", "Ada", "age", 30)).mapAll().apply();

    assertEquals("Ada", user.getName());
    assertEquals(30, user.getAge());
  }

  @Test
  void explicitNullNeedsNullableToReadBack() {
    User user = new User();
    BeanMapResource<User> result = BeanMapper.of(user, source("name", null)).mapAll().apply();

    assertNull(user.getName());
    assertThrows(BeanMapException.class, () -> result.written(user::getName));
  }

  @Test
  void nullableAllowsExplicitNull() {
    User user = new User();
    BeanMapResource<User> result = BeanMapper.of(user, source("name", null))
            .map("name", User::setName).nullable().apply();

    assertNull(user.getName());
    assertNull(result.written(user::getName));
    assertTrue(result.willWrite(user::getName));
  }

  @Test
  void notClearableAbortsOnExplicitNull() {
    User user = new User();
    BeanMapAbortException e = assertThrows(BeanMapAbortException.class, () ->
            BeanMapper.of(user, source("name", null))
                    .map("name", User::setName).notClearable().apply());

    assertEquals(AbortCode.NOT_CLEARABLE, e.getAbortCode());
  }

  @Test
  void requireAnyProvidedAbortsWithNoChange() {
    BeanMapAbortException e = assertThrows(BeanMapAbortException.class, () ->
            BeanMapper.of(new User(), source()).requireAnyProvided().mapAll().apply());

    assertEquals(AbortCode.NO_CHANGE, e.getAbortCode());
    assertEquals(304, e.getCode());
  }

  @Test
  void requiredIsEnforcedWhenIgnoreBlankMatches() {
    User user = new User();
    var chain = BeanMapper.of(user, source("name", "   "))
            .map("name", User::setName).ignoreBlank().required();

    BeanMapAbortException e = assertThrows(BeanMapAbortException.class, chain::apply);
    assertEquals(AbortCode.REQUIRED, e.getAbortCode());
  }

  @Test
  void trimAndDefaultApply() {
    User trimmed = new User();
    BeanMapper.of(trimmed, source("name", "  Ada  "))
            .map("name", User::setName).trim().apply();
    assertEquals("Ada", trimmed.getName());

    User defaulted = new User();
    BeanMapper.of(defaulted, source("age", null))
            .map("age", User::setAge).withDefault(7).apply();
    assertEquals(7, defaulted.getAge());
  }

  @Test
  void excludesBlocksMapAll() {
    UserDto dto = new UserDto();
    User user = new User();
    BeanMapper.of(user, source("name", "Ada", "email", "a@b.c"))
            .excludes(dto::getName).mapAll().apply();

    assertEquals("original", user.getName());
    assertEquals("a@b.c", user.getEmail());
  }

  @Test
  void coverageVerifyAcceptsCompleteDeclaration() {
    BeanMapper<User> chain = BeanMapper.of(new User(), source()).mapAll();
    BeanMapCoverage.verify(chain);
  }

  @Test
  void coverageVerifyRejectsMissingDeclaration() {
    var chain = BeanMapper.of(new User(), source()).map("name", User::setName);
    assertThrows(BeanMapException.class, () -> BeanMapCoverage.verify(chain));
  }

  @Test
  void coverageVerifyRejectsPrimitiveDtoField() {
    BeanMapper<User> chain = BeanMapper.of(new User(), new MapAccessor(PrimitiveDto.class, payload()));
    assertThrows(BeanMapException.class, () -> BeanMapCoverage.verify(chain));
  }

  @Test
  void readDirectionSkipsBeanUnmappedTargetFields() {
    ReadTarget target = new ReadTarget();
    BeanReader.read(new ReadSource(), target).mapAll().apply();

    assertEquals("n", target.getName());
  }

  public static class ReadSource {
    public String getName() { return "n"; }
    public String getIgnored() { return "x"; }
  }

  public static class ReadTarget {
    @cn.labzen.beanmap.annotation.BeanUnmapped
    private String ignored;
    private String name;

    public String getName() { return name; }
    public void setName(String v) { name = v; }
  }
}
