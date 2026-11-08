/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

import cn.labzen.beanmap.accessor.*;
import cn.labzen.beanmap.core.*;
import cn.labzen.beanmap.exception.*;
import cn.labzen.beanmap.spi.*;

import cn.labzen.beanmap.annotation.BeanUnmapped;
import org.junit.jupiter.api.Test;

import static cn.labzen.beanmap.TestFixtures.payload;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanComposerTest {

  public static class UserSrc {
    private String name = "user-name";
    private String email = "user@x";
    private Integer age = 20;
    private String secret = "hidden";
  }

  public static class ProfileSrc {
    private String name = "profile-name";
    private String phone = "123";
  }

  public static class ExtraDto {
    private String remark;
    private String internalNote;
    private Integer age;
  }

  public static class View {
    private String name;
    private String email;
    private String phone;
    private Integer age;
    private String remark;
    private String internalNote;
    @BeanUnmapped
    private String secret;

    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { email = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { phone = v; }
    public Integer getAge() { return age; }
    public void setAge(Integer v) { age = v; }
    public String getRemark() { return remark; }
    public void setRemark(String v) { remark = v; }
    public String getInternalNote() { return internalNote; }
    public void setInternalNote(String v) { internalNote = v; }
    public String getSecret() { return secret; }
    public void setSecret(String v) { secret = v; }
  }

  private static MapAccessor extra() {
    return new MapAccessor(ExtraDto.class, payload("remark", "from-map", "age", "30"));
  }

  @Test
  void lastWinsIsDefaultAndConvertsValues() {
    View view = BeanComposer.compose(new View())
            .from(new JavaBeanAccessor(new UserSrc()))
            .from(new JavaBeanAccessor(new ProfileSrc()))
            .from(extra())
            .excludes("internalNote")
            .apply();

    assertEquals("profile-name", view.getName());
    assertEquals("user@x", view.getEmail());
    assertEquals("123", view.getPhone());
    assertEquals(30, view.getAge());
    assertEquals("from-map", view.getRemark());
    assertNull(view.getInternalNote());
    assertNull(view.getSecret());
  }

  @Test
  void firstWinsKeepsEarliestSource() {
    View view = BeanComposer.compose(new View())
            .from(new JavaBeanAccessor(new UserSrc()))
            .from(new JavaBeanAccessor(new ProfileSrc()))
            .conflict(ConflictPolicy.firstWins())
            .apply();

    assertEquals("user-name", view.getName());
    assertEquals(20, view.getAge());
  }

  @Test
  void throwOnConflictAbortsWithFieldConflict() {
    BeanMapAbortException e = assertThrows(BeanMapAbortException.class, () ->
            BeanComposer.compose(new View())
                    .from(new JavaBeanAccessor(new UserSrc()))
                    .from(new JavaBeanAccessor(new ProfileSrc()))
                    .conflict(ConflictPolicy.throwOnConflict())
                    .apply());

    assertEquals(AbortCode.FIELD_CONFLICT, e.getAbortCode());
    assertEquals(409, e.getCode());
    assertTrue(e.getMessage().contains("name"));
  }

  @Test
  void threeStateSourceOnlyContributesProvidedKeys() {
    View view = BeanComposer.compose(new View())
            .from(new JavaBeanAccessor(new ProfileSrc()))
            .from(new MapAccessor(ExtraDto.class, payload("remark", "r")))
            .apply();

    assertEquals("profile-name", view.getName());
    assertEquals("r", view.getRemark());
    assertNull(view.getInternalNote());
    assertNull(view.getAge());
  }

  @Test
  void whitelistLimitsContributedProperties() {
    View view = BeanComposer.compose(new View())
            .from(new JavaBeanAccessor(new UserSrc()), "email")
            .from(new JavaBeanAccessor(new ProfileSrc()))
            .apply();

    assertEquals("profile-name", view.getName());
    assertEquals("user@x", view.getEmail());
    assertNull(view.getAge());
  }

  @Test
  void whitelistValidationHappensAtApplyTime() {
    var composer = BeanComposer.compose(new View())
            .from(new JavaBeanAccessor(new UserSrc()), "missing");

    assertThrows(BeanMapException.class, composer::apply);
  }

  @Test
  void customAbortHandlerReplacesDefaultException() {
    IllegalStateException e = assertThrows(IllegalStateException.class, () ->
            BeanComposer.compose(new View())
                    .from(new JavaBeanAccessor(new UserSrc()))
                    .from(new JavaBeanAccessor(new ProfileSrc()))
                    .withAbortHandler((code, message) -> new IllegalStateException(code.name()))
                    .conflict(ConflictPolicy.throwOnConflict())
                    .apply());

    assertEquals("FIELD_CONFLICT", e.getMessage());
  }
}
