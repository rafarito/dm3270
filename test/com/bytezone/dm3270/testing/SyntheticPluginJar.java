package com.bytezone.dm3270.testing;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

// Compila e empacota plugins de teste em JARs usando CodeSource das dependencias como classpath
// (java.class.path no Surefire traz apenas o jar de boot devido a useManifestOnlyJar).
// -----------------------------------------------------------------------------------//
public final class SyntheticPluginJar
// -----------------------------------------------------------------------------------//
{
  private final Map<String, String> sources = new LinkedHashMap<> ();

  // ---------------------------------------------------------------------------------//
  public SyntheticPluginJar add (String className, String source)
  // ---------------------------------------------------------------------------------//
  {
    sources.put (className, source);
    return this;
  }

  // ---------------------------------------------------------------------------------//
  public Path writeTo (Path directory, String jarName) throws IOException
  // ---------------------------------------------------------------------------------//
  {
    return writeTo (directory, jarName, new String[0]);
  }

  // Compila todas as classes juntas, mas empacota apenas classNames (ou todas, se vazio).
  // ---------------------------------------------------------------------------------//
  public Path writeTo (Path directory, String jarName, String... classNames)
      throws IOException
  // ---------------------------------------------------------------------------------//
  {
    Path work = Files.createTempDirectory ("synthetic-plugin");
    Path sourceRoot = Files.createDirectories (work.resolve ("src"));
    Path classRoot = Files.createDirectories (work.resolve ("classes"));

    List<String> files = new ArrayList<> ();
    for (Map.Entry<String, String> entry : sources.entrySet ())
    {
      Path file = sourceRoot.resolve (entry.getKey ().replace ('.', '/') + ".java");
      Files.createDirectories (file.getParent ());
      Files.writeString (file, entry.getValue ());
      files.add (file.toString ());
    }

    compile (files, classRoot);

    Path jar = directory.resolve (jarName);
    try (JarOutputStream out = new JarOutputStream (Files.newOutputStream (jar)))
    {
      try (Stream<Path> walk = Files.walk (classRoot))
      {
        walk.filter (path -> path.toString ().endsWith (".class")).forEach (path ->
        {
          try
          {
            String name = classRoot.relativize (path).toString ().replace (File.separatorChar,
                '/');
            if (!wanted (name, classNames))
              return;
            out.putNextEntry (new JarEntry (name));
            copy (path, out);
            out.closeEntry ();
          }
          catch (IOException e)
          {
            throw new UncheckedIOException (e);
          }
        });
      }
    }

    return jar;
  }

  // ---------------------------------------------------------------------------------//
  private static boolean wanted (String entryName, String[] classNames)
  // ---------------------------------------------------------------------------------//
  {
    if (classNames.length == 0)
      return true;

    for (String className : classNames)
    {
      String prefix = className.replace ('.', '/');
      if (entryName.equals (prefix + ".class") || entryName.startsWith (prefix + "$"))
        return true;
    }

    return false;
  }

  // ---------------------------------------------------------------------------------//
  private static void copy (Path path, OutputStream out) throws IOException
  // ---------------------------------------------------------------------------------//
  {
    out.write (Files.readAllBytes (path));
  }

  // Falha explicitamente em vez de pular com assumeTrue se rodar sem JDK.
  // ---------------------------------------------------------------------------------//
  private static void compile (List<String> files, Path classRoot)
  // ---------------------------------------------------------------------------------//
  {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler ();
    if (compiler == null)
      throw new IllegalStateException (
          "nao ha compilador Java nesta JVM - a suite precisa rodar sobre um JDK, nao um JRE."
              + " Sem ele a rede que prova a Regra 3 nao existe.");

    List<String> options = new ArrayList<> (
        List.of ("-classpath", compileClasspath (), "-d", classRoot.toString ()));
    options.addAll (files);

    int result = compiler.run (null, null, null, options.toArray (new String[0]));
    if (result != 0)
      throw new IllegalStateException (
          "o plugin sintetico nao compilou - veja a saida do javac acima");
  }

  // ---------------------------------------------------------------------------------//
  private static String compileClasspath ()
  // ---------------------------------------------------------------------------------//
  {
    return Stream
        .of (com.bytezone.dm3270.plugins.Plugin.class, SyntheticPluginJar.class,
             org.slf4j.LoggerFactory.class, javafx.beans.property.SimpleStringProperty.class)
        .map (SyntheticPluginJar::locationOf).distinct ()
        .collect (Collectors.joining (File.pathSeparator));
  }

  // ---------------------------------------------------------------------------------//
  private static String locationOf (Class<?> type)
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      return new File (type.getProtectionDomain ().getCodeSource ().getLocation ().toURI ())
          .getAbsolutePath ();
    }
    catch (URISyntaxException e)
    {
      throw new IllegalStateException ("nao consegui localizar " + type.getName (), e);
    }
  }
}
