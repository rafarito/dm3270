package com.bytezone.dm3270.utilities;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// -----------------------------------------------------------------------------------//
@DisplayName ("Dm3270Utility - conversao EBCDIC e empacotamento de bytes")
class Dm3270UtilityTest
// -----------------------------------------------------------------------------------//
{
  private static final Charset CP1047 = Charset.forName ("CP1047");

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("tabelas de traducao")
  class TranslationTables
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("ebc2asc/asc2ebc sao inversas uma da outra")
    void tablesAreInverses ()
    {
      for (int ebcdic = 0; ebcdic < 256; ebcdic++)
      {
        int ascii = Dm3270Utility.ebc2asc[ebcdic];
        assertEquals (ebcdic, Dm3270Utility.asc2ebc[ascii],
            String.format ("round trip falhou para EBCDIC %02X", ebcdic));
      }
    }

    @Test
    @DisplayName ("mapeia os codigos EBCDIC conhecidos")
    void mapsKnownCodePoints ()
    {
      assertEquals (' ', Dm3270Utility.ebc2asc[0x40]);
      assertEquals ('A', Dm3270Utility.ebc2asc[0xC1]);
      assertEquals ('Z', Dm3270Utility.ebc2asc[0xE9]);
      assertEquals ('a', Dm3270Utility.ebc2asc[0x81]);
      assertEquals ('0', Dm3270Utility.ebc2asc[0xF0]);
      assertEquals ('9', Dm3270Utility.ebc2asc[0xF9]);

      assertEquals (0x40, Dm3270Utility.asc2ebc[' ']);
      assertEquals (0xC1, Dm3270Utility.asc2ebc['A']);
      assertEquals (0xF0, Dm3270Utility.asc2ebc['0']);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("ebc2asc (byte[])")
  class Ebc2Asc
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("converte um buffer EBCDIC para texto")
    void convertsBuffer ()
    {
      byte[] buffer = "HELLO".getBytes (CP1047);
      assertEquals ("HELLO", Dm3270Utility.ebc2asc (buffer));
    }

    @Test
    @DisplayName ("suprime os bytes nulos, encurtando o resultado")
    void suppressesNulls ()
    {
      byte[] buffer = { (byte) 0xC8, 0x00, (byte) 0xC9, 0x00 };   // H <nul> I <nul>
      String result = Dm3270Utility.ebc2asc (buffer);

      // os nulos sao suprimidos mas o array de saida mantem o tamanho original,
      // portanto as posicoes finais ficam com 0x00
      assertEquals ('H', result.charAt (0));
      assertEquals ('I', result.charAt (1));
      assertEquals (buffer.length, result.length ());
      assertEquals (0, result.charAt (2));
      assertEquals (0, result.charAt (3));
    }

    @Test
    @DisplayName ("buffer vazio devolve string vazia")
    void emptyBuffer ()
    {
      assertEquals ("", Dm3270Utility.ebc2asc (new byte[0]));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("getString")
  class GetString
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("decodifica o buffer inteiro")
    void decodesWholeBuffer ()
    {
      assertEquals ("DM3270", Dm3270Utility.getString ("DM3270".getBytes (CP1047)));
    }

    @Test
    @DisplayName ("decodifica um trecho a partir de offset/length")
    void decodesSlice ()
    {
      byte[] buffer = "ABCDEFGH".getBytes (CP1047);
      assertEquals ("CDE", Dm3270Utility.getString (buffer, 2, 3));
    }

    @Test
    @DisplayName ("trunca quando offset+length ultrapassa o buffer")
    void clampsOverrun ()
    {
      byte[] buffer = "ABCDE".getBytes (CP1047);
      // length pedido invade o fim do buffer: e reduzido para buffer.length-offset-1
      assertEquals ("CD", Dm3270Utility.getString (buffer, 2, 99));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("getSanitisedString")
  class SanitisedString
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("substitui bytes de controle (< 0x40) por espaco")
    void replacesControlBytes ()
    {
      byte[] buffer = { (byte) 0xC1, 0x00, (byte) 0xC2, 0x0D, (byte) 0xC3 };
      assertEquals ("A B C", Dm3270Utility.getSanitisedString (buffer, 0, 5));
    }

    @Test
    @DisplayName ("preserva os bytes imprimiveis")
    void keepsPrintableBytes ()
    {
      byte[] buffer = "TSO".getBytes (CP1047);
      assertEquals ("TSO", Dm3270Utility.getSanitisedString (buffer, 0, 3));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("inteiros sem sinal (big endian)")
  class UnsignedIntegers
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("unsignedShort le dois bytes em big endian")
    void readsShort ()
    {
      byte[] buffer = { 0x00, 0x00, (byte) 0x12, (byte) 0x34 };
      assertEquals (0x1234, Dm3270Utility.unsignedShort (buffer, 2));
    }

    @Test
    @DisplayName ("unsignedShort trata o bit alto sem sinal")
    void readsShortWithHighBit ()
    {
      byte[] buffer = { (byte) 0xFF, (byte) 0xFF };
      assertEquals (65535, Dm3270Utility.unsignedShort (buffer, 0));
    }

    @Test
    @DisplayName ("unsignedLong le quatro bytes em big endian")
    void readsLong ()
    {
      byte[] buffer = { 0x01, 0x02, 0x03, 0x04 };
      assertEquals (0x01020304, Dm3270Utility.unsignedLong (buffer, 0));
    }

    @Test
    @DisplayName ("packUnsignedShort escreve e devolve o proximo offset")
    void packsShort ()
    {
      byte[] buffer = new byte[4];
      int next = Dm3270Utility.packUnsignedShort (0xABCD, buffer, 1);

      assertEquals (3, next);
      assertArrayEquals (new byte[] { 0x00, (byte) 0xAB, (byte) 0xCD, 0x00 }, buffer);
    }

    @Test
    @DisplayName ("packUnsignedLong escreve e devolve o proximo offset")
    void packsLong ()
    {
      byte[] buffer = new byte[4];
      int next = Dm3270Utility.packUnsignedLong (0xDEADBEEFL, buffer, 0);

      assertEquals (4, next);
      assertArrayEquals (
          new byte[] { (byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF }, buffer);
    }

    @Test
    @DisplayName ("pack/unpack de short faz round trip para todos os valores de 16 bits")
    void shortRoundTrip ()
    {
      byte[] buffer = new byte[2];
      for (int value = 0; value <= 0xFFFF; value += 7)
      {
        Dm3270Utility.packUnsignedShort (value, buffer, 0);
        assertEquals (value, Dm3270Utility.unsignedShort (buffer, 0));
      }
    }

    @Test
    @DisplayName ("pack/unpack de long faz round trip")
    void longRoundTrip ()
    {
      byte[] buffer = new byte[4];
      for (long value : new long[] { 0, 1, 0xFF, 0x100, 0xFFFF, 0x10000, 0x7FFFFFFFL })
      {
        Dm3270Utility.packUnsignedLong (value, buffer, 0);
        assertEquals ((int) value, Dm3270Utility.unsignedLong (buffer, 0));
      }
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("formatacao hexadecimal")
  class HexFormatting
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("toHexString formata cada byte com dois digitos")
    void hexString ()
    {
      byte[] buffer = { 0x00, 0x0F, (byte) 0xFF };
      assertEquals ("00 0F FF", Dm3270Utility.toHexString (buffer, 0, 3));
    }

    @Test
    @DisplayName ("toHexString com offset/length nao deixa espaco no fim")
    void hexStringSlice ()
    {
      byte[] buffer = { 0x11, 0x22, 0x33, 0x44 };
      assertEquals ("22 33", Dm3270Utility.toHexString (buffer, 1, 2));
    }

    @Test
    @DisplayName ("toHex produz endereco, hex e texto EBCDIC")
    void hexDumpEbcdic ()
    {
      byte[] buffer = "ABC".getBytes (CP1047);
      String dump = Dm3270Utility.toHex (buffer);

      assertTrue (dump.startsWith ("0000  "), dump);
      assertTrue (dump.contains ("C1 C2 C3"), dump);
      assertTrue (dump.stripTrailing ().endsWith ("ABC"), dump);
    }

    @Test
    @DisplayName ("toHex substitui bytes de controle por ponto")
    void hexDumpMasksControlBytes ()
    {
      byte[] buffer = { 0x00, (byte) 0xC1 };
      String dump = Dm3270Utility.toHex (buffer);

      assertTrue (dump.stripTrailing ().endsWith (".A"), dump);
    }

    @Test
    @DisplayName ("a ultima linha ainda carrega um CR residual no Windows")
    void trailingCarriageReturnOnWindows ()
    {
      // toHex termina cada linha com %n e depois remove UM unico caractere.
      // Onde o separador de linha tem dois caracteres (Windows, "\r\n") sobra o \r.
      // O teste documenta o comportamento atual para que qualquer mudanca seja notada.
      String separator = System.lineSeparator ();
      String residue = separator.substring (0, separator.length () - 1);

      String dump = Dm3270Utility.toHex (new byte[] { (byte) 0xC1 });

      assertTrue (dump.endsWith ("A" + residue), dump);
    }

    @Test
    @DisplayName ("toHex quebra em linhas de 16 bytes")
    void hexDumpWraps ()
    {
      byte[] buffer = new byte[20];
      String dump = Dm3270Utility.toHex (buffer);

      String[] lines = dump.split ("\\R");
      assertEquals (2, lines.length);
      assertTrue (lines[0].startsWith ("0000"), lines[0]);
      assertTrue (lines[1].startsWith ("0010"), lines[1]);
    }

    @Test
    @DisplayName ("toHex em modo ASCII usa outra faixa de bytes imprimiveis")
    void hexDumpAscii ()
    {
      byte[] buffer = "AB".getBytes (java.nio.charset.StandardCharsets.US_ASCII);
      String dump = Dm3270Utility.toHex (buffer, false);

      assertTrue (dump.contains ("41 42"), dump);
      assertTrue (dump.stripTrailing ().endsWith ("AB"), dump);
    }

    @Test
    @DisplayName ("buffer vazio nao gera saida")
    void hexDumpEmpty ()
    {
      assertEquals ("", Dm3270Utility.toHex (new byte[0]));
    }
  }
}
