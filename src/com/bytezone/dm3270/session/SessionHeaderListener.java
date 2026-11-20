package com.bytezone.dm3270.session;

// Notifica mudanca no cabecalho da sessao; nao recebe String para que ouvintes
// assincronos leiam getHeaderText() no momento da atualizacao, evitando texto defasado.
// -----------------------------------------------------------------------------------//
public interface SessionHeaderListener
// -----------------------------------------------------------------------------------//
{
  void headerChanged ();
}
