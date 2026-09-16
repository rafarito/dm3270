package com.bytezone.dm3270.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import com.bytezone.dm3270.testing.JavaFxToolkit;

import javafx.stage.Stage;

/*
 * As tres mensagens de erro do lancamento, e a janela que reaparece depois do alerta.
 *
 * ESTE E O PEDACO MAIS VALIOSO DA REDE DO Console, e o motivo e de custo: os ramos de erro do
 * startSelectedFunction sao a unica parte do switch que NAO constroi nada. Os caminhos felizes
 * criam Screen, abrem arquivo SQLite no diretorio corrente, mostram janela e abrem socket para
 * um host - nenhum deles cabe num teste sem que a construcao saia do Console, que e o passo
 * seguinte. Os ramos de erro cabem hoje, e ate agora so tinham o roteiro de validacao MANUAL
 * do passo 7, que segue pendente.
 *
 * COMO ELE DIRIGE O LANCAMENTO: semeando o no de Preferences, e nada alem. O OptionStage e o
 * REAL - o LaunchRequest e montado pelos widgets de verdade, pelo mesmo caminho que
 * SiteListStage:114-122 percorre na aplicacao. O arreio so captura o gatilho que o Console ja
 * injeta em Console.java:81 e desvia o alerta, que de outra forma travaria a suite em
 * showAndWait.
 *
 * O MODO TEM DE SER Debug. Em Release o OptionStage forca a selecao para Terminal, entao
 * qualquer caso de Spy, Replay ou Test precisa de "Mode=Debug" semeado antes. E a mesma
 * disciplina do helper debugStage do OptionStageTest.
 */
