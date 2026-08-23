package com.bytezone.dm3270.screen;

/*
 * O que um campo precisa da tela onde ele vive.
 *
 * Field e FieldManager guardavam um Screen concreto - a classe de 1.032 linhas que estende
 * javafx.scene.canvas.Canvas e constroi o grafo de objetos da aplicacao inteira. Field usava
 * quatro coisas dela: as dimensoes, validar uma posicao, achar o cursor e redesenhar uma
 * posicao. Quatro, de 78 metodos. FieldManager usava duas: as dimensoes e repassar a tela ao
 * construtor de Field.
 *
 * Esta porta e o que permite o modelo de tela sair do pacote da interface. Sem ela, Field
 * arrasta Screen, Screen arrasta o JavaFX, e separar o modelo da view nao muda nada: o
 * modelo continuaria dependendo do pacote que contem o widget. A medicao foi feita: eram as
 * unicas arestas do modelo para a view, tirando HistoryManager, que e view de verdade porque
 * guarda uma lista de Canvas.
 *
 * Segue o padrao de CursorHost - o que o CURSOR pede a tela - e nao se confunde com ele:
 * CursorHost tem isInsertMode, getHomeField e getFieldAt, que um campo nao usa. As duas
 * estendem DisplayScreen, de onde vem as dimensoes e o validate, e Screen implementa ambas.
 */
// -----------------------------------------------------------------------------------//
public interface FieldHost extends DisplayScreen
// -----------------------------------------------------------------------------------//
{
  void drawPosition (int position, boolean hasCursor);

  Cursor getScreenCursor ();
}
