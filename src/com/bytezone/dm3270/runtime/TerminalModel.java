package com.bytezone.dm3270.runtime;

import java.util.Optional;

/*
 * Os quatro modelos de terminal 3270 que o emulador negocia, e o tamanho de tela de cada um.
 *
 * Era um switch de cinco ramos dentro do Console - a classe Application do JavaFX -, e por isso
 * nao tinha teste nenhum: nada na suite instancia um Console. O mapeamento em si nao tem nada
 * de composicao da aplicacao, e um dado.
 *
 * CARREGA int, E NAO ScreenDimensions, e isso e Regra 1 e nao gosto. O construtor de
 * ScreenDimensions chama BufferAddress.setScreenWidth (columns), que e estado estatico GLOBAL -
 * o ScreenDimensionsTest ja documenta o efeito. Constantes de enum pre-construidas disparariam
 * esse efeito para os quatro modelos na carga da classe, e na ordem da declaracao, em vez de
 * uma vez por selecao e com o valor do modelo escolhido. Quem precisa das dimensoes constroi
 * a ScreenDimensions no ponto em que ela ja era construida.
 *
 * O pacote runtime e o certo: ele nao tem import nenhum, e com int continua sem ter.
 *
 * ATENCAO ao usar isto para reescrever o Console.setModel: o modelo 5 e valido e cai no default
 * do switch original por falta de break, logando "Invalid model number: 5" DEPOIS de se
 * configurar corretamente. E o item 1 do BACKLOG-DEFEITOS.md, e a Regra 1 manda preservar.
 */
// -----------------------------------------------------------------------------------//
public enum TerminalModel
// -----------------------------------------------------------------------------------//
{
  MODEL_2 (2, 24, 80),
  MODEL_3 (3, 32, 80),
  MODEL_4 (4, 43, 80),
  MODEL_5 (5, 27, 132);

  private final int number;
  private final int rows;
  private final int columns;

  // ---------------------------------------------------------------------------------//
  TerminalModel (int number, int rows, int columns)
  // ---------------------------------------------------------------------------------//
  {
    this.number = number;
    this.rows = rows;
    this.columns = columns;
  }

  // ---------------------------------------------------------------------------------//
  public int number ()
  // ---------------------------------------------------------------------------------//
  {
    return number;
  }

  // ---------------------------------------------------------------------------------//
  public int rows ()
  // ---------------------------------------------------------------------------------//
  {
    return rows;
  }

  // ---------------------------------------------------------------------------------//
  public int columns ()
  // ---------------------------------------------------------------------------------//
  {
    return columns;
  }

  // Optional, e nao um default: quem chama tem de decidir o que fazer com um numero invalido,
  // e no Console essa decisao e um logger.warn sem atribuir dimensao nenhuma.
  // ---------------------------------------------------------------------------------//
  public static Optional<TerminalModel> forNumber (int number)
  // ---------------------------------------------------------------------------------//
  {
    for (TerminalModel model : values ())
      if (model.number == number)
        return Optional.of (model);

    return Optional.empty ();
  }
}
