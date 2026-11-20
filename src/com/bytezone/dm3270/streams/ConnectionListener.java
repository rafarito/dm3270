package com.bytezone.dm3270.streams;

// Notifica falhas ocorridas na thread do TerminalServer antes da conexao subir.
// -----------------------------------------------------------------------------------//
public interface ConnectionListener
// -----------------------------------------------------------------------------------//
{
  void connectionFailed (String host, int port, String reason);
}
