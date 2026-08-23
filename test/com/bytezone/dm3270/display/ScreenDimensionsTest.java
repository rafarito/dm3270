package com.bytezone.dm3270.display;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.bytezone.dm3270.orders.BufferAddress;
import com.bytezone.dm3270.screen.ScreenDimensions;

// -----------------------------------------------------------------------------------//
@DisplayName ("ScreenDimensions - modelos de tela 3270")
class ScreenDimensionsTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @ParameterizedTest (name = "modelo {0}: {1}x{2} = {3} posicoes")
  @CsvSource ({ "2, 24, 80, 1920", "3, 32, 80, 2560", "4, 43, 80, 3440",
                "5, 27, 132, 3564" })
  @DisplayName ("calcula o tamanho de cada modelo de terminal")
  void calculatesSize (int model, int rows, int columns, int expectedSize)
  // ---------------------------------------------------------------------------------//
  {
    ScreenDimensions dimensions = new ScreenDimensions (rows, columns);

    assertEquals (rows, dimensions.rows);
    assertEquals (columns, dimensions.columns);
    assertEquals (expectedSize, dimensions.size);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("aplica a borda fixa de 4 pixels")
  void appliesFixedBorder ()
  // ---------------------------------------------------------------------------------//
  {
    ScreenDimensions dimensions = new ScreenDimensions (24, 80);

    assertEquals (4, dimensions.xOffset);
    assertEquals (4, dimensions.yOffset);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("EFEITO COLATERAL: o construtor reconfigura BufferAddress globalmente")
  void mutatesBufferAddressGlobalState ()
  // ---------------------------------------------------------------------------------//
  {
    // ScreenDimensions chama BufferAddress.setScreenWidth() - um estado estatico
    // compartilhado. Duas telas com larguras diferentes nao podem coexistir sem que
    // a formatacao de depuracao da segunda vaze para a primeira.
    new ScreenDimensions (27, 132);

    assertEquals ("0000 000/000 : 40 40", new BufferAddress (0).toString ());
    assertEquals ("0132 001/000 : C2 C4", new BufferAddress (132).toString ());

    new ScreenDimensions (24, 80);

    assertEquals ("0132 001/052 : C2 C4", new BufferAddress (132).toString (),
        "a mesma posicao passa a ser descrita em outra linha/coluna");
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("toString resume linhas, colunas e tamanho")
  void summarises ()
  // ---------------------------------------------------------------------------------//
  {
    assertEquals ("[Rows:24, Columns:80, Size:1920]",
        new ScreenDimensions (24, 80).toString ());
  }
}
