/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static cn.labzen.beanmap.TestFixtures.Point;
import static cn.labzen.beanmap.TestFixtures.PointDomain;
import static cn.labzen.beanmap.TestFixtures.UserDto;
import static cn.labzen.beanmap.TestFixtures.payload;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceAccessorTest {

  public static class Bean {
    private String name = "Ada";
    private Integer age = 20;
  }

  private enum Kind { ONE }

  @Test
  void javaBeanAccessorIsTwoState() {
    JavaBeanAccessor accessor = new JavaBeanAccessor(new Bean());

    assertTrue(accessor.isProvided("name"));
    assertTrue(accessor.isProvided("age"));
    assertEquals("Ada", accessor.getValue("name"));
    assertEquals(20, accessor.getValue("age"));
    assertEquals(Bean.class, accessor.type());
    assertThrows(BeanMapException.class, () -> accessor.getValue("missing"));
  }

  @Test
  void recordSourceUsesComponentAccessor() {
    JavaBeanAccessor accessor = new JavaBeanAccessor(new Point(3, "p"));

    assertEquals(3, accessor.getValue("x"));
    assertEquals("p", accessor.getValue("label"));
    assertThrows(BeanMapException.class, () -> accessor.getValue("missing"));
  }

  @Test
  void recordCanDriveMapAll() {
    PointDomain domain = new PointDomain();
    BeanMapper.of(domain, new JavaBeanAccessor(new Point(4, "q"))).mapAll().apply();

    assertEquals(4, domain.getX());
    assertEquals("q", domain.getLabel());
  }

  @Test
  void mapAccessorIsThreeState() {
    MapAccessor accessor = new MapAccessor(UserDto.class, payload("name", "Ada", "email", null));

    assertTrue(accessor.isProvided("name"));
    assertTrue(accessor.isProvided("email"));
    assertFalse(accessor.isProvided("age"));
    assertEquals("Ada", accessor.getValue("name"));
    assertNull(accessor.getValue("email"));
    assertNull(accessor.getValue("age"));
    assertEquals(UserDto.class, accessor.type());
  }

  @Test
  void mapAccessorResolvesDottedPath() {
    MapAccessor accessor = new MapAccessor(UserDto.class,
            payload("profile", payload("contact", payload("userNo", "u1"))));

    assertTrue(accessor.isProvided("profile.contact.userNo"));
    assertFalse(accessor.isProvided("profile.contact.missing"));
    assertEquals("u1", accessor.getValue("profile.contact.userNo"));
  }

  @Test
  void nonJavaBeanShapesFailFast() {
    assertThrows(BeanMapException.class, () -> new JavaBeanAccessor(Map.of("a", 1)));
    assertThrows(BeanMapException.class, () -> new JavaBeanAccessor("text"));
    assertThrows(BeanMapException.class, () -> new JavaBeanAccessor(42));
    assertThrows(BeanMapException.class, () -> new JavaBeanAccessor(new int[]{1}));
    assertThrows(BeanMapException.class, () -> new JavaBeanAccessor(Kind.ONE));
    assertThrows(NullPointerException.class, () -> new JavaBeanAccessor(null));
  }
}
