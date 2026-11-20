package com.bytezone.dm3270.screen;

// -----------------------------------------------------------------------------------//
public interface WriteControlTarget
// -----------------------------------------------------------------------------------//
{
  void resetInsertMode ();

  void resetPartition ();

  void startPrinter ();

  void soundAlarm ();

  void resetModified ();

  void restoreKeyboard ();
}
