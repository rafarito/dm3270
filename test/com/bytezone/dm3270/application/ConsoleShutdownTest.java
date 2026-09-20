package com.bytezone.dm3270.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
 * bem-sucedido, que constroi Screen, abre socket e mostra janela.
 *
 * ESTE PARAGRAFO DIZIA "e isso fica para quando a construcao sair do Console, no passo
 * seguinte", e o passo 12 mostrou que a previsao estava errada. A construcao SAIU do switch e
 * foi para a porta LaunchTarget - mas quem a implementa continua sendo o proprio Console, e
 * num Console que nunca lancou os seis campos continuam nulos do mesmo jeito. O que falta para
 * alcanca-los nao e a decisao sair, e a CONSTRUCAO poder ser substituida - ou seja, a Screen
 * concreta sair da assinatura de createScreen. Isso e o passo 13.
 *
 * E O DEFEITO QUE ESTAVA AQUI FOI CORRIGIDO no passo 12, a pedido do usuario: o stop guardava
 * seis campos contra nulo e nao guardava o setimo. E o item 22 do BACKLOG-DEFEITOS.md, e o
 * caso no fim desta classe registra as duas pontas da mudanca.
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
   * Sem tela, as duas chaves de fonte nao sao escritas - a guarda de nulo do screen dentro de
   * savePreferences segurou. E ela que faz o stop sobreviver ao prefs nulo dentro do Console,
   * que e o que torna este arreio possivel sem uma costura a mais.
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
   * O item 22 do BACKLOG-DEFEITOS.md, CORRIGIDO no passo 12 a pedido do usuario.
   *
   * Ate a correcao este caso afirmava o contrario - assertThrows (NullPointerException.class) -
   * e estava certo: o stop () protegia seis colaboradores contra nulo e nao protegia o setimo,
   * porque savePreferences () desreferenciava o optionStage sem guarda.
   *
   * Nao era teorico. Application.stop () e chamado pelo runtime do JavaFX no fechamento, e
   * start (Stage) declara "throws Exception": qualquer falha antes de o OptionStage nascer -
   * o candidato realista e o PluginsStage, construido na linha imediatamente anterior, que
   * varre a pasta de plugins e monta um class loader por JAR - deixava o Console exatamente
   * neste estado.
   */
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("parar um Console que nunca comecou NAO estoura mais")
  void stoppingAConsoleThatNeverStartedIsSafe ()
  // ---------------------------------------------------------------------------------//
  {
    assertDoesNotThrow ( () -> console.stop (),
        "o stop passou a guardar o optionStage como ja guardava os outros seis");
  }
}
