package com.bytezone.dm3270.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.bytezone.dm3270.runtime.TerminalFunction;
import com.bytezone.dm3270.testing.JavaFxToolkit;
import com.bytezone.dm3270.utilities.Site;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.HBox;

/*
 * Caracterizacao do OptionStage - a janela que escolhe o modo e lanca a aplicacao.
 *
 * Ate este commit o caminho inteiro de lancamento - Console, OptionStage e ConsoleKeyPress -
 * nao tinha um unico teste, e e justamente o caminho que o passo 7 vai mexer: o Console le
 * hoje DEZ membros package-private do OptionStage, 100% da superficie de pacote de uma
 * classe que nao tem acessor nenhum. Esta e a rede que falta antes de tocar nisso.
 *
 * O TESTE ALCANCA OS CONTROLES PELO GRAFO DE CENA, E NAO PELOS CAMPOS DA CLASSE. Isto e
 * deliberado, e e o que faz a rede sobreviver ao refactor que ela protege: os dez membros
 * viram private no fim deste passo, e um teste preso a eles teria de ser reescrito
 * exatamente no commit que prova o trabalho - uma rede que se ajusta quando incomoda deixa
 * de ser prova de coisa alguma. Pela cena, o teste continua valendo, e de quebra afirma o
 * que o usuario ve em vez do que a classe guarda.
 *
 * O que fica congelado aqui:
 *
 *   - a matriz de habilitacao. Cada uma das quatro opcoes desabilita um conjunto diferente,
 *     e sao PARES: o combo e o botao de edicao ao lado andam sempre juntos.
 *   - o caminho de fallback, quando a preferencia "Function" guarda valor fora da lista. O
 *     documento de contexto dizia que ali a chamada final de disableButtons e um no-op, e
 *     parava nisso; a medicao mostrou que ANTES dela o selectToggle dispara o listener com
 *     "Terminal", e e essa chamada que faz o trabalho. O efeito observavel e o estado de
 *     Terminal, nao um estado indefinido.
 *   - o modo Release, que nao desabilita nada: ele REMOVE da cena o painel de funcoes e o
 *     de cliente/replay, troca o titulo e forca a selecao em Terminal.
 *   - a lista de arquivos de replay, que so aceita spyNNNN com ate quatro digitos e
 *     extensao .txt opcional, e vem ordenada.
 *
 * As Preferences sao globais por JVM, entao cada teste recebe um no proprio sob userRoot,
 * removido no fim. Sem isso a suite escreveria nas preferencias reais de quem a roda - o
 * OptionStage le "Function", "Mode", "SpyFolder", "ReplayFile", "ServerName" e "ClientName"
 * ja no construtor.
 */
