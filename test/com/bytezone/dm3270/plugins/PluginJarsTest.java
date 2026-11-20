package com.bytezone.dm3270.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bytezone.dm3270.testing.SyntheticPluginJar;

// -----------------------------------------------------------------------------------//
@DisplayName ("PluginJars - os JARs da pasta de plugins, sem janela")
class PluginJarsTest
// -----------------------------------------------------------------------------------//
{
  @TempDir
  private Path directory;

  private PluginJars jars;

  // Obrigatorio no Windows: URLClassLoader aberto impede a remocao do @TempDir pelo JUnit.
  // ---------------------------------------------------------------------------------//
  @AfterEach
  void closeLoaders ()
  // ---------------------------------------------------------------------------------//
  {
    if (jars != null)
      jars.close ();
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("A pasta")
  class Directory
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("uma pasta vazia nao descobre nada e nao quebra")
    void pastaVaziaNaoDescobreNada ()
    // -------------------------------------------------------------------------------//
    {
      jars = new PluginJars (directory);

      assertEquals (List.of (), jars.discover ());
      assertNull (jars.loadFromOwningJar ("com.example.QualquerCoisa"));
      assertEquals (directory, jars.getDirectory ());
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("so a extensao .jar entra, mesmo que o arquivo seja um JAR valido")
    void soAExtensaoJarEntra () throws Exception
    // -------------------------------------------------------------------------------//
    {
      writePlugin ("com.example.um", "Primeiro", "Primeiro.jar");
      writePlugin ("com.example.dois", "Segundo", "Segundo.zip");
      Files.writeString (directory.resolve ("LEIAME.txt"), "isto nao e um JAR");

      jars = new PluginJars (directory);
      List<String[]> discovered = jars.discover ();

      assertEquals (1, discovered.size ());
      assertEquals ("com.example.um.Primeiro", discovered.get (0)[1]);
      assertNull (jars.loadFromOwningJar ("com.example.dois.Segundo"));
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("uma pasta que nao existe e criada")
    void pastaInexistenteECriada ()
    // -------------------------------------------------------------------------------//
    {
      Path nova = directory.resolve ("ainda-nao-existe");

      jars = new PluginJars (nova);

      assertTrue (Files.isDirectory (nova));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("A descoberta")
  class Discovery
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("acha quem implementa Plugin, com nome simples e qualificado")
    void achaQuemImplementaPlugin () throws Exception
    // -------------------------------------------------------------------------------//
    {
      writePlugin ("com.example.um", "Primeiro", "Primeiro.jar");

      jars = new PluginJars (directory);
      List<String[]> discovered = jars.discover ();

      assertEquals (1, discovered.size ());
      assertEquals ("Primeiro", discovered.get (0)[0]);
      assertEquals ("com.example.um.Primeiro", discovered.get (0)[1]);
    }

    // Entradas com '$' (classes aninhadas) sao ignoradas intencionalmente na varredura.
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("ignora quem nao e Plugin e ignora classe aninhada")
    void ignoraQuemNaoEPluginEAninhada () throws Exception
    // -------------------------------------------------------------------------------//
    {
      new SyntheticPluginJar ()
          .add ("com.example.fora.Comum", """
              package com.example.fora;

              public class Comum
              {
                public Comum () { }

                public static class Dentro implements com.bytezone.dm3270.plugins.Plugin
                {
                  public Dentro () { }
                }
              }
              """)
          .writeTo (directory, "Fora.jar");

      jars = new PluginJars (directory);

      assertEquals (List.of (), jars.discover ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("O loader de cada JAR")
  class Loaders
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("cada plugin resolve a copia que veio no proprio JAR")
    void cadaPluginResolveACopiaDoProprioJar () throws Exception
    // -------------------------------------------------------------------------------//
    {
      writeStamped ("a", "A");
      writeStamped ("b", "B");

      jars = new PluginJars (directory);

      Class<?> pluginA = jars.loadFromOwningJar ("com.example.a.PluginA");
      Class<?> pluginB = jars.loadFromOwningJar ("com.example.b.PluginB");

      assertNotSame (pluginA.getClassLoader (), pluginB.getClassLoader ());
      assertEquals ("A", stampSeenBy (pluginA));
      assertEquals ("B", stampSeenBy (pluginB));
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("uma classe que nao esta em JAR nenhum devolve null")
    void classeAusenteDevolveNull () throws Exception
    // -------------------------------------------------------------------------------//
    {
      writePlugin ("com.example.um", "Primeiro", "Primeiro.jar");

      jars = new PluginJars (directory);

      assertNull (jars.loadFromOwningJar ("com.example.um.NaoExiste"));
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("um plugin acha a biblioteca solta noutro JAR da pasta")
    void pluginAchaBibliotecaSolta () throws Exception
    // -------------------------------------------------------------------------------//
    {
      SyntheticPluginJar sources = new SyntheticPluginJar ()
          .add ("com.example.lib.Greeting", """
              package com.example.lib;

              public class Greeting
              {
                public static String text ()
                {
                  return "biblioteca";
                }
              }
              """)
          .add ("com.example.user.PluginUser", """
              package com.example.user;

              import com.bytezone.dm3270.plugins.Plugin;

              public class PluginUser implements Plugin
              {
                public PluginUser () { }

                public static String greeting ()
                {
                  return com.example.lib.Greeting.text ();
                }
              }
              """);

      sources.writeTo (directory, "lib.jar", "com.example.lib.Greeting");
      sources.writeTo (directory, "PluginUser.jar", "com.example.user.PluginUser");

      jars = new PluginJars (directory);
      Class<?> plugin = jars.loadFromOwningJar ("com.example.user.PluginUser");

      assertEquals ("biblioteca", plugin.getMethod ("greeting").invoke (null));
    }
  }

  // ---------------------------------------------------------------------------------//
  private void writePlugin (String pkg, String name, String jarName) throws Exception
  // ---------------------------------------------------------------------------------//
  {
    new SyntheticPluginJar ().add (pkg + "." + name, """
        package %s;

        import com.bytezone.dm3270.plugins.Plugin;

        public class %s implements Plugin
        {
          public %s () { }
        }
        """.formatted (pkg, name, name)).writeTo (directory, jarName);
  }

  // ---------------------------------------------------------------------------------//
  private void writeStamped (String suffix, String letter) throws Exception
  // ---------------------------------------------------------------------------------//
  {
    String plugin = "Plugin" + letter;

    new SyntheticPluginJar ()
        .add ("com.example.shared.Stamp", """
            package com.example.shared;

            public class Stamp
            {
              public static String mark ()
              {
                return "%s";
              }
            }
            """.formatted (letter))
        .add ("com.example." + suffix + "." + plugin, """
            package com.example.%s;

            import com.bytezone.dm3270.plugins.Plugin;

            public class %s implements Plugin
            {
              public %s () { }
            }
            """.formatted (suffix, plugin, plugin))
        .writeTo (directory, plugin + ".jar");
  }

  // Usa o class loader do plugin para reproduzir a resolucao de simbolos feita pela JVM.
  // ---------------------------------------------------------------------------------//
  private static String stampSeenBy (Class<?> plugin) throws Exception
  // ---------------------------------------------------------------------------------//
  {
    Class<?> stamp =
        Class.forName ("com.example.shared.Stamp", true, plugin.getClassLoader ());

    return (String) stamp.getMethod ("mark").invoke (null);
  }
}
