package com.bytezone.dm3270.screen;

import java.util.function.Consumer;

import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.commands.SystemMessage;

/*
 * O que a aplicacao pendura no fim de um comando de escrita: o aviso de que o host terminou
 * de escrever e as mensagens de sistema (jobs, PROFILE, console).
 *
 * Consumidor unico: WriteCommand.process. Nada disto e protocolo 3270. O comando nao decide
 * mais quando gravar a tela nem quando rodar os plugins - quem implementa a tela decide, e a
 * Screen e o dublê decidem pela mesma HostWriteCompletion.
 */
// -----------------------------------------------------------------------------------//
public interface ApplicationHooks
// -----------------------------------------------------------------------------------//
{
  /*
   * O host terminou de escrever e o WCC ja foi aplicado; a tela ainda nao foi redesenhada.
   *
   * freshContent: a escrita trouxe orders, ou o WCC nao zerou os MDT. E o fato de protocolo
   * que so o comando conhece.
   *
   * reply: recebe a resposta dos plugins automaticos quando eles rodam - nula inclusive,
   * que apaga a resposta anterior do comando. Quando nao rodam, nao e chamado.
   */
  void hostWriteCompleted (boolean freshContent, Consumer<AIDCommand> reply);

  SystemMessage getSystemMessage ();
}
