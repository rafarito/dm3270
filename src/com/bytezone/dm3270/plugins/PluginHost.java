package com.bytezone.dm3270.plugins;

import java.util.List;
import java.util.Optional;

import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.screen.Cursor;
import com.bytezone.dm3270.screen.Field;
import com.bytezone.dm3270.screen.ScreenDimensions;

// Porta da tela exigida pelo subsistema de plugins, evitando dependencia de display/JavaFX.
// -----------------------------------------------------------------------------------//
public interface PluginHost
// -----------------------------------------------------------------------------------//
{
  ScreenDimensions getScreenDimensions ();

  boolean isKeyboardLocked ();

  Cursor getScreenCursor ();

  List<Field> getFields ();

  Optional<Field> getFieldAt (int position);

  void lockKeyboard (String keyName);

  void setAID (byte aid);

  AIDCommand readModifiedFields ();
}
