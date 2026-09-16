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

/*
 * Compila um plugin no meio do teste e o empacota num JAR de verdade.
 *
 * E o que torna a Regra 3 - "JARs de terceiros ja compilados tem de continuar carregando" -
 * uma porta de build a cada commit, em vez de um ritual manual no fim da onda. O JAR sai num
 * @TempDir, e carregado pelo proprio PluginsStage e passa por descoberta, instanciacao e
 * despacho como qualquer outro.
 *
 * ATENCAO AO CLASSPATH DE COMPILACAO, que e a armadilha desta classe. NAO se pode montar o
 * classpath a partir de System.getProperty ("java.class.path"): o Surefire roda com
 * useManifestOnlyJar ligado por padrao, e aquela propriedade contem apenas o jar de boot -
 * o javac nao enxergaria nem o Plugin nem o DefaultPlugin, e a mensagem de erro seria um
 * "cannot find symbol" sem explicacao. O classpath e montado a partir do CodeSource de
 * classes ancora, que e onde elas realmente estao.
 *
 * As quatro ancoras nao sao arbitrarias: target/classes pelo Plugin, target/test-classes
 * pelo PluginProbe, o slf4j porque DefaultPlugin guarda um Logger, e o javafx.base porque
 * PluginField importa javafx.beans.property - a API de plugins ja nasceu com JavaFX dentro, e
 * isso nao muda neste passo.
 */
// -----------------------------------------------------------------------------------//
public final class SyntheticPluginJar
// -----------------------------------------------------------------------------------//
{
  private final Map<String, String> sources = new LinkedHashMap<> ();

  /*
   * Acrescenta uma classe ao JAR. O nome tem de bater com o package + class do fonte.
   */
  // ---------------------------------------------------------------------------------//
  public SyntheticPluginJar add (String className, String source)
  // ---------------------------------------------------------------------------------//
  {
    sources.put (className, source);
    return this;
  }

  /*
   * Empacota TODAS as classes compiladas.
   */
  // ---------------------------------------------------------------------------------//
  public Path writeTo (Path directory, String jarName) throws IOException
  // ---------------------------------------------------------------------------------//
  {
    return writeTo (directory, jarName, new String[0]);
  }

  /*
   * Empacota so as classes nomeadas, embora todas sejam compiladas juntas.
   *
   * Serve para montar um JAR de plugin que DEPENDE de uma classe que mora noutro JAR da mesma
   * pasta - o caso da biblioteca solta, que o terceiro passo da busca do JarClassLoader
   * existe para atender. Sem isto nao ha como escrever esse teste: o javac precisa das duas
   * classes juntas, e o empacotamento precisa delas separadas.
   */
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

  /*
   * Lista vazia significa "todas". Um nome casa a propria classe e as aninhadas dela.
   */
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

  /*
   * getSystemJavaCompiler () devolve null quando a suite roda sobre um JRE em vez de um JDK.
   * Isso FALHA ALTO de proposito, em vez de virar um assumeTrue: pular a rede que prova a
   * compatibilidade de JARs antigos numa maquina mal configurada e exatamente o que a
   * Regra 5 proibe - a rede nao se afrouxa para caber no ambiente.
   */
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
