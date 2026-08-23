package com.bytezone.dm3270.screen;

import com.bytezone.dm3270.commands.AIDCommand;

/*
 * Para quem se manda um AID.
 *
 * Cinco classes espalhadas por assistant, display, filetransfer e plugins guardavam um
 * ConsolePane - a barra de menu, o status, o canvas, 432 linhas de JavaFX - e usavam duas
 * coisas dele: sendAID (byte, String) e sendAID (AIDCommand). O ConsolePane era o unico tipo
 * do pacote application que essas quatro camadas importavam, e por isso o composition root da
 * aplicacao aparecia em quatro ciclos mutuos.
 *
 * Mandar um AID e a acao que fecha o ciclo de uma tela 3270: o usuario aperta ENTER ou uma PF,
 * a tela monta o comando e alguem o escreve no socket. Quem escreve, hoje, e o ConsolePane -
 * mas isso e detalhe de quem implementa, nao do contrato.
 */
// -----------------------------------------------------------------------------------//
public interface AidSender
// -----------------------------------------------------------------------------------//
{
  void sendAID (byte aid, String name);

  void sendAID (AIDCommand command);
}
