/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanMapProcessorTest {

  private record Compilation(boolean ok, String generated, String diagnostics) {
  }

  private static String fixture(String methods) {
    return """
            package t;

            import cn.labzen.beanmap.*;
            import java.util.function.Function;

            @BeanMap
            public interface Fixture {
            %s
            }

            class Dto { String name; Integer age; }
            class Domain {
              public void setName(String v) {}
              public void setAge(Integer v) {}
            }
            class Src { public String getName() { return "n"; } }
            class Tgt { public void setName(String v) {} }
            class NameConverter implements Function<String, String> {
              public String apply(String v) { return v; }
            }
            """.formatted(methods);
  }

  private static Compilation compile(String source, String... options) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "APT tests require a JDK");

    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    Path temp = Files.createTempDirectory("beanmap-apt-");
    Path sourceFile = temp.resolve("Fixture.java");
    Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
    Path generatedDir = temp.resolve("generated");
    Files.createDirectories(generatedDir);
    Path classesDir = temp.resolve("classes");
    Files.createDirectories(classesDir);

    String beanMapClasses = Paths.get(BeanMap.class.getProtectionDomain()
            .getCodeSource().getLocation().toURI()).toString();
    String classpath = System.getProperty("java.class.path") + File.pathSeparator + beanMapClasses;

    List<String> args = new ArrayList<>(List.of(
            "-proc:only",
            "-classpath", classpath,
            "-s", generatedDir.toString(),
            "-d", classesDir.toString(),
            "-processor", "cn.labzen.beanmap.BeanMapProcessor"));
    args.addAll(Arrays.asList(options));

    boolean ok;
    try (StandardJavaFileManager files = compiler.getStandardFileManager(
            diagnostics, null, StandardCharsets.UTF_8)) {
      Iterable<? extends JavaFileObject> units = files.getJavaFileObjects(sourceFile.toFile());
      ok = Boolean.TRUE.equals(compiler.getTask(null, files, diagnostics, args, null, units).call());
    }

    Path generatedFile = generatedDir.resolve("t/FixtureImpl.java");
    String generated = Files.exists(generatedFile) ? Files.readString(generatedFile) : "";
    StringBuilder messages = new StringBuilder();
    for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
      messages.append(diagnostic.getKind()).append(": ")
              .append(diagnostic.getMessage(null)).append('\n');
    }
    return new Compilation(ok, generated, messages.toString());
  }

  @Test
  void springModeAddsComponent() throws Exception {
    Compilation result = compile(fixture("  Dto toDto(Domain domain);"), "-Abeanmap.registry=spring");

    assertTrue(result.ok(), result.diagnostics());
    assertTrue(result.generated().contains("@Component"), result.generated());
  }

  @Test
  void spiModeHasNoRegistrationAnnotation() throws Exception {
    Compilation result = compile(fixture("  Dto toDto(Domain domain);"), "-Abeanmap.registry=spi");

    assertTrue(result.ok(), result.diagnostics());
    assertFalse(result.generated().contains("@Component"), result.generated());
    assertTrue(result.generated().contains("public class FixtureImpl"), result.generated());
  }

  @Test
  void converterAnnotationDoesNotBreakProcessing() throws Exception {
    Compilation result = compile(fixture("""
              @BeanMapField(source = "name", target = "name", converter = NameConverter.class)
              Dto toDto(Domain domain);
            """));

    assertTrue(result.ok(), result.diagnostics());
    assertTrue(result.generated().contains("NameConverter"), result.generated());
  }

  @Test
  void updateDomainKeepsDeclaredParameterOrder() throws Exception {
    Compilation result = compile(fixture("  void updateDomain(@BeanTarget Domain domain, Dto dto);"));

    assertTrue(result.ok(), result.diagnostics());
    assertTrue(result.generated().contains("public void updateDomain(t.Domain domain, t.Dto dto)"),
            result.generated());
  }

  @Test
  void copyDtoUsesTargetParameterType() throws Exception {
    Compilation result = compile(fixture("  void copyDto(Src src, Tgt tgt);"));

    assertTrue(result.ok(), result.diagnostics());
    assertTrue(result.generated().contains("public void copyDto(t.Src src, t.Tgt tgt)"), result.generated());
  }

  @Test
  void staticAndDefaultMethodsAreNotOverridden() throws Exception {
    Compilation result = compile(fixture("""
              static String helper() { return "h"; }
              default int rank() { return 1; }
              Dto toDto(Domain domain);
            """));

    assertTrue(result.ok(), result.diagnostics());
    assertFalse(result.generated().contains("helper"), result.generated());
    assertFalse(result.generated().contains("rank"), result.generated());
  }
}
