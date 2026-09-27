package com.bytezone.dm3270.screen;

import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.commands.ReadStructuredFieldCommand;

/*
 * O que a tela devolve quando o host pede para ler: o buffer inteiro, os campos
 * modificados, a resposta a um Read Partition (Query), e o modo em que as proximas
 * respostas vao ser montadas.
 *
 * Consumidores: ReadCommand (readBuffer, readModifiedFields), SetReplyModeSF
 * (setReplyMode) e ReadPartitionQuery (buildQueryReply). Todos sao overrides de
 * Buffer.process, entao recebem o ScreenTarget inteiro; o papel existe para dizer qual
 * parte do contrato eles usam.
 */
// -----------------------------------------------------------------------------------//
public interface HostReplyTarget
// -----------------------------------------------------------------------------------//
{
  AIDCommand readBuffer ();

  AIDCommand readModifiedFields (byte type);

  void setReplyMode (byte replyMode, byte[] replyTypes);

  /*
   * Monta a resposta a um Read Partition (Query). Substitui getTelnetState (): o protocolo
   * pedia o estado da negociacao telnet apenas para construir este comando a partir dele,
   * e era o unico ponto em que o modelo de tela precisava nomear o pacote streams.
   */
  ReadStructuredFieldCommand buildQueryReply ();
}
