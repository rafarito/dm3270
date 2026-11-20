package com.bytezone.dm3270.session;

// Ouvinte de novos registros da sessao; no modo Spy, e chamado na thread do socket.
// -----------------------------------------------------------------------------------//
public interface SessionRecordListener
// -----------------------------------------------------------------------------------//
{
  void recordAdded (SessionRecord sessionRecord);
}
