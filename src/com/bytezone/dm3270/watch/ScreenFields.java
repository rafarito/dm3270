package com.bytezone.dm3270.watch;

import java.util.List;

import com.bytezone.dm3270.screen.Field;

/*
 * O que o observador de tela precisa ler dos campos.
 *
 * O ScreenWatcher guardava o FieldManager concreto, e o FieldManager mora em display. Como
 * display precisa nomear o ScreenWatcher de volta - o FieldManager e quem o constroi e quem
 * dispara o screenChanged -, o par ia continuar amarrado onde quer que o observador fosse
 * parar. A porta esta declarada AQUI, no lado que consome, que e o que inverte a dependencia.
 *
 * Sao sete metodos, levantados do codigo e nao imaginados: getFields e getMenus para
 * reconhecer a tela, os dois getRowFields para ler linha a linha, e os tres de comparacao de
 * texto, que o ScreenWatcher usa para conferir rotulos em posicao fixa. O FieldManager tinha
 * outras tres sobrecargas de textMatches e textMatchesTrim, que nao entraram aqui porque
 * ninguem as chamava; a limpeza do passo 10 as removeu.
 *
 * Os sete eram de visibilidade de pacote no FieldManager. Passaram a publicos, o que a
 * primeira vista e encapsulamento perdido - mas o que se perdeu foi acesso ACIDENTAL de
 * vizinho de pacote, e o que se ganhou foi um contrato nomeado, com um implementador so e uma
 * lista fechada de chamadas.
 */
// -----------------------------------------------------------------------------------//
public interface ScreenFields
// -----------------------------------------------------------------------------------//
{
  List<Field> getFields ();

  List<String> getMenus ();

  List<Field> getRowFields (int requestedRow);

  List<Field> getRowFields (int requestedRowFrom, int rows);

  boolean textMatches (int fieldNo, String text);

  boolean textMatches (int fieldNo, String text, int location);

  boolean textMatchesTrim (Field field, String text);
}
