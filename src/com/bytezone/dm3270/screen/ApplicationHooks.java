package com.bytezone.dm3270.screen;

import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.commands.SystemMessage;

/*
 * Os ganchos de aplicacao que o WriteCommand dispara depois de escrever: gravar a tela,
 * rodar os plugins automaticos e reconhecer as mensagens de sistema (jobs, PROFILE,
 * console).
 *
 * Consumidor unico: WriteCommand.process. Nada disto e protocolo 3270 - e a aplicacao
 * pendurada no fim de um comando de escrita. Tira-los de la e o ciclo C2 do plano; ate
 * la, o papel isola o que vai sair.
 */
// -----------------------------------------------------------------------------------//
public interface ApplicationHooks
// -----------------------------------------------------------------------------------//
{
  void checkRecording ();

  /*
   * Roda os plugins automaticos e devolve a resposta que eles produziram, se produziram.
   * Substitui getPluginsStage (): o protocolo quer o resultado, nao a janela.
   */
  AIDCommand processPluginAuto ();

  SystemMessage getSystemMessage ();
}
