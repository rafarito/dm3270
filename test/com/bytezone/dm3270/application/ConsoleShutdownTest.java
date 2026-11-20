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

// Sem sessao lancada, stop() exercita apenas optionStage.savePreferences e closeClassLoader.
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

  // Como Console.prefs e nulo em TestConsole, a ausencia da guarda de screen nulo causaria
  // NullPointerException em vez de escrever chaves no no do teste.
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

  // JavaFX chama Application.stop() mesmo se start() falhar antes de criar optionStage.
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
