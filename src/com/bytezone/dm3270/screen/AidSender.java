package com.bytezone.dm3270.screen;

import com.bytezone.dm3270.commands.AIDCommand;

/** Porta de envio de comandos AID ao host, sem acoplar emissores ao ConsolePane. */
// -----------------------------------------------------------------------------------//
public interface AidSender
// -----------------------------------------------------------------------------------//
{
  void sendAID (byte aid, String name);

  void sendAID (AIDCommand command);
}
