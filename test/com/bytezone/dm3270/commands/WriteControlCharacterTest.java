package com.bytezone.dm3270.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.bytezone.dm3270.display.HeadlessScreenTarget;

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

  /*
   * process () e o que a tela recebe de cada WCC. O modo de insercao cai SEMPRE, com ou sem
   * bit ligado, e sempre primeiro; os sinais vem depois numa ordem fixa, que nao e a ordem
   * dos bits - o teclado, que e o bit 0x02, e o ultimo.
   */
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("process com WCC zerado so desliga o modo de insercao")
  void processWithNoBitsOnlyResetsInsertMode ()
  // ---------------------------------------------------------------------------------//
  {
    assertEquals (List.of ("resetInsertMode"), processed ((byte) 0x00));
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("process com todos os bits sinaliza a tela numa ordem fixa")
  void processWithAllBitsSignalsInFixedOrder ()
  // ---------------------------------------------------------------------------------//
  {
    assertEquals (List.of ("resetInsertMode", "resetPartition", "startPrinter",
                           "soundAlarm", "resetModified", "restoreKeyboard"),
                  processed ((byte) 0x4F));
  }

  // ---------------------------------------------------------------------------------//
  @ParameterizedTest (name = "WCC {0} -> {1}")
  @CsvSource ({ "0x40, resetPartition", "0x08, startPrinter", "0x04, soundAlarm",
                "0x02, restoreKeyboard", "0x01, resetModified" })
  @DisplayName ("process liga cada sinal pelo seu bit")
  void processSignalsEachBit (String hex, String signal)
  // ---------------------------------------------------------------------------------//
  {
    byte wcc = (byte) Integer.decode (hex).intValue ();

    assertEquals (List.of ("resetInsertMode", signal), processed (wcc));
  }

  // ---------------------------------------------------------------------------------//
  private List<String> processed (byte value)
  // ---------------------------------------------------------------------------------//
  {
    HeadlessScreenTarget screen = new HeadlessScreenTarget ();
    screen.calls.clear ();

    new WriteControlCharacter (value).process (screen);

    return screen.calls;
  }

  // ---------------------------------------------------------------------------------//
  private String text (byte value)
  // ---------------------------------------------------------------------------------//
  {
    return new WriteControlCharacter (value).toString ();
  }
}