// -----------------------------------------------------------------------------------//
@ExtendWith (JavaFxToolkit.class)
@DisplayName ("OptionStage - a janela de abertura e as regras de habilitacao")
class OptionStageTest
// -----------------------------------------------------------------------------------//
{
  private Preferences prefs;

  // ---------------------------------------------------------------------------------//
  @BeforeEach
  void createPreferencesNode ()
  // ---------------------------------------------------------------------------------//
  {
    prefs = Preferences.userRoot ().node ("dm3270-test/" + UUID.randomUUID ());
  }

  // ---------------------------------------------------------------------------------//
  @AfterEach
  void removePreferencesNode () throws Exception
  // ---------------------------------------------------------------------------------//
  {
    prefs.removeNode ();
    prefs.flush ();
  }

  /*
   * O construtor de uma Stage exige a thread do JavaFX. O item de menu e o que o
   * OptionStage passou a receber em lugar do PluginsStage inteiro: construir um
   * PluginsStage montaria um class loader, varreria a pasta de plugins e gravaria
   * preferencias.
   */
  // ---------------------------------------------------------------------------------//
  private OptionStage stage ()
  // ---------------------------------------------------------------------------------//
  {
    return JavaFxToolkit
        .onFxThread ( () -> new OptionStage (prefs, new MenuItem ("Plugins...")));
  }

  // ---------------------------------------------------------------------------------//
  private OptionStage debugStage (String function)
  // ---------------------------------------------------------------------------------//
  {
    prefs.put ("Mode", "Debug");
    prefs.put ("Function", function);
    return stage ();
  }

  // ---------------------------------------------------------------------------------//
  private static void onFx (Runnable action)
  // ---------------------------------------------------------------------------------//
  {
    JavaFxToolkit.onFxThread ( () ->
    {
      action.run ();
      return null;
    });
  }

  // ---------------------------------------------------------------------------------//
  //  Navegacao pelo grafo de cena
  // ---------------------------------------------------------------------------------//

  /*
   * Nao desce por dentro de um Control: o skin de um ComboBox pode conter HBox propria, e a
   * busca por rotulo de linha passaria a casar coisa que nao e linha do formulario.
   */
  // ---------------------------------------------------------------------------------//
  private static void collect (Node node, List<Node> found)
  // ---------------------------------------------------------------------------------//
  {
    found.add (node);

    if (node instanceof Control || !(node instanceof Parent parent))
      return;

    for (Node child : parent.getChildrenUnmodifiable ())
      collect (child, found);
  }

  // ---------------------------------------------------------------------------------//
  private static List<Node> nodes (OptionStage stage)
  // ---------------------------------------------------------------------------------//
  {
    List<Node> found = new ArrayList<> ();
    collect (stage.getScene ().getRoot (), found);
    return found;
  }

  /*
   * Uma linha do formulario e a HBox montada por OptionStage.row (): o primeiro filho e um
   * Label com o texto pedido, e os seguintes sao os campos. Devolve so os campos.
   */
  // ---------------------------------------------------------------------------------//
  private static List<Node> row (OptionStage stage, String labelText)
  // ---------------------------------------------------------------------------------//
  {
    for (Node node : nodes (stage))
      if (node instanceof HBox hbox && !hbox.getChildren ().isEmpty ()
          && hbox.getChildren ().get (0) instanceof Label label
          && labelText.equals (label.getText ()))
        return List.copyOf (hbox.getChildren ().subList (1, hbox.getChildren ().size ()));

    return List.of ();
  }

  // ---------------------------------------------------------------------------------//
  private static boolean hasRow (OptionStage stage, String labelText)
  // ---------------------------------------------------------------------------------//
  {
    return !row (stage, labelText).isEmpty ();
  }

  // ---------------------------------------------------------------------------------//
  private static List<RadioButton> radios (OptionStage stage)
  // ---------------------------------------------------------------------------------//
  {
    List<RadioButton> found = new ArrayList<> ();

    for (Node node : nodes (stage))
      if (node instanceof RadioButton radioButton)
        found.add (radioButton);

    return found;
  }

  // ---------------------------------------------------------------------------------//
  private static RadioButton radio (OptionStage stage, String option)
  // ---------------------------------------------------------------------------------//
  {
    for (RadioButton radioButton : radios (stage))
      if (option.equals (radioButton.getUserData ()))
        return radioButton;

    throw new AssertionError ("nao achei o radio button de " + option);
  }

  // ---------------------------------------------------------------------------------//
  private static List<MenuItem> menuItems (OptionStage stage)
  // ---------------------------------------------------------------------------------//
  {
    for (Node node : nodes (stage))
      if (node instanceof MenuBar menuBar)
        return menuBar.getMenus ().get (0).getItems ();

    throw new AssertionError ("nao achei a barra de menu");
  }

  // ---------------------------------------------------------------------------------//
  private static CheckMenuItem modeMenuItem (OptionStage stage)
  // ---------------------------------------------------------------------------------//
  {
    for (MenuItem item : menuItems (stage))
      if (item instanceof CheckMenuItem checkMenuItem)
        return checkMenuItem;

    throw new AssertionError ("nao achei o item de menu do modo");
  }

  /*
   * Um clique num CheckMenuItem sao DUAS coisas, e fire () e so a segunda: quem inverte o
   * selected e o skin do menu (ContextMenuContent.doSelect), antes de disparar a acao.
   * Chamar fire () sozinho deixa o selected como estava, e o switchMode le justamente esse
   * valor - o modo nao mudaria, e o teste passaria a afirmar o contrario do que o usuario
   * ve. Foi assim que este teste descobriu a armadilha, em tres casos de uma vez.
   */
  // ---------------------------------------------------------------------------------//
  private static void clickModeMenuItem (OptionStage stage)
  // ---------------------------------------------------------------------------------//
  {
    CheckMenuItem menuItem = modeMenuItem (stage);

    onFx ( () ->
    {
      menuItem.setSelected (!menuItem.isSelected ());
      menuItem.fire ();
    });
  }

  // ---------------------------------------------------------------------------------//
  private static Button button (OptionStage stage, String text)
  // ---------------------------------------------------------------------------------//
  {
    for (Node node : nodes (stage))
      if (node instanceof Button candidate && text.equals (candidate.getText ()))
        return candidate;

    throw new AssertionError ("nao achei o botao " + text);
  }

  @SuppressWarnings ("unchecked")
  // ---------------------------------------------------------------------------------//
  private static ComboBox<String> combo (OptionStage stage, String labelText)
  // ---------------------------------------------------------------------------------//
  {
    return (ComboBox<String>) row (stage, labelText).get (0);
  }

  /*
   * A matriz vale para PARES - o combo e o botao de edicao ao lado. Uma afirmacao que
   * olhasse so o combo deixaria passar um setDisable que esquecesse o botao.
   */
  // ---------------------------------------------------------------------------------//
  private static void assertRowDisabled (OptionStage stage, String labelText,
      boolean expected)
  // ---------------------------------------------------------------------------------//
  {
    List<Node> fields = row (stage, labelText);
    assertEquals (2, fields.size (), "a linha " + labelText + " tem campo e botao");

    for (Node field : fields)
      assertEquals (expected, field.isDisable (),
          labelText + " - " + field.getClass ().getSimpleName ());
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("a matriz de habilitacao, no modo Debug")
  class DisableMatrix
  // ---------------------------------------------------------------------------------//
  {
    /*
     * disableButtons: Spy (false, false, true), Replay (true, true, false),
     * Terminal (false, true, true), Test (true, false, true) - servidor, cliente, arquivos.
     */
    @ParameterizedTest (name = "{0} => servidor {1}, cliente {2}, replay {3}")
    @CsvSource ({ "Spy, false, false, true",                //
                  "Replay, true, true, false",              //
                  "Terminal, false, true, true",            //
                  "Test, true, false, true" })
    @DisplayName ("a preferencia gravada decide o que vem desabilitado")
    void savedFunctionDecidesTheDisabledControls (String function, boolean server,
        boolean client, boolean files)
    {
      OptionStage stage = debugStage (function);

      assertTrue (radio (stage, function).isSelected ());
      assertRowDisabled (stage, "Server", server);
      assertRowDisabled (stage, "Client", client);
      assertRowDisabled (stage, "Replay", files);
    }

    @ParameterizedTest (name = "marcar {0} => servidor {1}, cliente {2}, replay {3}")
    @CsvSource ({ "Spy, false, false, true",                //
                  "Replay, true, true, false",              //
                  "Terminal, false, true, true",            //
                  "Test, true, false, true" })
    @DisplayName ("marcar uma opcao em tempo de execucao aplica a mesma matriz")
    void selectingAnOptionAppliesTheSameMatrix (String function, boolean server,
        boolean client, boolean files)
    {
      OptionStage stage = debugStage ("Terminal");

      onFx ( () -> radio (stage, function).setSelected (true));

      assertRowDisabled (stage, "Server", server);
      assertRowDisabled (stage, "Client", client);
      assertRowDisabled (stage, "Replay", files);
    }

    /*
     * O caso que o documento de contexto descrevia pela metade. Com "Function" guardando
     * algo fora da lista, o construtor cai no fallback: seleciona Terminal, o que dispara o
     * listener e aplica a matriz de Terminal, e SO ENTAO chama disableButtons com a string
     * desconhecida, que nao casa ramo nenhum e nao desfaz nada.
     */
    @Test
    @DisplayName ("uma funcao desconhecida cai em Terminal, com a matriz de Terminal")
    void unknownFunctionFallsBackToTerminal ()
    {
      OptionStage stage = debugStage ("Banana");

      assertTrue (radio (stage, "Terminal").isSelected ());
      assertRowDisabled (stage, "Server", false);
      assertRowDisabled (stage, "Client", true);
      assertRowDisabled (stage, "Replay", true);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("os modos Release e Debug")
  class ReleaseAndDebug
  // ---------------------------------------------------------------------------------//
  {
    /*
     * Release e o padrao - prefs.get ("Mode", "Release"). Ele nao desabilita nada: remove
     * da cena o painel de funcoes e o de cliente/replay, deixando so o servidor.
     */
    @Test
    @DisplayName ("Release e o padrao, e esconde funcoes, cliente e replay")
    void releaseIsTheDefaultAndHidesEverythingButTheServer ()
    {
      OptionStage stage = stage ();

      assertEquals ("Connect to Server", stage.getTitle ());
      assertTrue (hasRow (stage, "Server"));
      assertFalse (hasRow (stage, "Client"));
      assertFalse (hasRow (stage, "Replay"));
      assertTrue (radios (stage).isEmpty ());
      assertRowDisabled (stage, "Server", false);
    }

    @Test
    @DisplayName ("Release ignora a funcao gravada e usa Terminal")
    void releaseIgnoresTheSavedFunction ()
    {
      prefs.put ("Mode", "Release");
      prefs.put ("Function", "Spy");
      OptionStage stage = stage ();

      clickModeMenuItem (stage);        // volta para Debug, para os radios aparecerem

      assertTrue (radio (stage, "Terminal").isSelected ());
    }

    @Test
    @DisplayName ("Debug mostra funcoes, cliente e replay")
    void debugShowsEverything ()
    {
      OptionStage stage = debugStage ("Terminal");

      assertEquals ("Choose Function", stage.getTitle ());
      assertTrue (hasRow (stage, "Server"));
      assertTrue (hasRow (stage, "Client"));
      assertTrue (hasRow (stage, "Replay"));
      assertEquals (4, radios (stage).size ());
    }

    @Test
    @DisplayName ("o item de menu alterna os dois modos, e volta")
    void theMenuItemTogglesBothWays ()
    {
      OptionStage stage = debugStage ("Spy");
      CheckMenuItem menuItem = modeMenuItem (stage);
      assertFalse (menuItem.isSelected ());

      clickModeMenuItem (stage);

      assertTrue (menuItem.isSelected ());
      assertEquals ("Connect to Server", stage.getTitle ());
      assertFalse (hasRow (stage, "Client"));

      clickModeMenuItem (stage);

      assertFalse (menuItem.isSelected ());
      assertEquals ("Choose Function", stage.getTitle ());
      assertTrue (hasRow (stage, "Client"));
    }

    /*
     * Ir para Release forca Terminal, e como o listener ja existe nesse momento a matriz de
     * Terminal e aplicada junto - inclusive no cliente e no replay, que saem da cena.
     * Voltar para Debug os traz de volta ja desabilitados.
     */
    @Test
    @DisplayName ("ir para Release forca Terminal, e Debug devolve a matriz de Terminal")
    void goingToReleaseForcesTerminal ()
    {
      OptionStage stage = debugStage ("Spy");

      clickModeMenuItem (stage);        // Release
      clickModeMenuItem (stage);        // Debug

      assertTrue (radio (stage, "Terminal").isSelected ());
      assertRowDisabled (stage, "Server", false);
      assertRowDisabled (stage, "Client", true);
      assertRowDisabled (stage, "Replay", true);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("a janela")
  class TheWindow
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("as quatro opcoes vem na ordem, cada uma com o proprio nome no userData")
    void theFourOptionsCarryTheirOwnName ()
    {
      OptionStage stage = debugStage ("Terminal");
      List<RadioButton> radios = radios (stage);

      assertEquals (List.of ("Spy", "Replay", "Terminal", "Test"),
          radios.stream ().map (r -> (String) r.getUserData ()).toList ());
      assertEquals (List.of ("Spy", "Replay", "Terminal", "Test"),
          radios.stream ().map (RadioButton::getText).toList ());
    }

    @Test
    @DisplayName ("Connect e o botao default, Cancel e o de cancelamento")
    void theTwoButtons ()
    {
      OptionStage stage = debugStage ("Terminal");

      assertTrue (button (stage, "Connect").isDefaultButton ());
      assertTrue (button (stage, "Cancel").isCancelButton ());
    }

    @Test
    @DisplayName ("a janela nao e redimensionavel")
    void notResizable ()
    {
      assertFalse (debugStage ("Terminal").isResizable ());
    }

    @Test
    @DisplayName ("o item de menu recebido e o que vai para o menu Commands")
    void theGivenMenuItemIsTheOneInTheMenu ()
    {
      prefs.put ("Mode", "Debug");
      MenuItem given = new MenuItem ("Plugins...");
      OptionStage stage = JavaFxToolkit.onFxThread ( () -> new OptionStage (prefs, given));

      assertSame (given, menuItems (stage).get (1));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("a lista de arquivos de replay")
  class ReplayFiles
  // ---------------------------------------------------------------------------------//
  {
    @TempDir
    Path folder;

    @Test
    @DisplayName ("so entram spyNNNN, com ate quatro digitos e .txt opcional, ordenados")
    void onlySpyFilesInOrder () throws Exception
    {
      for (String name : List.of ("spy0002.txt", "spy1", "SPY0003.TXT", "spy12345",
          "notes.txt", "spy.txt"))
        Files.createFile (folder.resolve (name));

      prefs.put ("SpyFolder", folder.toString ());
      OptionStage stage = debugStage ("Replay");

      assertEquals (List.of ("SPY0003.TXT", "spy0002.txt", "spy1"),
          List.copyOf (combo (stage, "Replay").getItems ()));
    }

    @Test
    @DisplayName ("a preferencia ReplayFile vem selecionada")
    void theSavedReplayFileIsSelected () throws Exception
    {
      Files.createFile (folder.resolve ("spy0001.txt"));
      Files.createFile (folder.resolve ("spy0002.txt"));

      prefs.put ("SpyFolder", folder.toString ());
      prefs.put ("ReplayFile", "spy0002.txt");
      OptionStage stage = debugStage ("Replay");

      assertEquals ("spy0002.txt", combo (stage, "Replay").getValue ());
    }

    /*
     * Sem nada gravado o valor do combo e a STRING VAZIA, e nao null: buildComboBoxes faz
     * select (prefs.get ("ReplayFile", "")), e o modelo de selecao aceita um item que nao
     * esta na lista. Importa porque e com esse valor que o Console monta o caminho do
     * arquivo por concatenacao - o caminho fica "<pasta>/", que nao existe, e a mensagem
     * de erro mostra isso ao usuario. Medido, e nao deduzido: a expectativa inicial deste
     * teste era null, e o codigo a desmentiu.
     */
    @Test
    @DisplayName ("sem pasta gravada a lista e vazia e o valor e a string vazia")
    void noFolderMeansNoFiles ()
    {
      OptionStage stage = debugStage ("Replay");

      assertTrue (combo (stage, "Replay").getItems ().isEmpty ());
      assertEquals ("", combo (stage, "Replay").getValue ());
    }

    @Test
    @DisplayName ("uma pasta inexistente nao quebra a janela")
    void aMissingFolderIsTolerated ()
    {
      prefs.put ("SpyFolder", folder.resolve ("nao-existe").toString ());
      OptionStage stage = debugStage ("Replay");

      assertTrue (combo (stage, "Replay").getItems ().isEmpty ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("os sites, que e o que o Console vai pedir")
  class Sites
  // ---------------------------------------------------------------------------------//
  {
    /*
     * Com um no de Preferences vazio nao ha site gravado, e e nesse estado que o Console
     * produz "No server selected". O que o combo devolve aqui e o que o savePreferences
     * grava de volta, entao o valor importa.
     */
    @ParameterizedTest
    @ValueSource (strings = { "Server", "Client" })
    @DisplayName ("sem sites gravados o combo fica vazio")
    void noSitesMeansAnEmptyCombo (String labelText)
    {
      OptionStage stage = debugStage ("Terminal");

      assertTrue (combo (stage, labelText).getItems ().isEmpty ());
      assertEquals ("", combo (stage, labelText).getSelectionModel ().getSelectedItem ());
    }

    @Test
    @DisplayName ("um site gravado aparece no combo e vem selecionado pela preferencia")
    void aSavedSiteShowsUpAndIsSelected ()
    {
      prefs.put ("Server00Name", "prod");
      prefs.put ("Server00URL", "mvs.example.com");
      prefs.put ("Server00Port", "992");
      prefs.put ("Server00Model", "3");
      prefs.put ("ServerName", "prod");

      OptionStage stage = debugStage ("Terminal");

      assertEquals (List.of ("prod"), List.copyOf (combo (stage, "Server").getItems ()));
      assertEquals ("prod",
          combo (stage, "Server").getSelectionModel ().getSelectedItem ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("o pedido de lancamento")
  class TheLaunchRequest
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest
    @CsvSource ({ "Spy, SPY", "Replay, REPLAY", "Terminal, TERMINAL", "Test, TEST" })
    @DisplayName ("cada opcao produz a funcao correspondente")
    void eachOptionProducesItsFunction (String option, TerminalFunction function)
    {
      assertEquals (function, debugStage (option).getLaunchRequest ().function ());
    }

    @Test
    @DisplayName ("sem sites gravados os dois Optional vem vazios")
    void noSitesMeansTwoEmptyOptionals ()
    {
      LaunchRequest request = debugStage ("Terminal").getLaunchRequest ();

      assertTrue (request.serverSite ().isEmpty ());
      assertTrue (request.clientSite ().isEmpty ());
    }

    @Test
    @DisplayName ("a pasta e o arquivo de replay vem crus, como os widgets os tem")
    void theFolderAndFileComeRaw () throws Exception
    {
      Path folder = Files.createTempDirectory ("dm3270-launch-request");
      try
      {
        Files.createFile (folder.resolve ("spy0007.txt"));
        prefs.put ("SpyFolder", folder.toString ());
        prefs.put ("ReplayFile", "spy0007.txt");

        LaunchRequest request = debugStage ("Replay").getLaunchRequest ();

        assertEquals (folder.toString (), request.spyFolder ());
        assertEquals ("spy0007.txt", request.replayFile ());
      }
      finally
      {
        Files.deleteIfExists (folder.resolve ("spy0007.txt"));
        Files.deleteIfExists (folder);
      }
    }

    @Test
    @DisplayName ("sem arquivo escolhido o pedido leva a string vazia, e nao null")
    void noFileMeansTheEmptyString ()
    {
      assertEquals ("", debugStage ("Replay").getLaunchRequest ().replayFile ());
    }

    /*
     * A afirmacao que protege a decisao central do desenho. O pedido tem de entregar o
     * SiteForm VIVO - o objeto feito de widgets -, e nao uma copia dos seus valores: o
     * getPort () dele corrige o campo quando acha valor invalido, e e o valor corrigido que
     * o SiteListStage grava nas Preferences. Uma copia congelaria a porta antes da correcao
     * e mudaria o que vai para o disco.
     */
    @Test
    @DisplayName ("o Site entregue e o formulario vivo, o mesmo objeto a cada chamada")
    void theSiteIsTheLiveFormAndAlwaysTheSameObject ()
    {
      prefs.put ("Server00Name", "prod");
      prefs.put ("Server00URL", "mvs.example.com");
      prefs.put ("Server00Port", "992");
      prefs.put ("ServerName", "prod");

      OptionStage stage = debugStage ("Terminal");
      Site first = stage.getLaunchRequest ().serverSite ().orElseThrow ();
      Site second = stage.getLaunchRequest ().serverSite ().orElseThrow ();

      assertInstanceOf (SiteForm.class, first);
      assertSame (first, second);
      assertEquals (992, first.getPort ());
    }

    /*
     * A consulta tardia, que o ramo do replay usa depois de descobrir na sessao gravada de
     * que servidor ela veio. Ela nao olha o combo: casa so pelo nome.
     */
    @Test
    @DisplayName ("findServerSite acha pelo nome, mesmo sem nada selecionado no combo")
    void findServerSiteMatchesByNameAlone ()
    {
      prefs.put ("Server00Name", "prod");
      prefs.put ("Server00URL", "mvs.example.com");
      prefs.put ("Server01Name", "homolog");
      prefs.put ("Server01URL", "test.example.com");

      OptionStage stage = debugStage ("Replay");

      assertEquals ("homolog", stage.findServerSite ("homolog").orElseThrow ().getName ());
      assertTrue (stage.findServerSite ("nao-existe").isEmpty ());
    }
  }
}
