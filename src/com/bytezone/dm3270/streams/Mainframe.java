package com.bytezone.dm3270.streams;

import com.bytezone.dm3270.commands.Command;

// Porta de saida para onde o MainframeServer entrega comandos recebidos do cliente.
// -----------------------------------------------------------------------------------//
public interface Mainframe
// -----------------------------------------------------------------------------------//
{
  public void receiveCommand (Command command);
}
