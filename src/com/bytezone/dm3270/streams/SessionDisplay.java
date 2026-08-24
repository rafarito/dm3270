package com.bytezone.dm3270.streams;

import com.bytezone.dm3270.screen.ScreenTarget;

/*
 * O que a camada de sockets precisa da tela.
 *
 * TelnetListener e SpyServer guardavam a classe Screen concreta. O TelnetListener usa a tela
 * de dois jeitos: como ScreenTarget, para entregar cada Command e cada TelnetCommand montado
 * ao process (); e uma unica vez como widget, em close (), para escrever na tela o resumo da
 * sessao quando o socket cai. So por causa dessa segunda coisa - uma chamada - o pacote
 * streams inteiro nomeava display, e como a Screen precisa nomear TelnetState de volta o par
 * display <-> streams era ciclo mutuo.
 *
 * ScreenTarget ja cobre a primeira parte e mora em screen, que nao conhece streams. Esta
 * porta acrescenta a segunda, e esta declarada aqui, no lado que consome.
 *
 * O construtor de modo TERMINAL do TelnetListener perguntava a funcao a tela
 * (screen.getFunction ()). Agora recebe a TerminalFunction de quem constroi, que e o
 * composition root e ja a tem em maos - era a unica outra coisa que ele pedia ao widget.
 */
// -----------------------------------------------------------------------------------//
public interface SessionDisplay extends ScreenTarget
// -----------------------------------------------------------------------------------//
{
  void displayText (String text);
}
