package com.bytezone.dm3270.watch;

/*
 * Onde se registra interesse em mudancas de tela.
 *
 * O TransfersStage fazia screen.getFieldManager ().addScreenChangeListener (tsoCommand) - dois
 * saltos para alcancar uma inscricao, atravessando a classe Screen inteira e expondo o
 * FieldManager a quem so queria ser avisado. Era a outra razao pela qual assistant nomeava
 * display.
 *
 * Um metodo so, de proposito: removeScreenChangeListener existe no FieldManager e nao tem
 * chamador nenhum no projeto. Uma porta declara o que se usa.
 */
// -----------------------------------------------------------------------------------//
public interface ScreenChangeSource
// -----------------------------------------------------------------------------------//
{
  void addScreenChangeListener (ScreenChangeListener listener);
}
