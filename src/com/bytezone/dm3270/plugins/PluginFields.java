package com.bytezone.dm3270.plugins;

import java.util.ArrayList;
import java.util.List;

import com.bytezone.dm3270.screen.Field;

// Converte Field de screen em PluginData mantendo o modelo de tela desacoplado de plugins.
// -----------------------------------------------------------------------------------//
public final class PluginFields
// -----------------------------------------------------------------------------------//
{
  private PluginFields ()
  {
  }

  // ---------------------------------------------------------------------------------//
  public static PluginData toPluginData (int sequence, ScreenLocation cursorLocation,
      List<Field> fields)
  // ---------------------------------------------------------------------------------//
  {
    List<PluginField> pluginFields = new ArrayList<> ();
    int count = 0;

    for (Field field : fields)
      pluginFields.add (toPluginField (field, count++));

    return new PluginData (sequence, cursorLocation, pluginFields);
  }

  // ---------------------------------------------------------------------------------//
  private static PluginField toPluginField (Field field, int fieldSequence)
  // ---------------------------------------------------------------------------------//
  {
    ScreenLocation screenLocation = new ScreenLocation (field.getFirstLocation ());

    return new PluginField (fieldSequence, screenLocation, field.getDisplayLength (),
        field.isProtected (), field.isAlphanumeric (), field.isVisible (),
        field.isModified (), field.getText ());
  }
}
