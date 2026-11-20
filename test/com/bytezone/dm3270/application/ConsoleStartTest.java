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
    // Fecha a OptionStage real (Stage.show e final) para nao acumular janelas na suite.
    JavaFxToolkit.onFxThread ( () ->
    {
      console.recordedOptionStage.hide ();
      return null;
    });

    console.recordedPluginsStage.closeClassLoader ();

    prefs.removeNode ();
    prefs.flush ();
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("monta os dois Stage, liga o gatilho e SO ENTAO mostra a janela")
  void assemblesInOrder ()
  // ---------------------------------------------------------------------------------//
  {
    assertEquals (List.of ("createPluginsStage", "createOptionStage",
        "optionStage.setOnConnect", "optionStage.show"), console.calls);
  }

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

  // Nao disparar este handler: ele chama Platform.exit() e encerra o toolkit na JVM.
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
