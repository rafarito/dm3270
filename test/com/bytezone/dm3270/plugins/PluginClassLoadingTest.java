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

/*
 * A COLISAO DE NOME QUALIFICADO ENTRE JARS DE PLUGIN.
 *
 * O host monta UM URLClassLoader com todos os JARs da pasta. Quando dois JARs trazem uma
 * classe com o MESMO nome qualificado e bytecode diferente, a primeira definicao encontrada
 * vence PARA OS DOIS PLUGINS - e qual delas vence depende da ordem em que File.listFiles ()
 * devolve os arquivos.
 *
 * ISSO NAO E COMPORTAMENTO A PRESERVAR, E INDETERMINISMO, e e a unica excecao a Regra 1
 * prevista para o passo 9, autorizada pelo usuario.
 *
 * O caso real, medido no repositorio irmao: com.bytezone.plugins.Document e
 * com.bytezone.plugins.DocumentPage existem em duas copias, no DownloadDataset e no
 * ShowDataset, e divergem. A do Document e semantica: o stitch () do DownloadDataset remonta
 * o arquivo fielmente e reinicia o contador de linha entre faixas horizontais; o do
 * ShowDataset prefixa o numero da linha no texto e usa leftColumn + 6 onde o outro usa
 * leftColumn - 1. Quem perde o sorteio recebe o formato de arquivo do outro plugin.
 *
 * UMA CORRECAO AOS DOCUMENTOS DO PROJETO: o ScreenBuilder.java, que os dois CLAUDE.md listam
 * ao lado desses dois, NAO colide. Ele existe so em test/, em tres modulos, e nunca e
 * empacotado num JAR - o release.yml compila apenas a pasta de fontes de cada modulo.
 * Colidem duas classes, nao tres.
 *
 * E a colisao esta LATENTE nesta maquina: a pasta de plugins tem so DownloadDataset.jar e
 * UploadDataset.jar, que nao compartilham nome nenhum. Ela aparece quando o ShowDataset.jar
 * e instalado ao lado do DownloadDataset.jar.
 */
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

  /*
   * Dois JARs, cada um com a SUA copia de com.example.shared.Stamp, devolvendo letras
   * diferentes. Cada plugin reporta o que a copia que ele enxergou respondeu.
   *
   * ESTE CASO DELIBERADAMENTE NAO AFIRMA QUAL LETRA CADA UM VE. Hoje isso depende da ordem em
   * que File.listFiles () devolve os arquivos, que nao e especificada: afirmar um valor
   * concreto seria escrever um teste que passa por sorte, e que mudaria de resultado noutro
   * sistema de arquivos. O que ele afirma e a CONSEQUENCIA observavel da colisao - os dois
   * veem a mesma letra, isto e, um dos dois esta rodando o codigo do outro.
   *
   * Medido nesta maquina no momento em que este teste foi escrito: os dois viam "A", e o
   * PluginB rodava o Stamp que veio dentro do PluginA.jar.
   */
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("hoje os dois plugins veem a mesma copia, e qual delas e indeterminado")
  void osDoisVeemAMesmaCopia () throws Exception
  // ---------------------------------------------------------------------------------//
  {
    writeJar ("a", "A");
    writeJar ("b", "B");

    buildMenu ();
    stage.processAll (data (0));

    assertEquals (stampSeenBy ("PluginA"), stampSeenBy ("PluginB"));
  }

  // ---------------------------------------------------------------------------------//
  //  Apoio
  // ---------------------------------------------------------------------------------//

  /*
   * Um JAR com um plugin e uma classe auxiliar de nome qualificado FIXO - o mesmo nos dois
   * JARs -, cuja unica diferenca e a letra que ela devolve.
   */
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

  /*
   * A letra que um plugin reportou. A busca e por nome, e nao por posicao, porque a ordem de
   * despacho segue a ordem de listagem do diretorio, que nao e especificada.
   */
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
