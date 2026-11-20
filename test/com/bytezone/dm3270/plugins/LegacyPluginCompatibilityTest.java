package com.bytezone.dm3270.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

// URLClassLoader delega ao pai para classes do host, permitindo ao JAR gravar no PluginProbe.
// -----------------------------------------------------------------------------------//
@ExtendWith (JavaFxToolkit.class)
@DisplayName ("Regra 3 - um JAR compilado contra a API continua carregando")
class LegacyPluginCompatibilityTest
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

  // Obrigatorio no Windows: URLClassLoader aberto impede a remocao do @TempDir pelo JUnit.
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
  @DisplayName ("um plugin que estende DefaultPlugin e descoberto, ativado e despachado")
  void pluginQueEstendeDefaultPluginContinuaFuncionando () throws Exception
  // ---------------------------------------------------------------------------------//
  {
    new SyntheticPluginJar ()
        .add ("com.example.legacy.LegacyPlugin", """
            package com.example.legacy;

            import java.util.List;

            import com.bytezone.dm3270.plugins.DefaultPlugin;
            import com.bytezone.dm3270.plugins.PluginData;
            import com.bytezone.dm3270.plugins.PluginField;
            import com.bytezone.dm3270.plugins.PluginProbe;

            public class LegacyPlugin extends DefaultPlugin
            {
              public LegacyPlugin () { }

              @Override
              public void activate ()
              {
                PluginProbe.record ("LegacyPlugin.activate");
              }

              @Override
              public boolean doesAuto ()
              {
                return true;
              }

              @Override
              public void processAuto (PluginData data)
              {
                List<PluginField> modifiable = getModifiableFields (data);
                PluginProbe.record ("LegacyPlugin.processAuto:" + data.sequence
                    + " modificaveis=" + modifiable.size ());
              }
            }
            """)
        .writeTo (pluginsDirectory, "LegacyPlugin.jar");

    buildMenu ();

    assertEquals ("LegacyPlugin", prefs.get ("PluginName-00", ""));
    assertEquals ("com.example.legacy.LegacyPlugin", prefs.get ("PluginClass-00", ""));
    assertTrue (prefs.getBoolean ("PluginActivate-00", false));

    stage.processAll (data (5));

    assertEquals (List.of ("LegacyPlugin.processAuto:5 modificaveis=0"),
                  PluginProbe.calls ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("um plugin que implementa Plugin direto tambem e descoberto e despachado")
  void pluginQueImplementaPluginDireto () throws Exception
  // ---------------------------------------------------------------------------------//
  {
    new SyntheticPluginJar ()
        .add ("com.example.modern.ModernPlugin", """
            package com.example.modern;

            import com.bytezone.dm3270.plugins.Plugin;
            import com.bytezone.dm3270.plugins.PluginData;
            import com.bytezone.dm3270.plugins.PluginProbe;

            public class ModernPlugin implements Plugin
            {
              public ModernPlugin () { }

              @Override
              public boolean doesAuto ()
              {
                return true;
              }

              @Override
              public void processAuto (PluginData data)
              {
                PluginProbe.record ("ModernPlugin.processAuto:" + data.sequence);
              }
            }
            """)
        .writeTo (pluginsDirectory, "ModernPlugin.jar");

    buildMenu ();
    stage.processAll (data (1));

    assertEquals (List.of ("ModernPlugin.processAuto:1"), PluginProbe.calls ());
  }

  // Entradas com '$' (classes aninhadas) sao ignoradas intencionalmente na varredura.
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("classe que nao e Plugin e classe aninhada nao entram na descoberta")
  void oQueADescobertaIgnora () throws Exception
  // ---------------------------------------------------------------------------------//
  {
    new SyntheticPluginJar ()
        .add ("com.example.ignored.NotAPlugin", """
            package com.example.ignored;

            public class NotAPlugin
            {
              public NotAPlugin () { }

              public static class Nested
                  implements com.bytezone.dm3270.plugins.Plugin
              {
                public Nested () { }
              }
            }
            """)
        .writeTo (pluginsDirectory, "Ignored.jar");

    buildMenu ();

    assertEquals ("", prefs.get ("PluginName-00", ""));
    assertEquals (0, stage.activePlugins ());
  }

  // ---------------------------------------------------------------------------------//
  private void buildMenu ()
  // ---------------------------------------------------------------------------------//
  {
    stage = JavaFxToolkit.onFxThread ( () -> new PluginsStage (prefs, pluginsDirectory));
    JavaFxToolkit.onFxThread ( () -> stage.getMenu ());
    PluginProbe.clearCalls ();
  }

  // ---------------------------------------------------------------------------------//
  private static PluginData data (int sequence)
  // ---------------------------------------------------------------------------------//
  {
    return new PluginData (sequence, new ScreenLocation (0), new ArrayList<> ());
  }
}
