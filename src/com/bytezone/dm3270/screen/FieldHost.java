package com.bytezone.dm3270.screen;

/** Visao da tela usada por Field e FieldManager para desacoplar o modelo da view JavaFX. */
// -----------------------------------------------------------------------------------//
public interface FieldHost extends DisplayScreen
// -----------------------------------------------------------------------------------//
{
  void drawPosition (int position, boolean hasCursor);

  Cursor getScreenCursor ();
}
