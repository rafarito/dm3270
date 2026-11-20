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

// Descobre e carrega JARs de plugins com um class loader por JAR.
// Usa o logger de PluginsStage porque logback.xml imprime o nome da classe nos logs.
// -----------------------------------------------------------------------------------//
public class PluginJars
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (PluginsStage.class);

  private final Path directory;

  // Fallback compartilhado para JARs de biblioteca soltos na pasta de plugins.
  private URLClassLoader allJars;

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

  // Carrega cada candidato pelo loader do proprio JAR para evitar colisoes de nomes.
  // Ignora entradas com '$' (classes aninhadas) por compatibilidade com o legado.
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

  // Usa findResource para checar so as URLs do proprio JAR (sem pai nem siblings),
  // garantindo que o plugin seja carregado pelo loader do JAR que o contem.
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

  // Fecha todos mesmo se algum falhar, para nao deixar JARs bloqueados no Windows.
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

  // Ordem de busca: pai (API do app) -> proprio JAR -> siblings (libs compartilhadas).
  // Buscar no proprio JAR antes de siblings evita colisoes de classes entre plugins.
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
