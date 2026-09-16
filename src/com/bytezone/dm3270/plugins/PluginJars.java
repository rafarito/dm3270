package com.bytezone.dm3270.plugins;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Os JARs da pasta de plugins: listar, montar um class loader por JAR, descobrir quem
 * implementa Plugin, carregar uma classe pelo loader do JAR de onde ela veio, e fechar tudo
 * no fim.
 *
 * Saiu do PluginsStage no passo 9. A classe de la e uma javafx.stage.Stage - uma janela de
 * preferencias com dez linhas de formulario - e era tambem dona de um URLClassLoader, de uma
 * varredura de diretorio e de reflexao sobre bytecode de terceiros. Nada disto precisa de
 * interface grafica, e e ZERO JavaFX: esta classe roda headless e tem teste proprio.
 *
 * O PluginsStage ficou com o que de fato e dele - o formulario, as preferencias, o menu e o
 * despacho para os plugins.
 *
 * O LOGGER E O DO PluginsStage, DE PROPOSITO. O logback.xml imprime %logger{36}, entao o nome
 * do logger e saida observavel: as oito mensagens desta classe - a pasta criada, os JARs
 * carregados, os plugins descobertos, os erros - sempre sairam com o nome daquela classe, e
 * declarar PluginJars.class mudaria todas elas. E a mesma regra que fez DatasetDetails
 * declarar o logger de ScreenWatcher e as seis classes novas de database declararem o de
 * DatabaseThread.
 */
