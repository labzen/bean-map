/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

import java.io.Writer;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * 处理 {@link BeanMap} 注解，为映射接口生成实现类（{@code *BeanMapImpl}）。
 *
 * <p>
 * 生成规则：
 * <ul>
 * <li>{@code toDomain(Dto)} —— 创建全量映射：new 出 domain 后同名直写（{@code BeanMapper#mapAll()}）；</li>
 * <li>{@code updateDomain(Dto, @BeanTarget Domain)} —— 编辑映射：对传入 domain 就地同名直写；</li>
 * <li>{@code copyDto(Dto, Dto)} —— DTO 同类型拷贝：{@link BeanReader#read(Object, Object)} 同名直读。</li>
 * <li>{@code toDto(Domain)} / {@code toChain(Domain)} —— 读方向装配：{@link BeanReader#read(Object, Object)}
 * 同名直读（{@code mapAll()}） + 方法上的 {@link BeanMapField} 显式映射（重命名 / 转换 / 忽略），二者并存。</li>
 * </ul>
 *
 * <p>
 * 生成代码统一委托给编程式 {@link BeanMapper} / {@link BeanReader}（字段管线、三态门控、 {@code @BeanUnmapped}/{@code @BeanClearable}
 * 注解规则均由其承载），因此本处理器<b>不读取 domain/DTO 的字段</b>， 只解析映射接口的方法签名与 {@link BeanMapField} 声明，无需与 Lombok 抢占字段生成顺序。
 *
 * <p>
 * 生成类的注册方式由编译器选项 {@code -Abeanmap.registry} 决定：{@code spring}（默认）生成带 {@code @Component} 的实现；
 * {@code spi} 生成不带注册注解的普通类，由使用方按自己的容器注册。库不提供全局注册表，也不做 {@code ServiceLoader} 自动加载。
 *
 * @since 2026-11-07
 */
@SupportedAnnotationTypes(BeanMapProcessor.BEAN_MAP_PKG + ".BeanMap")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedOptions("beanmap.registry")
public class BeanMapProcessor extends AbstractProcessor {

  static final String BEAN_MAP_PKG = "cn.labzen.beanmap";

  private static final String OPTION_REGISTRY = "beanmap.registry";
  private static final String REGISTRY_SPRING = "spring";
  private static final String REGISTRY_SPI = "spi";
  private static final String SPRING_COMPONENT = "org.springframework.stereotype.Component";

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation : annotations) {
      for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
        if (element instanceof TypeElement iface) {
          generate(iface);
        }
      }
    }
    return true;
  }

  private void generate(TypeElement iface) {
    String ifaceName = iface.getSimpleName().toString();
    String pkg = processingEnv.getElementUtils().getPackageOf(iface).getQualifiedName().toString();
    String implName = ifaceName + "Impl";

    StringBuilder body = new StringBuilder();
    for (Element enclosed : iface.getEnclosedElements()) {
      if (!(enclosed instanceof ExecutableElement method)) {
        continue;
      }
      Set<Modifier> modifiers = method.getModifiers();
      if (modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.DEFAULT)) {
        continue;
      }
      try {
        body.append(generateMethod(method));
      } catch (RuntimeException e) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "BeanMapProcessor 处理方法 " + method.getSimpleName() + " 失败：" + e, method);
      }
    }

    boolean spring = REGISTRY_SPRING.equals(registry());
    String componentImport = spring ? "import " + SPRING_COMPONENT + ";\n" : "";
    String componentAnnotation = spring ? "@Component\n" : "";
    String src = """
            package %s;

            import %s.BeanMapper;
            import %s.BeanReader;
            import %s.JavaBeanAccessor;
            %s
            %spublic class %s implements %s {
            %s
            }
            """.formatted(pkg, BEAN_MAP_PKG, BEAN_MAP_PKG, BEAN_MAP_PKG,
            componentImport, componentAnnotation, implName, ifaceName, body.toString());

    try {
      Filer filer = processingEnv.getFiler();
      JavaFileObject file = filer.createSourceFile(pkg + "." + implName, iface);
      try (Writer w = file.openWriter()) {
        w.write(src);
      }
    } catch (Exception e) {
      processingEnv.getMessager().printMessage(
              Diagnostic.Kind.ERROR, "BeanMapProcessor 生成 " + implName + " 失败：" + e);
    }
  }

  private String registry() {
    String value = processingEnv.getOptions().get(OPTION_REGISTRY);
    if (value == null || value.isBlank() || REGISTRY_SPRING.equals(value)) {
      return REGISTRY_SPRING;
    }
    if (REGISTRY_SPI.equals(value)) {
      return REGISTRY_SPI;
    }
    processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
            "未知的 -A" + OPTION_REGISTRY + " 取值：" + value + "，按 spring 处理");
    return REGISTRY_SPRING;
  }

  private String generateMethod(ExecutableElement method) {
    String name = method.getSimpleName().toString();
    List<? extends VariableElement> params = method.getParameters();
    boolean returnsDomain = method.getReturnType().getKind() != TypeKind.VOID;
    String paramList = params.stream()
            .map(p -> typeName(p.asType()) + " " + p.getSimpleName())
            .collect(Collectors.joining(", "));
    String returnType = returnsDomain ? typeName(method.getReturnType()) : "void";

    VariableElement domainParam = params.stream()
            .filter(p -> p.getAnnotation(BeanTarget.class) != null)
            .findFirst().orElse(null);

    if (domainParam != null && !returnsDomain && params.size() == 2) {
      VariableElement dtoParam = params.stream().filter(p -> p != domainParam).findFirst().orElseThrow();
      String domainName = domainParam.getSimpleName().toString();
      String dtoName = dtoParam.getSimpleName().toString();
      return """
              @Override
              public void %s(%s) {
                BeanMapper.of(%s, new JavaBeanAccessor(%s)).mapAll().apply();
              }

              """.formatted(name, paramList, domainName, dtoName);
    } else if (name.startsWith("toDomain") && params.size() == 1 && returnsDomain) {
      String domainType = typeName(method.getReturnType());
      String dtoName = params.get(0).getSimpleName().toString();
      return """
              @Override
              public %s %s(%s) {
                %s domain = new %s();
                BeanMapper.of(domain, new JavaBeanAccessor(%s)).mapAll().apply();
                return domain;
              }

              """.formatted(domainType, name, paramList, domainType, domainType, dtoName);
    } else if (name.equals("copyDto") && params.size() == 2 && !returnsDomain) {
      String srcName = params.get(0).getSimpleName().toString();
      String tgtName = params.get(1).getSimpleName().toString();
      return """
              @Override
              public void %s(%s) {
                BeanReader.read(%s, %s).mapAll().apply();
              }

              """.formatted(name, paramList, srcName, tgtName);
    } else if (params.size() == 1 && returnsDomain) {
      return generateRead(method, paramList);
    } else {
      return """
              @Override
              public %s %s(%s) {
                throw new UnsupportedOperationException("@BeanMap 暂不支持的方法形态：" + %s);
              }

              """.formatted(returnType, name, paramList, "\"" + name + "\"");
    }
  }

  private String generateRead(ExecutableElement method, String paramList) {
    String name = method.getSimpleName().toString();
    VariableElement domainParam = method.getParameters().get(0);
    String domainType = typeName(domainParam.asType());
    String domainName = domainParam.getSimpleName().toString();
    String retType = typeName(method.getReturnType());

    StringBuilder chain = new StringBuilder();
    BeanMapField[] annos = readBeanMapFields(method);
    for (BeanMapField mf : annos) {
      if (mf.ignore()) {
        String target = mf.target().isEmpty() ? mf.source() : mf.target();
        chain.append("      .ignore(\"").append(target).append("\")\n");
        continue;
      }
      String source = mf.source();
      String target = mf.target().isEmpty() ? mf.source() : mf.target();
      String getter = resolveGetter(domainType, source);
      String setter = retType + "::set" + cap(target);
      String converterType = converterTypeName(mf);
      if (converterType != null) {
        chain.append("      .map(\"").append(target).append("\", ")
                .append(getter).append(", ").append(setter)
                .append(", new ").append(converterType).append("())\n");
      } else {
        chain.append("      .map(\"").append(target).append("\", ")
                .append(getter).append(", ").append(setter).append(")\n");
      }
    }
    chain.append("      .mapAll()\n");

    return """
            @Override
            public %s %s(%s) {
              return BeanReader.read(%s, new %s())
            %s
                  .apply();
            }

            """.formatted(retType, name, paramList, domainName, retType, chain.toString());
  }

  /**
   * 读取 converter 的 Class 成员：编译期类型不可用时代理会抛 MirroredTypeException，从 TypeMirror 取类型名
   */
  private String converterTypeName(BeanMapField mf) {
    try {
      Class<?> converter = mf.converter();
      return void.class.equals(converter) ? null : converter.getName();
    } catch (MirroredTypeException e) {
      return e.getTypeMirror().toString();
    }
  }

  private BeanMapField[] readBeanMapFields(ExecutableElement method) {
    BeanMapFields container = method.getAnnotation(BeanMapFields.class);
    if (container != null) {
      return container.value();
    }
    BeanMapField single = method.getAnnotation(BeanMapField.class);
    return single == null ? new BeanMapField[0] : new BeanMapField[]{single};
  }

  private String cap(String name) {
    if (name.isEmpty()) {
      return name;
    }
    return Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }

  /**
   * 解析读方向显式映射的源 getter 方法引用：boolean 字段（含 {@code isXxx} 命名）用 {@code is} 前缀，其余用 {@code get} 前缀。
   *
   * <p>
   * 领域实体常把 boolean 字段命名为 {@code isXxx}（如 {@code isMobileNumberValidated}），其 getter 是 {@code isMobileNumberValidated()}，
   * 而非 {@code getMobileNumberValidated()} —— 属性名仍是 {@code mobileNumberValidated}。 若不按字段类型选前缀，生成的 {@code get} 引用会编译失败。
   */
  private String resolveGetter(String domainType, String source) {
    TypeElement te = processingEnv.getElementUtils().getTypeElement(domainType);
    if (te != null) {
      String isName = "is" + cap(source);
      for (Element e : te.getEnclosedElements()) {
        if (e instanceof VariableElement f) {
          String fname = f.getSimpleName().toString();
          if (fname.equals(source) || fname.equals(isName)) {
            TypeMirror t = f.asType();
            boolean bool = t.getKind() == TypeKind.BOOLEAN
                    || "java.lang.Boolean".equals(t.toString());
            if (bool) {
              return domainType + "::is" + cap(source);
            }
          }
        }
      }
    }
    return domainType + "::get" + cap(source);
  }

  private String typeName(TypeMirror type) {
    return type.toString();
  }
}
