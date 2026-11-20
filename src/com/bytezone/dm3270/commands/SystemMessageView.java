package com.bytezone.dm3270.commands;

/** Porta de exibicao usada por SystemMessage para manter o protocolo sem JavaFX. */
// -----------------------------------------------------------------------------------//
public interface SystemMessageView
// -----------------------------------------------------------------------------------//
{
  // Texto cru da tela TSO PROFILE: text1 traz CHAR/LINE/PREFIX e text2 traz o restante.
  void showProfile (String profileMessageText1, String profileMessageText2);

  // Chamado uma vez ao reconhecer modo console (IPL) para abrir o destino das linhas.
  ConsoleLines openConsoleLog ();
}
