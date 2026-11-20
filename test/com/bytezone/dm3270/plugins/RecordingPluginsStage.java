package com.bytezone.dm3270.plugins;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.screen.ScreenDimensions;

// Reexpoe o construtor com pasta de plugins para testes fora do pacote, preservando
// o comportamento e registrando chamadas/dimensoes vistas durante a construcao da Screen.
// -----------------------------------------------------------------------------------//
public class RecordingPluginsStage extends PluginsStage
// -----------------------------------------------------------------------------------//
{
  public final List<String> calls;

  public ScreenDimensions dimensionsSeenDuringConstruction;

  // ---------------------------------------------------------------------------------//
  public RecordingPluginsStage (Preferences prefs, Path pluginsDirectory)
  // ---------------------------------------------------------------------------------//
  {
    this (prefs, pluginsDirectory, new ArrayList<> ());
  }

  // Recebe lista compartilhada quando o teste precisa afirmar ordem entre multiplos dubles.
  // ---------------------------------------------------------------------------------//
  public RecordingPluginsStage (Preferences prefs, Path pluginsDirectory, List<String> calls)
  // ---------------------------------------------------------------------------------//
  {
    super (prefs, pluginsDirectory);

    this.calls = calls;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void closeClassLoader ()
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("pluginsStage.closeClassLoader");

    super.closeClassLoader ();
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public AIDCommand processPluginAuto ()
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("processPluginAuto");

    return super.processPluginAuto ();
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void setScreen (PluginHost screen)
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("setScreen");
    dimensionsSeenDuringConstruction = screen.getScreenDimensions ();

    super.setScreen (screen);
  }
}
