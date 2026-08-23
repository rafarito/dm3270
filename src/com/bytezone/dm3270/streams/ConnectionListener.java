package com.bytezone.dm3270.streams;

/*
 * Avisa quem pediu a conexao que ela nao subiu.
 *
 * Sem isto a falha morre na thread do TerminalServer: ela e registrada no log, o socket e
 * fechado, e a interface continua achando que o terminal conectou - a janela abre em branco
 * e o usuario nao recebe explicacao nenhuma. Era o que acontecia ao apontar para um host
 * inacessivel.
 */
// -----------------------------------------------------------------------------------//
public interface ConnectionListener
// -----------------------------------------------------------------------------------//
{
  void connectionFailed (String host, int port, String reason);
}
