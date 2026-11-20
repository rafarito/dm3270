package com.bytezone.dm3270.screen;

import java.util.Optional;

/** Visao da tela exigida por Cursor, permitindo instanciar o cursor sem depender do JavaFX. */
// -----------------------------------------------------------------------------------//
public interface CursorHost extends DisplayScreen
// -----------------------------------------------------------------------------------//
{
  void drawPosition (int position, boolean hasCursor);

  boolean isInsertMode ();

  Optional<Field> getHomeField ();

  Optional<Field> getFieldAt (int position);
}
