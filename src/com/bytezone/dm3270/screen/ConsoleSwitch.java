package com.bytezone.dm3270.screen;

/*
 * Avisa a tela de que a sessao e o console do sistema, e nao um terminal TSO.
 *
 * Consumidor unico: SystemMessage, que guarda a tela so para isto e so chama quando
 * reconhece o IEA371I de IPL. Por isso o campo dele nomeia este papel, e nao o
 * ScreenTarget inteiro.
 */
// -----------------------------------------------------------------------------------//
public interface ConsoleSwitch
// -----------------------------------------------------------------------------------//
{
  void setIsConsole ();
}
