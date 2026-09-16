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

/*
 * A REGRA 3 vira porta de build.
 *
 * "JARs de terceiros ja compilados tem de continuar carregando" era, ate aqui, uma promessa
 * verificada a mao: rodar mvn install no dm3270 e mvn test no repositorio de plugins. Isso
 * prova compatibilidade de FONTE - la tudo e recompilado. Esta classe prova a outra metade:
 * um JAR e compilado e empacotado durante o teste, e depois carregado pelo PluginsStage pelo
 * mesmo caminho de um plugin de verdade - varredura do diretorio, descoberta por reflexao,
 * auto-registro nas Preferences, instanciacao e despacho.
 *
 * O primeiro caso usa a forma que os dois JARs instalados nesta maquina realmente tem -
 * extends DefaultPlugin, conferido com javap -, e chama o getModifiableFields herdado, que e
 * o UNICO dos sete utilitarios que algum plugin usa. E exatamente o que o bloco 2 vai mexer.
 *
 * Um detalhe que faz isto funcionar e que vale saber: o plugin sintetico fala com o teste
 * pelo PluginProbe, e ele consegue porque um URLClassLoader filho delega ao pai antes de
 * definir a classe. O PluginProbe que o plugin enxerga e o mesmo do classpath da aplicacao, e
 * portanto os estaticos sao compartilhados. O bloco 3 depende da mesma propriedade.
 */
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

  /*
   * O closeClassLoader e obrigatorio: no Windows um URLClassLoader aberto mantem o JAR
   * mapeado e a remocao do @TempDir pelo JUnit falha com DirectoryNotEmptyException. Aqui ha
   * JAR de verdade, entao isto nao e teorico.
   */
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

  /*
   * A forma legada: extends DefaultPlugin, com o utilitario herdado sendo usado. Se o bloco 2
   * mover getModifiableFields de um jeito que quebre o invokestatic ja gravado, e aqui que
   * aparece.
   */
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

  /*
   * O filtro da descoberta, pelas duas bordas: uma classe que nao implementa Plugin e
   * ignorada, e uma classe ANINHADA e ignorada porque o nome da entrada do JAR contem "$" -
   * a varredura pula qualquer entrada com cifrao. Um plugin declarado como classe aninhada e
   * invisivel, e isso e comportamento, nao acidente.
   */
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
  //  Apoio
  // ---------------------------------------------------------------------------------//

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