// -----------------------------------------------------------------------------------//
@ExtendWith (JavaFxToolkit.class)
@DisplayName ("Console.startSelectedFunction - os ramos que recusam o lancamento")
class ConsoleLaunchErrorsTest
// -----------------------------------------------------------------------------------//
{
  @TempDir
  private Path pluginsDirectory;

  @TempDir
  private Path spyFolder;

  private Preferences prefs;
  private TestConsole console;

  // ---------------------------------------------------------------------------------//
  @BeforeEach
  void createPreferencesNode ()
  // ---------------------------------------------------------------------------------//
  {
    prefs = Preferences.userRoot ().node ("dm3270-test/" + UUID.randomUUID ());
    prefs.put ("Mode", "Debug");
  }

  // ---------------------------------------------------------------------------------//
  @AfterEach
  void closeEverything () throws Exception
  // ---------------------------------------------------------------------------------//
  {
    // Stage.show () e final, entao a janela de opcoes apareceu de verdade. Fechar e
    // obrigatorio: sem isto a suite acumula janelas abertas.
    JavaFxToolkit.onFxThread ( () ->
    {
      console.recordedOptionStage.hide ();
      return null;
    });

    console.recordedPluginsStage.closeClassLoader ();

    prefs.removeNode ();
    prefs.flush ();
  }

  /*
   * Sobe o Console de verdade e dispara o gatilho que o OptionStage recebeu - o mesmo Runnable
   * que o botao Connect dispara na aplicacao.
   */
  // ---------------------------------------------------------------------------------//
  private void launch (String function)
  // ---------------------------------------------------------------------------------//
  {
    prefs.put ("Function", function);
    console = new TestConsole (prefs, pluginsDirectory);

    JavaFxToolkit.onFxThread ( () ->
    {
      console.start (new Stage ());
      console.connect ();

      return null;
    });
  }

  // ---------------------------------------------------------------------------------//
  private void seedServer ()
  // ---------------------------------------------------------------------------------//
  {
    prefs.put ("Server00Name", "prod");
    prefs.put ("Server00URL", "mvs.example.com");
    prefs.put ("ServerName", "prod");
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("as tres mensagens")
  class TheMessages
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("Terminal sem servidor recusa com No server selected")
    void terminalWithoutServer ()
    // -------------------------------------------------------------------------------//
    {
      launch ("Terminal");

      assertEquals (List.of ("No server selected"), console.alerts);
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("Spy sem servidor recusa com No server selected")
    void spyWithoutServer ()
    // -------------------------------------------------------------------------------//
    {
      launch ("Spy");

      assertEquals (List.of ("No server selected"), console.alerts);
    }

    /*
     * A ORDEM das duas guardas do ramo Spy e comportamento: o servidor e testado primeiro
     * (Console.java:147-150). Com servidor e sem cliente, a mensagem TEM de ser a do cliente.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("Spy COM servidor e sem cliente recusa com No client selected")
    void spyWithServerButNoClient ()
    // -------------------------------------------------------------------------------//
    {
      seedServer ();

      launch ("Spy");

      assertEquals (List.of ("No client selected"), console.alerts);
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("Test sem cliente recusa com No client selected")
    void testWithoutClient ()
    // -------------------------------------------------------------------------------//
    {
      launch ("Test");

      assertEquals (List.of ("No client selected"), console.alerts);
    }

    /*
     * O caminho do replay e montado por concatenacao de strings com barra normal
     * (Console.java:97), e so depois vira Path - por isso a mensagem sai com o separador ja
     * normalizado pelo sistema.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("Replay com arquivo inexistente diz o caminho inteiro")
    void replayWithMissingFile ()
    // -------------------------------------------------------------------------------//
    {
      prefs.put ("SpyFolder", spyFolder.toString ());
      prefs.put ("ReplayFile", "nao-existe.txt");

      launch ("Replay");

      Path expected = Paths.get (spyFolder + "/" + "nao-existe.txt");
      assertEquals (List.of (expected + " does not exist"), console.alerts);
    }
  }

  /*
   * O epilogo, em Console.java:175-176. E o item do roteiro de validacao manual do passo 7
   * que dizia "ao clicar OK no alerta, a janela de opcoes tem de reaparecer".
   */
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("o epilogo: a janela que reaparece")
  class TheEpilogue
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("esconde a janela ANTES de tudo, e a traz de volta depois do alerta")
    void hidesFirstAndShowsAgain ()
    // -------------------------------------------------------------------------------//
    {
      console = new TestConsole (prefs, pluginsDirectory);
      prefs.put ("Function", "Terminal");

      JavaFxToolkit.onFxThread ( () ->
      {
        console.start (new Stage ());
        console.calls.clear ();
        console.connect ();

        return null;
      });

      assertEquals (List.of ("optionStage.hide", "showAlert", "optionStage.show"),
          console.calls);
    }

    /*
     * O "&&" do epilogo: quando o usuario nao confirma o alerta, a janela NAO volta. E o
     * Console fica sem janela nenhuma, que e o comportamento de hoje.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("se o usuario nao confirma o alerta, a janela nao volta")
    void doesNotShowAgainWhenTheAlertIsDismissed ()
    // -------------------------------------------------------------------------------//
    {
      console = new TestConsole (prefs, pluginsDirectory);
      console.alertAnswer = false;
      prefs.put ("Function", "Terminal");

      JavaFxToolkit.onFxThread ( () ->
      {
        console.start (new Stage ());
        console.calls.clear ();
        console.connect ();

        return null;
      });

      assertEquals (List.of ("optionStage.hide", "showAlert"), console.calls);
    }

    /*
     * A SEQUENCIA INTEIRA, do start ao alerta, numa asserticao de lista COMPLETA. E ela que
     * prova que um lancamento recusado nao constroi NADA - nem tela, nem painel, nem sessao:
     * qualquer colaborador novo apareceria na lista e derrubaria o caso. E o que torna este
     * grupo alcancavel sem socket, sem banco e sem arquivo.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("a sequencia inteira de um lancamento recusado, do start ao alerta")
    void theWholeRefusedLaunch ()
    // -------------------------------------------------------------------------------//
    {
      launch ("Terminal");

      assertEquals (List.of ("createPluginsStage", "createOptionStage",
          "optionStage.setOnConnect", "optionStage.show", "optionStage.hide", "showAlert",
          "optionStage.show"), console.calls);
    }
  }
}
