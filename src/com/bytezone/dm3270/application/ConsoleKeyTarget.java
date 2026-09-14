package com.bytezone.dm3270.application;

import com.bytezone.dm3270.screen.AidSender;

/*
 * O que o tratador de teclado faz com o console.
 *
 * O ConsoleKeyPress guardava o ConsolePane concreto - 442 linhas que estendem
 * javafx.scene.layout.BorderPane, montam menu, barra de status e o canvas no construtor, e
 * leem as preferencias do disco - e usava tres metodos dele: sendAID (byte, String), back () e
 * forward ().
 *
 * O sendAID ja tinha porta, a screen.AidSender, que o ConsolePane implementa por via da
 * display.ConsoleView. Esta interface a estende e acrescenta as duas que faltavam, em vez de
 * alargar a AidSender: quem manda AID - o assistant, o filetransfer, os plugins - nao tem nada
 * que ver com navegar o historico de telas, e alargar a porta poria cinco classes a implementar
 * dois metodos que nao usam.
 *
 * back () e forward () eram PACKAGE-PRIVATE, e o unico motivo de compilarem era o
 * ConsoleKeyPress morar no mesmo pacote. Implementar uma interface os torna publicos, e isso e
 * passivo de visibilidade - o mesmo balanco da onda 3: o que se perde e acesso ACIDENTAL de
 * vizinho de pacote, e o que se ganha e contrato nomeado, com um implementador so e um chamador
 * so no repositorio inteiro.
 *
 * A porta e declarada aqui, no pacote que consome, porque quem a implementa tambem esta aqui -
 * o ConsolePane e de application, nao de display. E o padrao da refatoracao no seu caso mais
 * simples, sem risco de camada nenhum.
 */
// -----------------------------------------------------------------------------------//
interface ConsoleKeyTarget extends AidSender
// -----------------------------------------------------------------------------------//
{
  void back ();

  void forward ();
}
