package com.bytezone.dm3270.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// -----------------------------------------------------------------------------------//
@DisplayName ("WriteControlCharacter - decodificacao do WCC")
class WriteControlCharacterTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @ParameterizedTest (name = "WCC {0}: reset MDT = {1}")
  @CsvSource ({ "0x00, no", "0x01, yes", "0x02, no", "0xC3, yes" })
  @DisplayName ("bit 7 (0x01) pede o reset do modified data tag")
  void resetModifiedBit (String hex, String expected)
  // ---------------------------------------------------------------------------------//
  {
    byte wcc = (byte) Integer.decode (hex).intValue ();

    assertTrue (new WriteControlCharacter (wcc).toString ()
        .startsWith ("reset MDT=" + expected));
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("decodifica cada bit de forma independente")
  void decodesEachBit ()
  // ---------------------------------------------------------------------------------//
  {
    assertTrue (text ((byte) 0x40).contains ("partition=yes"));
    assertTrue (text ((byte) 0x08).contains ("printer=yes"));
    assertTrue (text ((byte) 0x04).contains ("alarm=yes"));
    assertTrue (text ((byte) 0x02).contains ("keyboard=yes"));
    assertTrue (text ((byte) 0x01).startsWith ("reset MDT=yes"));
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("WCC zerado nao liga nenhuma acao")
  void noBitsSet ()
  // ---------------------------------------------------------------------------------//
  {
    String text = text ((byte) 0x00);

    assertEquals (0, text.split ("yes", -1).length - 1, text);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("WCC com todos os bits liga todas as acoes")
  void allBitsSet ()
  // ---------------------------------------------------------------------------------//
  {
    String text = text ((byte) 0x4F);

    assertEquals (5, text.split ("yes", -1).length - 1, text);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("os bits nao usados (0x80, 0x30) sao ignorados")
  void ignoresUnusedBits ()
  // ---------------------------------------------------------------------------------//
  {
    assertEquals (text ((byte) 0x00), text ((byte) 0xB0));
  }

  // ---------------------------------------------------------------------------------//
  private String text (byte value)
  // ---------------------------------------------------------------------------------//
  {
    return new WriteControlCharacter (value).toString ();
  }
}
