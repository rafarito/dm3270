package com.bytezone.dm3270.display;

/*
 * Qual das duas geometrias de tela esta em uso: a default negociada no telnet ou a
 * alternativa que o host pede num Erase Write Alternate.
 *
 * Era um enum aninhado em Screen. Subiu para o topo porque aparece na assinatura de
 * ScreenTarget, e mante-lo dentro de Screen faria o protocolo continuar importando a
 * classe concreta so para nomear a constante - exatamente o acoplamento que ScreenTarget
 * existe para cortar.
 */
// -----------------------------------------------------------------------------------//
public enum ScreenOption
// -----------------------------------------------------------------------------------//
{
  DEFAULT, ALTERNATE
}
