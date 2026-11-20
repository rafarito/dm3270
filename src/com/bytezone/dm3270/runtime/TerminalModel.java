package com.bytezone.dm3270.runtime;

import java.util.Optional;

// Guarda linhas/colunas como int em vez de ScreenDimensions, pois o construtor desta altera
// estado estatico global (BufferAddress.setScreenWidth) na carga da classe.
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
