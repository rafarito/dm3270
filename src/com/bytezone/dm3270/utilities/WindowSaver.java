package com.bytezone.dm3270.utilities;

import java.util.prefs.Preferences;

import javafx.stage.Stage;

// -----------------------------------------------------------------------------------//
public class WindowSaver
// -----------------------------------------------------------------------------------//
{
  private final Preferences prefs;
  private final Stage stage;
  private final String windowId;

  // ---------------------------------------------------------------------------------//
  public WindowSaver (Preferences prefs, Stage stage, String windowId)
  // ---------------------------------------------------------------------------------//
  {
    this.prefs = prefs;
    this.stage = stage;
    this.windowId = windowId;
  }

  // ---------------------------------------------------------------------------------//
  public void saveWindow ()
  // ---------------------------------------------------------------------------------//
  {
    prefs.putDouble (windowId + "X", stage.getX ());
    prefs.putDouble (windowId + "Y", stage.getY ());
    prefs.putDouble (windowId + "Height", stage.getHeight ());
    prefs.putDouble (windowId + "Width", stage.getWidth ());
  }

  // ---------------------------------------------------------------------------------//
  public boolean restoreWindow ()
  // ---------------------------------------------------------------------------------//
  {
    Double x = prefs.getDouble (windowId + "X", -1.0);
    Double y = prefs.getDouble (windowId + "Y", -1.0);
    Double height = prefs.getDouble (windowId + "Height", -1.0);
    Double width = prefs.getDouble (windowId + "Width", -1.0);

    if (width < 0)                // nothing to restore
    {
      stage.centerOnScreen ();
      return false;
    }

    stage.setX (x);
    stage.setY (y);
    stage.setHeight (height);
    stage.setWidth (width);

    return true;
  }
}