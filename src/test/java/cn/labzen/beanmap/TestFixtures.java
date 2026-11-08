/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

import cn.labzen.beanmap.annotation.BeanUnmapped;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TestFixtures {

  private TestFixtures() {
  }

  public static Map<String, Object> payload(Object... pairs) {
    Map<String, Object> data = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      data.put((String) pairs[i], pairs[i + 1]);
    }
    return data;
  }

  public static class UserDto {
    private String name;
    private String email;
    private Integer age;
    @BeanUnmapped
    private String token;

    public String getName() { return name; }
    public String getEmail() { return email; }
    public Integer getAge() { return age; }
    public String getToken() { return token; }
  }

  public static class User {
    private String name = "original";
    private String email;
    private Integer age;
    private String token;

    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { email = v; }
    public Integer getAge() { return age; }
    public void setAge(Integer v) { age = v; }
    public String getToken() { return token; }
    public void setToken(String v) { token = v; }
  }

  public static class PrimitiveDto {
    private int age;
  }

  public record Point(int x, String label) {
  }

  public static class PointDomain {
    private int x;
    private String label;

    public int getX() { return x; }
    public void setX(int v) { x = v; }
    public String getLabel() { return label; }
    public void setLabel(String v) { label = v; }
  }
}
