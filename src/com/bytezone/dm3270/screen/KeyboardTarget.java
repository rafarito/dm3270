package com.bytezone.dm3270.screen;

/**
 * Porta da tela usada pelo tratador de teclado (ConsoleKeyPress).
 * Expoe clearSelection () em vez de ScreenSelection para evitar dependencia de JavaFX.
 */
// -----------------------------------------------------------------------------------//
public interface KeyboardTarget extends KeyboardState
// -----------------------------------------------------------------------------------//
{
  Cursor getScreenCursor ();

  void clearSelection ();

  void copySelection ();

  void pasteText ();

  void toggleInsertMode ();
}
