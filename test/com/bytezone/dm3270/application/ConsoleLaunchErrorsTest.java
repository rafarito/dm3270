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

// Usa Mode=Debug nas Preferences porque Release forca Terminal e desabilita Spy/Replay/Test.
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

    // O caminho e concatenado com "/" antes de virar Path, normalizando o separador do SO.
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
