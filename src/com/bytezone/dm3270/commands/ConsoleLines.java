package com.bytezone.dm3270.commands;

/** Destino das linhas de console recortadas pelo protocolo em SystemMessage. */
// -----------------------------------------------------------------------------------//
public interface ConsoleLines
// -----------------------------------------------------------------------------------//
{
  // As linhas do IPL, reconhecidas pelo codigo de mensagem no inicio de cada uma.
  void addLines1 (String[] lines, int firstLine, int lastLine);

  // As linhas do console, reconhecidas pelo horario.
  void addLines2 (String[] lines, int firstLine, int lastLine);
}
