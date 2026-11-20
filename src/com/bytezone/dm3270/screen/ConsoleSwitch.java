package com.bytezone.dm3270.screen;

/** Marca a sessao como console do sistema (acionado por SystemMessage ao detectar IEA371I). */
// -----------------------------------------------------------------------------------//
public interface ConsoleSwitch
// -----------------------------------------------------------------------------------//
{
  void setIsConsole ();
}