// -----------------------------------------------------------------------------------//
public class PluginJars
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (PluginsStage.class);

  private final Path directory;

  // O loader combinado, com todos os JARs. Ele NAO carrega os plugins - cada um tem o seu -
  // mas continua existindo como ultimo recurso para uma classe que nao esteja no JAR do
  // plugin: e o caso de um JAR de biblioteca solto na pasta, que sempre funcionou.
  private URLClassLoader allJars;

  // Um loader POR JAR, na ordem em que o diretorio os lista.
  private final List<JarClassLoader> jarClassLoaders = new ArrayList<> ();

  // ---------------------------------------------------------------------------------//
  public PluginJars (Path directory)
  // ---------------------------------------------------------------------------------//
  {
    this.directory = directory;
    build ();
  }

  // ---------------------------------------------------------------------------------//
  public Path getDirectory ()
  // ---------------------------------------------------------------------------------//
  {
    return directory;
  }

  // ---------------------------------------------------------------------------------//
  private void build ()
  // ---------------------------------------------------------------------------------//
  {
    if (!Files.isDirectory (directory))
    {
      try
      {
        Files.createDirectories (directory);
        logger.info ("Created plugins directory: {}", directory);
      }
      catch (IOException e)
      {
        logger.error ("Could not create plugins directory", e);
        return;
      }
    }

    File[] jarFiles = directory.toFile ().listFiles (
        (dir, name) -> name.toLowerCase ().endsWith (".jar"));

    if (jarFiles == null || jarFiles.length == 0)
    {
      logger.info ("No plugin JARs found in: {}", directory);
      return;
    }

    try
    {
      URL[] urls = new URL[jarFiles.length];
      for (int i = 0; i < jarFiles.length; i++)
      {
        urls[i] = jarFiles[i].toURI ().toURL ();
        logger.info ("Loaded plugin JAR: {}", jarFiles[i].getName ());
      }

      allJars = new URLClassLoader (urls, getClass ().getClassLoader ());

      for (int i = 0; i < jarFiles.length; i++)
        jarClassLoaders.add (
            new JarClassLoader (jarFiles[i], urls[i], getClass ().getClassLoader (), allJars));
    }
    catch (IOException e)
    {
      logger.error ("Error loading plugin JARs", e);
    }
  }

  /*
   * Varre os JARs procurando classes que implementem Plugin, e devolve pares
   * { nome simples, nome qualificado }.
   *
   * Cada classe e carregada pelo loader do JAR DE ONDE ELA VEIO. Carregar tudo por um loader
   * unico aqui dentro reintroduziria a colisao de nome qualificado que o loader por JAR
   * existe para tirar.
   *
   * Entradas com cifrao sao puladas, entao um plugin declarado como classe aninhada e
   * invisivel para a descoberta. Isso e comportamento antigo, congelado em teste.
   */
  // ---------------------------------------------------------------------------------//
  public List<String[]> discover ()
  // ---------------------------------------------------------------------------------//
  {
    List<String[]> discovered = new ArrayList<> ();

    for (JarClassLoader jarClassLoader : jarClassLoaders)
    {
      File jarFile = directory.resolve (jarClassLoader.getName ()).toFile ();

      try (JarFile jar = new JarFile (jarFile))
      {
        Enumeration<JarEntry> entries = jar.entries ();
        while (entries.hasMoreElements ())
        {
          JarEntry entry = entries.nextElement ();
          String entryName = entry.getName ();

          if (!entryName.endsWith (".class") || entryName.contains ("$"))
            continue;

          String className =
              entryName.replace ('/', '.').substring (0, entryName.length () - 6);

          try
          {
            Class<?> c = jarClassLoader.loadClass (className);
            if (Plugin.class.isAssignableFrom (c) && !c.isInterface ()
                && !java.lang.reflect.Modifier.isAbstract (c.getModifiers ()))
            {
              discovered.add (new String[] { c.getSimpleName (), className });
              logger.info ("Discovered plugin: {}", className);
            }
          }
          catch (ClassNotFoundException | LinkageError e)
          {
            // class cannot be loaded, skip it
          }
        }
      }
      catch (IOException e)
      {
        logger.error ("Error scanning JAR: {}", jarFile.getName (), e);
      }
    }

    return discovered;
  }

  /*
   * Carrega uma classe pelo loader do JAR que REALMENTE a contem, ou devolve null.
   *
   * A pergunta e feita com findResource, que num URLClassLoader procura apenas nas URLs dele
   * - sem o pai e sem o combinado. E o que distingue "esta classe esta neste JAR" de "esta
   * classe e alcancavel a partir deste JAR": a segunda e verdadeira para todos os loaders, e
   * escolher por ela poria o plugin de um JAR para rodar sob o loader de outro, desfazendo a
   * correcao sem que nada acusasse.
   */
  // ---------------------------------------------------------------------------------//
  public Class<?> loadFromOwningJar (String className)
  // ---------------------------------------------------------------------------------//
  {
    String resource = className.replace ('.', '/') + ".class";

    for (JarClassLoader jarClassLoader : jarClassLoaders)
      if (jarClassLoader.findResource (resource) != null)
        try
        {
          return jarClassLoader.loadClass (className);
        }
        catch (ClassNotFoundException e)
        {
          // o recurso esta la mas a classe nao carrega - tenta o proximo
        }

    return null;
  }

  /*
   * Fecha todos os loaders. Acumular os erros em vez de parar no primeiro importa porque no
   * Windows um loader que fique aberto mantem o JAR mapeado, e o proximo lancamento nao
   * consegue substituir o arquivo.
   */
  // ---------------------------------------------------------------------------------//
  public void close ()
  // ---------------------------------------------------------------------------------//
  {
    for (JarClassLoader jarClassLoader : jarClassLoaders)
      close (jarClassLoader);

    close (allJars);
  }

  // ---------------------------------------------------------------------------------//
  private static void close (URLClassLoader classLoader)
  // ---------------------------------------------------------------------------------//
  {
    if (classLoader == null)
      return;

    try
    {
      classLoader.close ();
    }
    catch (IOException e)
    {
      logger.error ("Error closing class loader", e);
    }
  }

  /*
   * UM CLASS LOADER POR JAR, e a ordem de busca dele e o coracao da correcao do passo 9:
   *
   *   1. o pai, que e o class loader da aplicacao - de la vem Plugin, PluginData e tudo mais
   *      do dm3270, sempre, para todos os plugins;
   *   2. o PROPRIO JAR;
   *   3. so entao o loader combinado, com todos os JARs da pasta.
   *
   * O passo 2 antes do 3 e o que resolve a colisao: duas copias de uma classe com o mesmo
   * nome qualificado deixam de disputar, porque cada plugin encontra a sua antes de chegar ao
   * monte comum. Antes havia um loader so, e a primeira definicao encontrada valia para
   * todos - com o desempate decidido pela ordem de listagem do diretorio.
   *
   * E o passo 3 existe para nao quebrar o que ja funcionava: um JAR de plugin que dependa de
   * um JAR de biblioteca solto na mesma pasta continua achando a biblioteca, e continua
   * compartilhando UMA copia dela com os demais plugins - porque quem a define e o loader
   * combinado, nao este.
   */
  // ---------------------------------------------------------------------------------//
  private static final class JarClassLoader extends URLClassLoader
  // ---------------------------------------------------------------------------------//
  {
    private final URLClassLoader siblings;

    JarClassLoader (File jarFile, URL url, ClassLoader parent, URLClassLoader siblings)
    {
      super (jarFile.getName (), new URL[] { url }, parent);
      this.siblings = siblings;
    }

    @Override
    protected Class<?> findClass (String name) throws ClassNotFoundException
    {
      try
      {
        return super.findClass (name);
      }
      catch (ClassNotFoundException e)
      {
        return siblings.loadClass (name);
      }
    }
  }
}
