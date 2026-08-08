package com.bytezone.dm3270.orders;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

// -----------------------------------------------------------------------------------//
@DisplayName ("BufferAddress - enderecamento de 12 e 14 bits do 3270")
class BufferAddressTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("tabela de codificacao")
  class AddressTable
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("tem 64 entradas, uma por valor de 6 bits")
    void hasSixtyFourEntries ()
    {
      assertEquals (64, BufferAddress.address.length);
    }

    @Test
    @DisplayName ("nenhum codigo colide com uma order do data stream")
    void neverCollidesWithAnOrder ()
    {
      for (int i = 0; i < BufferAddress.address.length; i++)
      {
        byte code = BufferAddress.address[i];
        for (byte orderValue : Order.orderValues)
          if (code == orderValue)
            org.junit.jupiter.api.Assertions.fail (
                String.format ("codigo %02X (indice %d) colide com a order %02X", code, i,
                    orderValue));
      }
    }

    @Test
    @DisplayName ("os codigos sao todos distintos")
    void codesAreDistinct ()
    {
      boolean[] seen = new boolean[256];
      for (byte code : BufferAddress.address)
      {
        int value = code & 0xFF;
        assertFalse (seen[value], String.format ("codigo duplicado: %02X", code));
        seen[value] = true;
      }
    }

    @Test
    @DisplayName ("aplica os dois ajustes documentados (indices 33 e 48)")
    void appliesDocumentedFixups ()
    {
      // 0xE1 seria a order START_FIELD? nao - mas 0x61 evita colisao com 0xE1
      assertEquals ((byte) 0x61, BufferAddress.address[33]);
      assertEquals ((byte) 0xF0, BufferAddress.address[48]);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("decodificacao a partir de dois bytes")
  class Decoding
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("usa 14 bits quando os dois bits altos sao 00")
    void fourteenBitAddressing ()
    {
      // 0x00 0x50 -> (0x00 & 0x3F) << 8 | 0x50 = 0x50
      BufferAddress address = new BufferAddress ((byte) 0x00, (byte) 0x50);
      assertEquals (0x50, address.getLocation ());
      assertTrue (address.isValid ());

      // 0x07 0xFF -> 0x07FF
      BufferAddress big = new BufferAddress ((byte) 0x07, (byte) 0xFF);
      assertEquals (0x07FF, big.getLocation ());
    }

    @Test
    @DisplayName ("usa 12 bits quando os dois bits altos sao 01 ou 11")
    void twelveBitAddressing ()
    {
      // 0x40 0x40 -> (0x00 << 6) | 0x00 = 0
      assertEquals (0, new BufferAddress ((byte) 0x40, (byte) 0x40).getLocation ());

      // 0xC1 0x50 -> (0x01 << 6) | 0x10 = 0x50
      assertEquals (0x50, new BufferAddress ((byte) 0xC1, (byte) 0x50).getLocation ());
    }

    @Test
    @DisplayName ("marca como invalido o prefixo reservado 10")
    void rejectsReservedPrefix ()
    {
      assertFalse (new BufferAddress ((byte) 0x80, (byte) 0x40).isValid ());
      assertFalse (new BufferAddress ((byte) 0xBF, (byte) 0x40).isValid ());
    }

    @ParameterizedTest (name = "prefixo {0} e valido")
    @ValueSource (ints = { 0x00, 0x40, 0xC0 })
    @DisplayName ("aceita os prefixos 00, 01 e 11")
    void acceptsValidPrefixes (int b1)
    {
      assertTrue (new BufferAddress ((byte) b1, (byte) 0x40).isValid ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("codificacao a partir de uma posicao")
  class Encoding
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "posicao {0}")
    @ValueSource (ints = { 0, 1, 63, 64, 79, 80, 1000, 1919, 3439 })
    @DisplayName ("codifica e decodifica de volta a mesma posicao")
    void roundTrip (int location)
    {
      BufferAddress encoded = new BufferAddress (location);

      byte[] buffer = new byte[2];
      encoded.packAddress (buffer, 0);

      BufferAddress decoded = new BufferAddress (buffer[0], buffer[1]);
      assertTrue (decoded.isValid ());
      assertEquals (location, decoded.getLocation ());
    }

    @Test
    @DisplayName ("cobre todas as posicoes de uma tela modelo 2 (24x80)")
    void roundTripWholeModel2Screen ()
    {
      byte[] buffer = new byte[2];
      for (int location = 0; location < 24 * 80; location++)
      {
        new BufferAddress (location).packAddress (buffer, 0);
        assertEquals (location, new BufferAddress (buffer[0], buffer[1]).getLocation (),
            "falhou na posicao " + location);
      }
    }

    @Test
    @DisplayName ("cobre todas as posicoes de uma tela modelo 5 (27x132)")
    void roundTripWholeModel5Screen ()
    {
      byte[] buffer = new byte[2];
      for (int location = 0; location < 27 * 132; location++)
      {
        new BufferAddress (location).packAddress (buffer, 0);
        assertEquals (location, new BufferAddress (buffer[0], buffer[1]).getLocation (),
            "falhou na posicao " + location);
      }
    }

    @Test
    @DisplayName ("packAddress escreve no offset pedido e devolve o proximo")
    void packAddressHonoursOffset ()
    {
      byte[] buffer = new byte[4];
      int next = new BufferAddress (0).packAddress (buffer, 1);

      assertEquals (3, next);
      assertArrayEquals (new byte[] { 0x00, 0x40, 0x40, 0x00 }, buffer);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("toString")
  class ToString
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "posicao {0} = linha {1} coluna {2}")
    @CsvSource ({ "0, 0, 0", "79, 0, 79", "80, 1, 0", "161, 2, 1" })
    @DisplayName ("mostra a posicao como linha/coluna para 80 colunas")
    void showsRowAndColumn (int location, int row, int column)
    {
      BufferAddress.setScreenWidth (80);
      String text = new BufferAddress (location).toString ();

      assertTrue (text.startsWith (String.format ("%04d %03d/%03d", location, row, column)),
          text);
    }
  }
}
