package com.bytezone.dm3270.streams;

import com.bytezone.dm3270.screen.ScreenTarget;

// Porta de tela para a camada de sockets: processamento de comandos e escrita de resumo.
// -----------------------------------------------------------------------------------//
public interface SessionDisplay extends ScreenTarget
// -----------------------------------------------------------------------------------//
{
  void displayText (String text);
}
