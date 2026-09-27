package com.bytezone.dm3270.screen;

/*
 * Avisa a tela de que a sessao e o console do sistema, e nao um terminal TSO.
 *
 * Consumidor unico: SystemMessage, que guarda a tela so para isto e so chama quando
 * reconhece o IEA371I de IPL.
 */
// -----------------------------------------------------------------------------------//
public interface ConsoleSwitch
// -----------------------------------------------------------------------------------//
{
  void setIsConsole ();
}
