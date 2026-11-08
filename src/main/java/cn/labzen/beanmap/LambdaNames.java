/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * 方法引用解析：属性名（{@code resource::getEmailAddr} ⇒ {@code emailAddr}）与 setter 的参数类型。
 *
 * <p>
 * 已绑定实例的设值引用（如 {@code user::setEmail} 作为 {@code BiConsumer} 时，编译器会生成合成方法）无法直接取到真实 setter 名， 这类引用须用
 * {@link #propertyName(Serializable, String, String)} 解析，并回退到声明侧属性名（绑定引用下二者同名）。
 */
final class LambdaNames {

  private LambdaNames() {
  }

  /**
   * 属性名：去掉 {@code get} / {@code is} / {@code set} 前缀并首字母小写（取值与赋值的引用都可用）； 方法名无法解析时抛异常
   */
  static String propertyOf(Serializable reference, String usage) {
    String methodName = lambdaOf(reference, usage).getImplMethodName();
    String name = methodName;
    if (name.startsWith("get") && name.length() > 3) {
      name = name.substring(3);
    } else if (name.startsWith("is") && name.length() > 2) {
      name = name.substring(2);
    } else if (name.startsWith("set") && name.length() > 3) {
      name = name.substring(3);
    }
    if (name.isEmpty()) {
      throw new BeanMapException("{}的方法名无法解析属性名：{}", usage, methodName);
    }
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }

  /**
   * 解析属性名：已绑定实例的设值方法引用（如 {@code user::setEmail} 作为 {@code BiConsumer} 时，编译器会生成合成方法，其 方法名不再是真实 setter 名）无法直接取到属性名，此时回退到
   * {@code fallback}（即声明侧属性名，绑定引用下二者必然同名）。
   */
  static String propertyName(Serializable reference, String usage, String fallback) {
    if (isSyntheticReference(reference)) {
      return fallback;
    }
    return propertyOf(reference, usage);
  }

  private static boolean isSyntheticReference(Serializable reference) {
    return lambdaOf(reference, "方法引用").getImplMethodName().startsWith("lambda$");
  }

  /**
   * 方法引用的<b>最后一个</b>参数类型（setter 的写入值类型）；无法判定时为空
   */
  static Optional<Class<?>> parameterTypeOf(Serializable reference, String usage) {
    String descriptor = lambdaOf(reference, usage).getImplMethodSignature();
    int start = descriptor.indexOf('(');
    int end = descriptor.lastIndexOf(')');
    if (start < 0 || end <= start + 1) {
      return Optional.empty();
    }
    String parameters = descriptor.substring(start + 1, end);
    int split = lastParameterStart(parameters);
    return classOf(parameters.substring(split));
  }

  /**
   * 最后一个参数在描述串里的起始下标（参数串形如 {@code Ljava/lang/String;I}）
   */
  private static int lastParameterStart(String parameters) {
    int start = 0;
    int index = 0;
    while (index < parameters.length()) {
      start = index;
      while (index < parameters.length() && parameters.charAt(index) == '[') {
        index++; // 数组：跳过所有维度
      }
      if (index >= parameters.length()) {
        break;
      }
      if (parameters.charAt(index) == 'L') {
        int end = parameters.indexOf(';', index); // 引用类型：跳到类名之后
        if (end < 0) {
          break;
        }
        index = end + 1;
      } else {
        index++; // 基本类型：单字符
      }
    }
    return start;
  }

  private static Optional<Class<?>> classOf(String descriptor) {
    return switch (descriptor) {
      case "Z" -> Optional.of(boolean.class);
      case "B" -> Optional.of(byte.class);
      case "C" -> Optional.of(char.class);
      case "S" -> Optional.of(short.class);
      case "I" -> Optional.of(int.class);
      case "J" -> Optional.of(long.class);
      case "F" -> Optional.of(float.class);
      case "D" -> Optional.of(double.class);
      default -> objectClassOf(descriptor);
    };
  }

  private static Optional<Class<?>> objectClassOf(String descriptor) {
    if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) {
      return Optional.empty();
    }
    try {
      return Optional.of(
              Class.forName(descriptor.substring(1, descriptor.length() - 1).replace('/', '.')));
    } catch (ClassNotFoundException e) {
      return Optional.empty();
    }
  }

  private static SerializedLambda lambdaOf(Serializable reference, String usage) {
    try {
      Method writeReplace = reference.getClass().getDeclaredMethod("writeReplace");
      writeReplace.setAccessible(true);
      Object replacement = writeReplace.invoke(reference);
      if (replacement instanceof SerializedLambda lambda) {
        return lambda;
      }
      throw new BeanMapException("{}必须是对类方法的引用：{}", usage, reference.getClass().getName());
    } catch (ReflectiveOperationException e) {
      throw new BeanMapException(e, "{}必须是对类方法的引用（无法解析属性名）", usage);
    }
  }
}
