package com.bytezone.dm3270.display;

import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.screen.ScreenPosition;

// Porta de ScreenSelection para a tela. Implementada por adaptador privado em Screen
// porque implementar na propria Screen tornaria publicos redrawRange e redrawSelection.
// -----------------------------------------------------------------------------------//
interface SelectionHost
// -----------------------------------------------------------------------------------//
{
  ScreenDimensions getScreenDimensions ();

  ScreenPosition getScreenPosition (int position);

  void redrawRange (int from, int to);

  void redrawSelection (int oldMin, int oldMax, int newMin, int newMax);
}
