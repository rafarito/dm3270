package com.bytezone.dm3270.application;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import com.bytezone.dm3270.plugins.PluginsStage;
import com.bytezone.dm3270.plugins.RecordingPluginsStage;

import javafx.scene.control.MenuItem;

// Subclasse de teste do Console que substitui OptionStage, PluginsStage e showAlert.
// Sem withLivePreferences(), Console.prefs permanece nulo por nao executar Application.init().
// -----------------------------------------------------------------------------------//
class TestConsole extends Console
// -----------------------------------------------------------------------------------//
{
  final List<String> calls = new ArrayList<> ();
  final List<String> alerts = new ArrayList<> ();

  // Resposta simulada para showAlert (true reabre a janela de opcoes).
  boolean alertAnswer = true;

  private final Preferences prefs;
  private final Path pluginsDirectory;

  RecordingPluginsStage recordedPluginsStage;
  RecordingOptionStage recordedOptionStage;
  MenuItem menuItemGivenToTheOptionStage;

  // ---------------------------------------------------------------------------------//
  TestConsole (Preferences prefs, Path pluginsDirectory)
  // ---------------------------------------------------------------------------------//
  {
    this.prefs = prefs;
    this.pluginsDirectory = pluginsDirectory;
  }

  // Necessario em lancamentos reais porque WindowSaver exige Console.prefs nao nulo.
  // ---------------------------------------------------------------------------------//
  static TestConsole withLivePreferences (Preferences prefs, Path pluginsDirectory)
  // ---------------------------------------------------------------------------------//
  {
    return new TestConsole (prefs, pluginsDirectory, prefs);
  }

  // ---------------------------------------------------------------------------------//
  private TestConsole (Preferences prefs, Path pluginsDirectory, Preferences consolePrefs)
  // ---------------------------------------------------------------------------------//
  {
    super (consolePrefs);

    this.prefs = prefs;
    this.pluginsDirectory = pluginsDirectory;
  }

  // Redireciona PluginsStage para um @TempDir para isolar da pasta local de plugins.
  // ---------------------------------------------------------------------------------//
  @Override
  PluginsStage createPluginsStage ()
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("createPluginsStage");
    recordedPluginsStage = new RecordingPluginsStage (prefs, pluginsDirectory, calls);

    return recordedPluginsStage;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  OptionStage createOptionStage (MenuItem pluginsEditMenuItem)
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("createOptionStage");
    menuItemGivenToTheOptionStage = pluginsEditMenuItem;
    recordedOptionStage = new RecordingOptionStage (prefs, pluginsEditMenuItem, calls);

    return recordedOptionStage;
  }

  // Evita alert.showAndWait(), que bloquearia a execucao automatizada.
  // ---------------------------------------------------------------------------------//
  @Override
  boolean showAlert (String message)
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("showAlert");
    alerts.add (message);

    return alertAnswer;
  }

  // ---------------------------------------------------------------------------------//
  void connect ()
  // ---------------------------------------------------------------------------------//
  {
    recordedOptionStage.onConnect.run ();
  }
}
