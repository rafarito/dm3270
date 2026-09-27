package com.bytezone.dm3270.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
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

import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.screen.ScreenPosition;
import com.bytezone.dm3270.testing.JavaFxToolkit;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.stage.Stage;
import javafx.stage.Window;

/*
 * O PRIMEIRO CAMINHO FELIZ DO LANCAMENTO OBSERVADO PELO EFEITO, e nao so pela decisao.
 *
 * O passo 12 deu 22 casos aos quatro caminhos felizes, e todos afirmam DECISAO e ORDEM contra
 * um gravador da porta LaunchTarget: ninguem afirmava que o ConsolePane nasceu, que a janela
 * apareceu ou que a tela recebeu alguma coisa. Este caso afirma, com o Console, a Screen, o
 * ConsolePane e a ReplayStage REAIS. O que o tornou possivel foi o construtor de pacote
 * Console (Preferences), do commit anterior.
 *
 * POR QUE SO O REPLAY, e e honesto dizer: e o unico dos quatro ramos que nao abre socket.
 * TERMINAL conecta no host (connectConsole), SPY sobe um servidor (spyPane.startServer) e TEST
 * faz os dois. Afirma-los por efeito exige um host falso, e isso e outro trabalho.
 *
 * E o REPLAY e justamente o ramo com a dependencia de ordem mais escondida: a ReplayStage
 * recebe o campo screen que showConsole acabou de atribuir, e nenhuma assinatura diz isso. O
 * caso central abaixo afirma pelo efeito: a tela que esta DENTRO da janela principal e a que
 * recebeu a primeira tela da sessao gravada. Inverter as duas chamadas entregaria a ReplayStage
 * uma tela nula, ou a de um lancamento anterior, e a da janela ficaria em branco.
 *
 * A sessao e o mf.txt, o mesmo do golden master. O servidor gravado nele, "FanDeZhi", nao
 * esta na lista de sites, entao o console nasce sem site - e portanto com DatasetStore.NONE,
 * sem abrir SQLite no diretorio corrente.
 *
 * DUAS JANELAS ABREM DE VERDADE - Stage.show () e final - e o @AfterEach fecha as tres. O
 * JavaFxToolkit ja desliga o implicitExit, sem o que fechar a ultima derrubaria o toolkit para
 * o resto da JVM.
 */
// -----------------------------------------------------------------------------------//
@ExtendWith (JavaFxToolkit.class)
@DisplayName ("Console - o lancamento do replay, observado pelo efeito")
class ConsoleReplayLaunchTest
// -----------------------------------------------------------------------------------//
{
  private static final String SESSION_RESOURCE = "com/bytezone/dm3270/application/mf.txt";

  @TempDir
  private Path pluginsDirectory;

  @TempDir
  private Path spyFolder;

  private Preferences prefs;
  private TestConsole console;
  private Stage primaryStage;

  // ---------------------------------------------------------------------------------//
  @BeforeEach
  void launchTheReplay () throws Exception
  // ---------------------------------------------------------------------------------//
  {
    try (InputStream in = getClass ().getClassLoader ().getResourceAsStream (SESSION_RESOURCE))
    {
      assertNotNull (in, SESSION_RESOURCE);
      Files.copy (in, spyFolder.resolve ("mf.txt"));
    }

    prefs = Preferences.userRoot ().node ("dm3270-test/" + UUID.randomUUID ());
    prefs.put ("Mode", "Debug");
    prefs.put ("Function", "Replay");
    prefs.put ("SpyFolder", spyFolder.toString ());
    prefs.put ("ReplayFile", "mf.txt");

    console = TestConsole.withLivePreferences (prefs, pluginsDirectory);

    primaryStage = JavaFxToolkit.onFxThread ( () ->
    {
      Stage stage = new Stage ();
      console.start (stage);
      console.connect ();
      return stage;
    });
  }

  // ---------------------------------------------------------------------------------//
  @AfterEach
  void closeEverything () throws Exception
  // ---------------------------------------------------------------------------------//
  {
    JavaFxToolkit.onFxThread ( () ->
    {
      for (Window window : List.copyOf (Window.getWindows ()))
        window.hide ();
      return null;
    });

    console.recordedPluginsStage.closeClassLoader ();

    prefs.removeNode ();
    prefs.flush ();

    // ScreenDimensions escreve BufferAddress.setScreenWidth, estado estatico global.
    new ScreenDimensions (24, 80);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("nao ha alerta: o lancamento foi aceito")
  void noAlert ()
  // ---------------------------------------------------------------------------------//
  {
    assertEquals (List.of (), console.alerts);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("a janela principal aparece com o ConsolePane, e a de opcoes some")
  void theConsoleWindowIsShown ()
  // ---------------------------------------------------------------------------------//
  {
    assertTrue (primaryStage.isShowing ());
    assertEquals ("dm3270", primaryStage.getTitle ());
    assertInstanceOf (ConsolePane.class, primaryStage.getScene ().getRoot ());

    assertTrue (!console.recordedOptionStage.isShowing (), "a janela de opcoes continuou aberta");
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("a janela de replay aparece, com o nome do arquivo no titulo")
  void theReplayWindowIsShown ()
  // ---------------------------------------------------------------------------------//
  {
    List<ReplayStage> replays = Window.getWindows ().stream ()
        .filter (ReplayStage.class::isInstance).map (ReplayStage.class::cast).toList ();

    assertEquals (1, replays.size (), "janelas de replay abertas");
    assertTrue (replays.get (0).isShowing ());
    assertEquals ("Replay Commands - mf.txt", replays.get (0).getTitle ());
  }

  /*
   * A dependencia de ordem do ramo do replay, afirmada pelo efeito. A ReplayStage processa a
   * primeira tela da sessao na tela que recebeu; se ela recebesse outra tela que nao a da
   * janela principal, esta estaria em branco.
   */
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("a tela DENTRO da janela principal e a que recebeu a sessao gravada")
  void theReplayDrawsOnTheScreenOfTheConsoleWindow ()
  // ---------------------------------------------------------------------------------//
  {
    Screen screen = find (Screen.class, primaryStage.getScene ().getRoot ());
    assertNotNull (screen, "a Screen nao esta no grafo de cena do ConsolePane");

    long written = 0;
    for (ScreenPosition position : screen.getScreenPositions ())
      if (position.getChar () != ' ')
        written++;

    assertTrue (written > 0, "a tela da janela principal ficou em branco");
  }

  // ---------------------------------------------------------------------------------//
  private static <T> T find (Class<T> type, Node node)
  // ---------------------------------------------------------------------------------//
  {
    if (type.isInstance (node))
      return type.cast (node);

    if (node instanceof Parent parent)
      for (Node child : parent.getChildrenUnmodifiable ())
      {
        T found = find (type, child);
        if (found != null)
          return found;
      }

    return null;
  }
}
