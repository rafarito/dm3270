package com.bytezone.dm3270.screen;

import com.bytezone.dm3270.attributes.TerminalColor;

/** Abstracao de desenho da tela sem JavaFX; a conversao de cores ocorre na implementacao. */
// -----------------------------------------------------------------------------------//
public interface ScreenCanvas
// -----------------------------------------------------------------------------------//
{
  void setFill (TerminalColor color);

  void setStroke (TerminalColor color);

  void fillRect (double x, double y, double width, double height);

  void fillText (String text, double x, double y);

  void strokeLine (double x1, double y1, double x2, double y2);
}
