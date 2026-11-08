/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap.accessor;

import cn.labzen.beanmap.exception.*;

import java.util.Map;
import java.util.Objects;

/**
 * 以 {@link Map} 为负载的源访问器：按 key 是否存在判定 presence，天然支持三态。
 *
 * <p>
 * {@code type} 指定描述字段的 DTO 类型，供字段声明、注解规则与覆盖度断言解析； {@code data} 提供本次负载的
 * key 集合与实际值。点号路径按 Map 层级逐段下钻。
 */
public class MapAccessor implements SourceAccessor {

  private final Class<?> type;
  private final Map<String, Object> data;

  public MapAccessor(Class<?> type, Map<String, Object> data) {
    this.type = Objects.requireNonNull(type, "字段描述类型不能为空");
    this.data = Objects.requireNonNull(data, "Map 负载不能为空");
  }

  @Override
  public boolean isProvided(String path) {
    Object current = data;
    for (String segment : segments(path)) {
      if (!(current instanceof Map<?, ?> map) || !map.containsKey(segment)) {
        return false;
      }
      current = map.get(segment);
    }
    return true;
  }

  @Override
  public Object getValue(String path) {
    Object current = data;
    for (String segment : segments(path)) {
      if (current == null) {
        return null;
      }
      if (!(current instanceof Map<?, ?> map)) {
        throw new BeanMapException("Map 负载路径 {} 的段 {} 不是 Map：{}",
                path, segment, current.getClass().getSimpleName());
      }
      current = map.get(segment);
    }
    return current;
  }

  @Override
  public Class<?> type() {
    return type;
  }

  private static String[] segments(String path) {
    return path.split("\\.");
  }
}
