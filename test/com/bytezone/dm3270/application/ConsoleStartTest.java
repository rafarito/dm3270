package com.bytezone.dm3270.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * A primeira caracterizacao de Console.start (Stage) - o ponto de entrada da aplicacao, que
 * ate este commit nao tinha prova nenhuma.
 *
 * O QUE ELE CONGELA: a ORDEM em que os dois Stage nascem, que o MenuItem entregue ao
 * OptionStage e o do PluginsStage, que o gatilho e ligado ANTES de a janela aparecer, e que
 * o start nao constroi mais nada - nem tela, nem painel, nem sessao.
 *
 * A ARMADILHA MAIS PERIGOSA DESTA CLASSE, e ela nao e sutil: Platform.exit () e porta de mao
 * unica. O start instala primaryStage.setOnCloseRequest (e -> Platform.exit ())
 * (Console.java:73), o construtor do OptionStage instala o mesmo (:203) e ainda poe um Quit
 * no menu (:195-199). O JavaFxToolkit:39-40 registra que o toolkit NAO religa na mesma JVM -
 * disparar qualquer um dos tres mataria todo teste de JavaFX que rodasse depois, em classes
 * sem relacao nenhuma com este passo. Por isso o caso abaixo afirma que o handler EXISTE e
 * nunca o dispara.
 *
 * A JANELA DE OPCOES APARECE DE VERDADE, e nao ha como evitar: Stage.show () e FINAL -
 * conferido com javap no javafx-graphics-21.0.7 -, entao o gravador nao pode suprimi-la. Ele
 * observa a showingProperty (), que dispara de forma sincrona dentro do proprio show (), e o
 * @AfterEach fecha a janela. Num ambiente headless rode sob xvfb-run.
 *
 * O primaryStage, esse, nao e mostrado: quem o mostra e setConsolePane e setSpyPane, que
 * ficam atras da Screen concreta e portanto fora do alcance deste passo.
 */
// -----------------------------------------------------------------------------------//
@ExtendWith (JavaFxToolkit.class)
@DisplayName ("Console.start - a montagem da janela de abertura")
class ConsoleStartTest
// -----------------------------------------------------------------------------------//
{
  @TempDir
  private Path pluginsDirectory;

  private Preferences prefs;
  private TestConsole console;
  private Stage primaryStage;

  // ---------------------------------------------------------------------------------//
  @BeforeEach
  void startTheConsole ()
  // ---------------------------------------------------------------------------------//
  {
    prefs = Preferences.userRoot ().node ("dm3270-test/" + UUID.randomUUID ());
    console = new TestConsole (prefs, pluginsDirectory);

    primaryStage = JavaFxToolkit.onFxThread ( () ->
    {
      Stage stage = new Stage ();
      console.start (stage);

      return stage;
    });
  }

  // ---------------------------------------------------------------------------------//
  @AfterEach
  void removePreferencesNode () throws Exception
  // ---------------------------------------------------------------------------------//
  {
    // Stage.show () e final, entao a janela apareceu de verdade. Window.hide () NAO e final,
    // e e o unico jeito de fecha-la. Sem isto a suite acumula janelas abertas.
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
   * A ordem inteira do start, numa asserticao so. Ela e a lista COMPLETA, e nao um contains:
   * e isso que faz o caso falhar se o start passar a construir qualquer outra coisa.
   */
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("monta os dois Stage, liga o gatilho e SO ENTAO mostra a janela")
  void assemblesInOrder ()
  // ---------------------------------------------------------------------------------//
  {
    assertEquals (List.of ("createPluginsStage", "createOptionStage",
        "optionStage.setOnConnect", "optionStage.show"), console.calls);
  }

  /*
   * O PluginsStage nasce primeiro porque o OptionStage precisa do item de menu dele - e o
   * unico acoplamento entre os dois, e e por valor.
   */
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("o item de menu entregue ao OptionStage e o do PluginsStage")
  void handsOverThePluginsEditMenuItem ()
  // ---------------------------------------------------------------------------------//
  {
    assertSame (console.recordedPluginsStage.getEditMenuItem (),
        console.menuItemGivenToTheOptionStage);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("o gatilho do lancamento fica ligado, e e um Runnable vivo")
  void wiresTheLaunchTrigger ()
  // ---------------------------------------------------------------------------------//
  {
    assertNotNull (console.recordedOptionStage.onConnect);
  }

  /*
   * NUNCA dispare este handler. Ver o cabecalho da classe: ele chama Platform.exit ().
   */
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("a janela principal fica redimensionavel e com o handler de fechamento")
  void configuresThePrimaryStage ()
  // ---------------------------------------------------------------------------------//
  {
    assertTrue (primaryStage.isResizable ());
    assertNotNull (primaryStage.getOnCloseRequest ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("a janela principal NAO e mostrada pelo start")
  void doesNotShowThePrimaryStage ()
  // ---------------------------------------------------------------------------------//
  {
    assertFalse (primaryStage.isShowing ());
  }

  /*
   * O start nao constroi Screen, ConsolePane, SpyPane nem Session - todos esses estao dentro
   * dos ramos do switch, e o switch so roda quando o gatilho e disparado. A prova e que o
   * lancamento ainda nao aconteceu: nenhum alerta, e a lista de chamadas nao cresceu.
   */
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("o start nao lanca nada - nenhum ramo do switch roda")
  void doesNotLaunchAnything ()
  // ---------------------------------------------------------------------------------//
  {
    assertEquals (List.of (), console.alerts);
    assertEquals (4, console.calls.size ());
  }
}
