package com.bytezone.dm3270.screen;

import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.commands.ReadStructuredFieldCommand;

/** Papel da tela para comandos de leitura e configuracao de resposta do host. */
// -----------------------------------------------------------------------------------//
public interface HostReplyTarget
// -----------------------------------------------------------------------------------//
{
  AIDCommand readBuffer ();

  AIDCommand readModifiedFields (byte type);

  void setReplyMode (byte replyMode, byte[] replyTypes);

  ReadStructuredFieldCommand buildQueryReply ();
}
