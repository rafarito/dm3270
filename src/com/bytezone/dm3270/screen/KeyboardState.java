package com.bytezone.dm3270.screen;

/*
 * Se o teclado esta travado.
 *
 * Existe por causa de um consumidor so, e por isso e uma porta de verdade e nao teatro de ISP:
 * as abas do assistant guardavam a classe Screen inteira - 1.087 linhas de JavaFX - para
 * decidir se o botao Execute fica habilitado. Era a ultima razao pela qual o pacote assistant
 * nomeava display.
 *
 * ScreenTarget passa a estende-la em vez de declarar o metodo, entao nada muda para o
 * protocolo: quem ja tinha um ScreenTarget continua tendo isKeyboardLocked ().
 */
// -----------------------------------------------------------------------------------//
public interface KeyboardState
// -----------------------------------------------------------------------------------//
{
  boolean isKeyboardLocked ();
}
