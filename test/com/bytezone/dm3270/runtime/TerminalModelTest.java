package com.bytezone.dm3270.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

// -----------------------------------------------------------------------------------//
@DisplayName ("TerminalModel - os quatro modelos de terminal e o tamanho de cada um")
class TerminalModelTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("as dimensoes de cada modelo")
  class Dimensoes
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @ParameterizedTest (name = "o modelo {0} e {1}x{2}")
    @CsvSource ({ "2,24,80", "3,32,80", "4,43,80", "5,27,132" })
    void osQuatroModelos (int number, int rows, int columns)
    // -------------------------------------------------------------------------------//
    {
      TerminalModel model = TerminalModel.forNumber (number).orElseThrow ();

      assertEquals (rows, model.rows (), "as linhas do modelo " + number);
      assertEquals (columns, model.columns (), "as colunas do modelo " + number);
      assertEquals (number, model.number (), "o numero do modelo");
    }

    // ---------------------------------------------------------------------------------//
    @Test
    @DisplayName ("sao exatamente quatro, e nesta ordem")
    void saoQuatro ()
    // ---------------------------------------------------------------------------------//
    {
      assertEquals (4, TerminalModel.values ().length);
      assertSame (TerminalModel.MODEL_2, TerminalModel.values ()[0]);
      assertSame (TerminalModel.MODEL_5, TerminalModel.values ()[3]);
    }

    // ---------------------------------------------------------------------------------//
    @Test
    @DisplayName ("o modelo 5 e a tela larga, 27x132")
    void oModeloCincoEALarga ()
    // ---------------------------------------------------------------------------------//
    {
      assertEquals (132, TerminalModel.MODEL_5.columns ());
      assertEquals (27, TerminalModel.MODEL_5.rows ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("a busca por numero")
  class BuscaPorNumero
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @ParameterizedTest (name = "o numero {0} tem modelo")
    @ValueSource (ints = { 2, 3, 4, 5 })
    void osQuatroValidos (int number)
    // -------------------------------------------------------------------------------//
    {
      assertTrue (TerminalModel.forNumber (number).isPresent ());
    }

    // Modelo 5 e valido; o log de invalido em Console.setModel e bug de break ausente.
    // -------------------------------------------------------------------------------//
    @ParameterizedTest (name = "o numero {0} nao tem modelo")
    @ValueSource (ints = { -1, 0, 1, 6, 7, 99 })
    void osInvalidos (int number)
    // -------------------------------------------------------------------------------//
    {
      assertEquals (Optional.empty (), TerminalModel.forNumber (number),
          "o numero " + number + " nao e um modelo 3270 que o emulador negocie");
    }

    // ---------------------------------------------------------------------------------//
    @Test
    @DisplayName ("a busca devolve a propria constante, nao uma copia")
    void devolveAConstante ()
    // ---------------------------------------------------------------------------------//
    {
      assertSame (TerminalModel.MODEL_3, TerminalModel.forNumber (3).orElseThrow ());
    }

    // ---------------------------------------------------------------------------------//
    @Test
    @DisplayName ("todo modelo se acha pelo proprio numero")
    void idaEVolta ()
    // ---------------------------------------------------------------------------------//
    {
      for (TerminalModel model : TerminalModel.values ())
        assertSame (model, TerminalModel.forNumber (model.number ()).orElseThrow (),
            "o modelo " + model + " nao se achou pelo proprio numero");
    }
  }
}
