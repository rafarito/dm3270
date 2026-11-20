package com.bytezone.dm3270.application;

import java.util.List;
import java.util.prefs.Preferences;

import javafx.scene.control.MenuItem;

// Subclasse de OptionStage que grava transicoes e o gatilho de conexao sem suprimir a UI.
// Stage.show() e final, entao usa showingProperty() para registrar show/hide na ordem real.
// -----------------------------------------------------------------------------------//
class RecordingOptionStage extends OptionStage
// -----------------------------------------------------------------------------------//
{
  private final List<String> calls;

  Runnable onConnect;

  // ---------------------------------------------------------------------------------//
  RecordingOptionStage (Preferences prefs, MenuItem pluginsEditMenuItem, List<String> calls)
  // ---------------------------------------------------------------------------------//
  {
    super (prefs, pluginsEditMenuItem);

    this.calls = calls;

    showingProperty ().addListener ( (obs, wasShowing, isShowing) ->
        calls.add (isShowing ? "optionStage.show" : "optionStage.hide"));
  }

  // ---------------------------------------------------------------------------------//
  @Override
  void savePreferences ()
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("optionStage.savePreferences");

    super.savePreferences ();
  }

  // ---------------------------------------------------------------------------------//
  @Override
  void setOnConnect (Runnable action)
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("optionStage.setOnConnect");
    onConnect = action;

    super.setOnConnect (action);
  }
}
