/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.convert.support.GenericConversionService;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.labzen.beanmap.TestFixtures.User;
import static cn.labzen.beanmap.TestFixtures.UserDto;
import static cn.labzen.beanmap.TestFixtures.payload;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElementMappingTest {

  public static class ListDto {
    private List<String> names;
    private Set<String> tags;
    private Map<String, Integer> scores;
    private String[] codes;
    private List<Src> items;
  }

  public static class ListDomain {
    private List<String> names;
    private Set<String> tags;
    private Map<String, String> scores;
    private List<String> codes;
    private List<Dst> items;

    public List<String> getNames() { return names; }
    public void setNames(List<String> v) { names = v; }
    public Set<String> getTags() { return tags; }
    public void setTags(Set<String> v) { tags = v; }
    public Map<String, String> getScores() { return scores; }
    public void setScores(Map<String, String> v) { scores = v; }
    public List<String> getCodes() { return codes; }
    public void setCodes(List<String> v) { codes = v; }
    public List<Dst> getItems() { return items; }
    public void setItems(List<Dst> v) { items = v; }
  }

  public static class Src {
    private String name = "n";

    public String getName() { return name; }
  }

  public static class Dst {
    private String name;

    public String getName() { return name; }
    public void setName(String v) { name = v; }
  }

  private static MapAccessor source(Object... pairs) {
    return new MapAccessor(ListDto.class, payload(pairs));
  }

  @Test
  void elementFunctionConvertsList() {
    ListDomain domain = new ListDomain();
    BeanMapper.of(domain, source("names", List.of("a", "b")))
            .map("names", ListDomain::setNames).element(v -> "[" + v + "]").apply();

    assertEquals(List.of("[a]", "[b]"), domain.getNames());
  }

  @Test
  void ignoreNullElementSkipsNullItems() {
    ListDomain domain = new ListDomain();
    BeanMapper.of(domain, source("names", Arrays.asList("a", null, "b")))
            .map("names", ListDomain::setNames).element(v -> v).ignoreNullElement().apply();

    assertEquals(List.of("a", "b"), domain.getNames());
  }

  @Test
  void mapValuesAreConvertedAndKeysKept() {
    ListDomain domain = new ListDomain();
    BeanMapper.of(domain, source("scores", Map.of("math", 90)))
            .map("scores", ListDomain::setScores).element(v -> "v" + v).apply();

    assertEquals(Map.of("math", "v90"), domain.getScores());
  }

  @Test
  void arrayIsReturnedAsList() {
    ListDomain domain = new ListDomain();
    BeanMapper.of(domain, source("codes", new String[]{"c1", "c2"}))
            .map("codes", ListDomain::setCodes).element(v -> "[" + v + "]").apply();

    assertEquals(List.of("[c1]", "[c2]"), domain.getCodes());
  }

  @Test
  void setKeepsSetSemantics() {
    ListDomain domain = new ListDomain();
    BeanMapper.of(domain, source("tags", new LinkedHashSet<>(List.of("t1", "t2"))))
            .map("tags", ListDomain::setTags).element(v -> "[" + v + "]").apply();

    assertTrue(domain.getTags() instanceof Set<?>);
    assertEquals(new LinkedHashSet<>(List.of("[t1]", "[t2]")), domain.getTags());
  }

  @Test
  void classBasedElementUsesInstantiationStrategy() {
    ListDomain domain = new ListDomain();
    BeanMapper.of(domain, source("items", List.of(new Src())))
            .map("items", ListDomain::setItems).element(Src.class, Dst.class).apply();

    assertEquals(1, domain.getItems().size());
    assertEquals("n", domain.getItems().get(0).getName());
  }

  @Test
  void conversionServiceIsScopedToSingleChain() {
    GenericConversionService custom = new GenericConversionService();
    DefaultConversionService.addDefaultConverters(custom);
    custom.addConverter(String.class, Integer.class, s -> 999);

    User one = new User();
    BeanMapper.of(one, new MapAccessor(UserDto.class, payload("age", "7")))
            .withConversionService(custom).map("age", User::setAge).apply();
    assertEquals(999, one.getAge());

    User two = new User();
    BeanMapper.of(two, new MapAccessor(UserDto.class, payload("age", "7")))
            .map("age", User::setAge).apply();
    assertEquals(7, two.getAge());
  }
}
