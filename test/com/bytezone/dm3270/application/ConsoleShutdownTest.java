package com.bytezone.dm3270.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
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

import javafx.stage.Stage;

/*
 * Console.stop () - o desligamento, que ate aqui nao tinha prova nenhuma.
 *
 * O QUE ESTE GRUPO ALCANCA, e e pouco de proposito: o stop protege SEIS colaboradores com
 * "if != null" (mainframeStage, spyPane, consolePane, replayStage, os dois WindowSaver e a
 * screen), e num Console que nunca lancou todos eles SAO nulos. Sobram dois caminhos vivos -
 * optionStage.savePreferences () e pluginsStage.closeClassLoader () -, e e a ORDEM entre eles
 * que se congela aqui.
 *
 * O QUE ELE NAO ALCANCA, e vale dizer em vez de calar: a ordem dos quatro disconnect () e as
 * duas chamadas de WindowSaver.saveWindow (). Os seis so existem depois de um lancamento
 * bem-sucedido, que constroi Screen, abre socket e mostra janela - e isso fica para quando a
 * construcao sair do Console, no passo seguinte.
 *
 * E HA UM DEFEITO AQUI, congelado e nao corrigido (Regra 1): o stop guarda seis campos contra
 * nulo e NAO guarda o setimo. savePreferences () desreferencia optionStage sem guarda
 * (Console.java:299), entao um Console que nunca chegou ao start estoura NullPointerException
 * ao ser parado. E o item 22 do BACKLOG-DEFEITOS.md.
 */
// -----------------------------------------------------------------------------------//
@ExtendWith (JavaFxToolkit.class)
@DisplayName ("Console.stop - o desligamento")
class ConsoleShutdownTest
// -----------------------------------------------------------------------------------//
{
  @TempDir
  private Path pluginsDirectory;

  private Preferences prefs;
  private TestConsole console;

  // ---------------------------------------------------------------------------------//
  @BeforeEach
  void createPreferencesNode ()
  // ---------------------------------------------------------------------------------//
  {
    prefs = Preferences.userRoot ().node ("dm3270-test/" + UUID.randomUUID ());
    console = new TestConsole (prefs, pluginsDirectory);
  }

  // ---------------------------------------------------------------------------------//
  @AfterEach
  void removePreferencesNode () throws Exception
  // ---------------------------------------------------------------------------------//
  {
    if (console.recordedOptionStage != null)
      JavaFxToolkit.onFxThread ( () ->
      {
        console.recordedOptionStage.hide ();
        return null;
      });

    prefs.removeNode ();
    prefs.flush ();
  }

  // ---------------------------------------------------------------------------------//
  private void start ()
  // ---------------------------------------------------------------------------------//
  {
    JavaFxToolkit.onFxThread ( () ->
    {
      console.start (new Stage ());
      console.calls.clear ();

      return null;
    });
  }

  /*
   * A ordem e comportamento: as preferencias sao gravadas ANTES de o class loader dos plugins
   * fechar. Inverter significaria salvar com o loader ja fechado.
   */
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("grava as preferencias e SO ENTAO fecha o class loader dos plugins")
  void savesThenClosesTheClassLoader ()
  // ---------------------------------------------------------------------------------//
  {
    start ();

    JavaFxToolkit.onFxThread ( () ->
    {
      console.stop ();
      return null;
    });

    assertEquals (
        List.of ("optionStage.savePreferences", "pluginsStage.closeClassLoader"),
        console.calls);
  }

  /*
   * Sem tela, as duas chaves de fonte nao sao escritas - a guarda de Console.java:301
   * segurou. E ela que faz o stop sobreviver ao prefs nulo dentro do Console, que e o que
   * torna este arreio possivel sem uma costura a mais.
   *
   * O LIMITE: isto prova que a guarda segurou, e nao que a chave ficou ausente num no vivo.
   * Se o prefs do Console fosse o no do teste e a guarda falhasse, o sintoma seria uma chave
   * a mais; aqui o sintoma e um NullPointerException. Os dois pegam a regressao.
   */
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("sem tela, as chaves de fonte nao sao escritas")
  void writesNoFontKeysWithoutAScreen ()
  // ---------------------------------------------------------------------------------//
  {
    start ();

    JavaFxToolkit.onFxThread ( () ->
    {
      console.stop ();
      return null;
    });

    assertEquals ("ausente", prefs.get ("FontName", "ausente"));
    assertEquals ("ausente", prefs.get ("FontSize", "ausente"));
  }

  /*
   * O item 22 do BACKLOG-DEFEITOS.md, congelado e NAO corrigido.
   *
   * Isto nao e teorico: Application.stop () e chamado pelo runtime do JavaFX no fechamento, e
   * start (Stage) declara "throws Exception". Um lancamento que falhe antes de o OptionStage
   * nascer - por exemplo porque o PluginsStage estourou ao varrer a pasta de plugins - deixa
   * o Console exatamente neste estado.
   */
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("DEFEITO: parar um Console que nunca comecou estoura")
  void stoppingAConsoleThatNeverStartedThrows ()
  // ---------------------------------------------------------------------------------//
  {
    assertThrows (NullPointerException.class, () -> console.stop (),
        "o stop guarda seis colaboradores contra nulo e nao guarda o optionStage");
  }
}
