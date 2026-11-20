package com.bytezone.dm3270.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import com.bytezone.dm3270.testing.JavaFxToolkit;
import com.bytezone.dm3270.testing.SyntheticPluginJar;

// Cada JAR de plugin usa class loader proprio (self-first) para evitar colisao entre JARs.
// -----------------------------------------------------------------------------------//
@ExtendWith (JavaFxToolkit.class)
@DisplayName ("O carregamento de classes de JARs de plugin")
class PluginClassLoadingTest
// -----------------------------------------------------------------------------------//
{
  @TempDir
  private Path pluginsDirectory;

  private Preferences prefs;
  private PluginsStage stage;

  // ---------------------------------------------------------------------------------//
  @BeforeEach
  void createPreferencesNode ()
  // ---------------------------------------------------------------------------------//
  {
    prefs = Preferences.userRoot ().node ("dm3270-test/" + UUID.randomUUID ());
    PluginProbe.reset ();
  }

  // ---------------------------------------------------------------------------------//
  @AfterEach
  void closeAndRemove () throws Exception
  // ---------------------------------------------------------------------------------//
  {
    if (stage != null)
      stage.closeClassLoader ();

    prefs.removeNode ();
    prefs.flush ();
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("cada plugin ve a copia que veio no proprio JAR")
  void cadaPluginVeACopiaDoProprioJar () throws Exception
  // ---------------------------------------------------------------------------------//
  {
    writeJar ("a", "A");
    writeJar ("b", "B");

    buildMenu ();
    stage.processAll (data (0));

    assertEquals ("A", stampSeenBy ("PluginA"));
    assertEquals ("B", stampSeenBy ("PluginB"));
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("um plugin continua achando a biblioteca solta noutro JAR da pasta")
  void pluginAchaBibliotecaSoltaNoutroJar () throws Exception
  // ---------------------------------------------------------------------------------//
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
            import com.bytezone.dm3270.plugins.PluginData;
            import com.bytezone.dm3270.plugins.PluginProbe;

            import com.example.lib.Greeting;

            public class PluginUser implements Plugin
            {
              public PluginUser () { }

              @Override
              public boolean doesAuto ()
              {
                return true;
              }

              @Override
              public void processAuto (PluginData data)
              {
                PluginProbe.record ("PluginUser:" + Greeting.text ());
              }
            }
            """);

    // Compila juntas e empacota em JARs separados.
    sources.writeTo (pluginsDirectory, "lib.jar", "com.example.lib.Greeting");
    sources.writeTo (pluginsDirectory, "PluginUser.jar", "com.example.user.PluginUser");

    buildMenu ();
    stage.processAll (data (0));

    assertEquals (List.of ("PluginUser:biblioteca"), PluginProbe.calls ());
  }

  // ---------------------------------------------------------------------------------//
  private void writeJar (String suffix, String letter) throws Exception
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
            import com.bytezone.dm3270.plugins.PluginData;
            import com.bytezone.dm3270.plugins.PluginProbe;

            import com.example.shared.Stamp;

            public class %s implements Plugin
            {
              public %s () { }

              @Override
              public boolean doesAuto ()
              {
                return true;
              }

              @Override
              public void processAuto (PluginData data)
              {
                PluginProbe.record ("%s:" + Stamp.mark ());
              }
            }
            """.formatted (suffix, plugin, plugin, plugin))
        .writeTo (pluginsDirectory, plugin + ".jar");
  }

  // ---------------------------------------------------------------------------------//
  private void buildMenu ()
  // ---------------------------------------------------------------------------------//
  {
    stage = JavaFxToolkit.onFxThread ( () -> new PluginsStage (prefs, pluginsDirectory));
    JavaFxToolkit.onFxThread ( () -> stage.getMenu ());
    PluginProbe.clearCalls ();
  }

  // Busca por nome porque a ordem de listagem dos JARs no diretorio nao e deterministica.
  // ---------------------------------------------------------------------------------//
  private static String stampSeenBy (String plugin)
  // ---------------------------------------------------------------------------------//
  {
    return PluginProbe.calls ().stream ().filter (call -> call.startsWith (plugin + ":"))
        .findFirst ()
        .orElseThrow ( () -> new AssertionError (plugin + " nao foi despachado"))
        .substring (plugin.length () + 1);
  }

  // ---------------------------------------------------------------------------------//
  private static PluginData data (int sequence)
  // ---------------------------------------------------------------------------------//
  {
    return new PluginData (sequence, new ScreenLocation (0), new ArrayList<> ());
  }
}
