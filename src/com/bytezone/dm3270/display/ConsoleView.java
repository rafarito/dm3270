package com.bytezone.dm3270.display;

import com.bytezone.dm3270.screen.AidSender;
import com.bytezone.dm3270.screen.KeyboardStatusListener;

/*
 * O que a Screen precisa do painel que a contem.
 *
 * Screen guardava um ConsolePane concreto e usava tres coisas dele: avisar que a sessao entrou
 * em modo console, escrever no status e remontar a fonte do status quando a fonte da tela
 * muda. Tres, de uma classe de 432 linhas que e a janela inteira - e era a ultima razao pela
 * qual o pacote display nomeava o pacote application.
 *
 * SOBRE OS DOIS extends: eles nao descrevem o que a Screen usa, e sim o que ela REPASSA. A
 * Screen entrega o painel a tres lugares - TransfersStage e TransferMenu, que precisam mandar
 * AID, e a propria lista de ouvintes de teclado - porque ela constroi o grafo de objetos
 * inteiro no construtor e acabou sendo o caminho por onde o painel chega a todo mundo. Nao ha
 * razao de projeto nisso.
 *
 * Os consumidores, esses sim, ficaram estreitos: assistant, filetransfer e plugins passaram a
 * depender de AidSender, duas assinaturas, em vez das 432 linhas de JavaFX do ConsolePane.
 * Quando o composition root da onda 3 assumir a fiacao, ConsoleView perde os dois extends e
 * fica so com os tres metodos de status.
 */
// -----------------------------------------------------------------------------------//
public interface ConsoleView extends AidSender, KeyboardStatusListener
// -----------------------------------------------------------------------------------//
{
  void setIsConsole (boolean value);

  void setStatusText (String text);

  void setStatusFont ();
}
