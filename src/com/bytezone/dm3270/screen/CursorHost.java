package com.bytezone.dm3270.screen;

import java.util.Optional;

/*
 * O que o cursor precisa da tela onde ele anda.
 *
 * Cursor guardava um Screen concreto e usava seis coisas dele: ler e validar posicoes,
 * redesenhar uma posicao, saber se o modo de insercao esta ligado, achar o campo inicial e
 * achar o campo numa posicao. Seis, de 78 metodos.
 *
 * Separar esta porta e o que permite existir uma tela headless: sem ela, qualquer dublê de
 * ScreenTarget teria de devolver um Cursor, e Cursor so podia ser construido a partir da
 * Screen do JavaFX - o que fecharia o circulo e deixaria commands intestavel apesar de toda
 * a onda anterior.
 *
 * Nao e a mesma coisa que ScreenTarget de proposito: aquele e o que o PROTOCOLO pede a tela,
 * este e o que o CURSOR pede. As duas se sobrepoem em getFieldAt e no que vem de
 * DisplayScreen, e Screen implementa as duas.
 */
// -----------------------------------------------------------------------------------//
public interface CursorHost extends DisplayScreen
// -----------------------------------------------------------------------------------//
{
  void drawPosition (int position, boolean hasCursor);

  boolean isInsertMode ();

  Optional<Field> getHomeField ();

  Optional<Field> getFieldAt (int position);
}
