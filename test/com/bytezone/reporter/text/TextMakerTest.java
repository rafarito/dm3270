package com.bytezone.reporter.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.bytezone.reporter.record.Record;

// -----------------------------------------------------------------------------------//
@DisplayName ("TextMaker - interpretacao dos bytes como texto")
class TextMakerTest
// -----------------------------------------------------------------------------------//
{
  private static final Charset CP1047 = Charset.forName ("CP1047");

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("EbcdicTextMaker")
  class Ebcdic
  // ---------------------------------------------------------------------------------//
  {
    private final EbcdicTextMaker maker = new EbcdicTextMaker ();

    @Test
    @DisplayName ("converte um buffer EBCDIC em texto legivel")
    void convertsEbcdic ()
    {
      byte[] buffer = "DM3270".getBytes (CP1047);

      assertEquals ("DM3270", maker.getText (buffer, 0, buffer.length));
    }

    @Test
    @DisplayName ("substitui bytes de controle por ponto")
    void masksControlBytes ()
    {
      byte[] buffer = { 0x00, (byte) 0xC1, 0x0D, (byte) 0xC2 };

      assertEquals (".A.B", maker.getText (buffer, 0, 4));
    }

    @Test
    @DisplayName ("0xFF tambem e mascarado")
    void masksFF ()
    {
      assertEquals (".", maker.getText (new byte[] { (byte) 0xFF }, 0, 1));
    }

    @Test
    @DisplayName ("nao le alem do fim do buffer")
    void clampsToBufferLength ()
    {
      byte[] buffer = "AB".getBytes (CP1047);

      assertEquals ("AB", maker.getText (buffer, 0, 100));
    }

    @Test
    @DisplayName ("getTextRightTrim remove os espacos finais")
    void rightTrims ()
    {
      byte[] buffer = "AB    ".getBytes (CP1047);

      assertEquals ("AB", maker.getTextRightTrim (buffer, 0, buffer.length));
      assertEquals ("AB    ", maker.getText (buffer, 0, buffer.length));
    }

    @Test
    @DisplayName ("test aceita um buffer inteiramente EBCDIC imprimivel")
    void testAcceptsEbcdic ()
    {
      byte[] buffer = "TSO READY".getBytes (CP1047);

      assertTrue (maker.test (buffer, 0, buffer.length));
    }

    @Test
    @DisplayName ("test rejeita um buffer com bytes de controle")
    void testRejectsControlBytes ()
    {
      assertFalse (maker.test (new byte[] { (byte) 0xC1, 0x00 }, 0, 2));
    }

    @Test
    @DisplayName ("test rejeita texto ASCII (letras caem na faixa de controle EBCDIC)")
    void testRejectsAscii ()
    {
      byte[] ascii = "HELLO".getBytes (java.nio.charset.StandardCharsets.US_ASCII);

      assertFalse (maker.test (ascii, 0, ascii.length));
    }

    @Test
    @DisplayName ("countAlphanumericBytes conta letras, digitos e espacos")
    void countsAlphanumerics ()
    {
      byte[] buffer = "AB 12".getBytes (CP1047);

      assertEquals (5, maker.countAlphanumericBytes (buffer, 0, buffer.length));
      assertEquals (0, maker.countAlphanumericBytes (new byte[] { 0x00, 0x01 }, 0, 2));
    }

    @Test
    @DisplayName ("aceita um Record diretamente")
    void acceptsRecord ()
    {
      byte[] buffer = "XXABXX".getBytes (CP1047);
      Record record = new Record (buffer, 2, 2, 0);

      assertEquals ("AB", maker.getText (record));
      assertEquals (2, maker.countAlphanumericBytes (record));
      assertTrue (maker.test (record));
    }

    @Test
    @DisplayName ("toString identifica o formato")
    void identifiesItself ()
    {
      assertEquals ("EBCDIC", maker.toString ());
    }

    // -------------------------------------------------------------------------------//
    @Nested
    @DisplayName ("valores exatos de fronteira")
    class Boundaries
    // -------------------------------------------------------------------------------//
    {
      @Test
      @DisplayName ("0x4A e mascarado, 0x4B e o primeiro byte imprimivel")
      void printableRangeStartsAt4B ()
      {
        assertEquals (".", maker.getText (new byte[] { 0x4A }, 0, 1));
        assertFalse (maker.test (new byte[] { 0x4A }, 0, 1));

        assertTrue (maker.test (new byte[] { 0x4B }, 0, 1),
            "0x4B esta dentro da faixa imprimivel");
      }

      @Test
      @DisplayName ("0x40 (espaco) e a excecao aceita abaixo de 0x4B")
      void spaceIsAccepted ()
      {
        assertEquals (" ", maker.getText (new byte[] { 0x40 }, 0, 1));
        assertTrue (maker.test (new byte[] { 0x40 }, 0, 1));
      }

      @Test
      @DisplayName ("a contagem de alfanumericos inclui 0xC1 e 0xF9, exclui as bordas")
      void alphanumericRangeIsInclusive ()
      {
        assertEquals (1, maker.countAlphanumericBytes (new byte[] { (byte) 0xC1 }, 0, 1));
        assertEquals (1, maker.countAlphanumericBytes (new byte[] { (byte) 0xF9 }, 0, 1));

        assertEquals (0, maker.countAlphanumericBytes (new byte[] { (byte) 0xC0 }, 0, 1));
        assertEquals (0, maker.countAlphanumericBytes (new byte[] { (byte) 0xFA }, 0, 1));
      }
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("AsciiTextMaker")
  class Ascii
  // ---------------------------------------------------------------------------------//
  {
    private final AsciiTextMaker maker = new AsciiTextMaker ();

    @Test
    @DisplayName ("converte um buffer ASCII em texto")
    void convertsAscii ()
    {
      byte[] buffer = "DM3270".getBytes (java.nio.charset.StandardCharsets.US_ASCII);

      assertEquals ("DM3270", maker.getText (buffer, 0, buffer.length));
    }

    @Test
    @DisplayName ("substitui controles e bytes altos por ponto")
    void masksNonPrintable ()
    {
      byte[] buffer = { 0x00, 'A', 0x1F, (byte) 0xC0, (byte) 0xFF };

      assertEquals (".A...", maker.getText (buffer, 0, buffer.length));
    }

    @Test
    @DisplayName ("test aceita texto ASCII e rejeita EBCDIC")
    void testDiscriminatesFormats ()
    {
      assertTrue (maker.test ("HELLO".getBytes (java.nio.charset.StandardCharsets.US_ASCII),
          0, 5));
      assertFalse (maker.test ("HELLO".getBytes (CP1047), 0, 5));
    }

    @Test
    @DisplayName ("countAlphanumericBytes conta maiusculas e espacos")
    void countsAlphanumerics ()
    {
      byte[] buffer = "AB 12".getBytes (java.nio.charset.StandardCharsets.US_ASCII);

      assertEquals (3, maker.countAlphanumericBytes (buffer, 0, buffer.length),
          "A, B e o espaco - os digitos ficam fora da faixa 0x41-0x5A");
    }

    @Test
    @DisplayName ("toString identifica o formato")
    void identifiesItself ()
    {
      assertEquals ("ASCII", maker.toString ());
    }

    // -------------------------------------------------------------------------------//
    @Nested
    @DisplayName ("valores exatos de fronteira")
    class Boundaries
    // -------------------------------------------------------------------------------//
    {
      @Test
      @DisplayName ("0x1F e mascarado, 0x20 (espaco) e o primeiro imprimivel")
      void printableRangeStartsAtSpace ()
      {
        assertEquals (".", maker.getText (new byte[] { 0x1F }, 0, 1));
        assertFalse (maker.test (new byte[] { 0x1F }, 0, 1));

        assertEquals (" ", maker.getText (new byte[] { 0x20 }, 0, 1));
        assertTrue (maker.test (new byte[] { 0x20 }, 0, 1));
      }

      @Test
      @DisplayName ("0xBF e o ultimo imprimivel, 0xC0 ja e mascarado")
      void printableRangeEndsBeforeC0 ()
      {
        assertEquals ("¿", maker.getText (new byte[] { (byte) 0xBF }, 0, 1));
        assertTrue (maker.test (new byte[] { (byte) 0xBF }, 0, 1));

        assertEquals (".", maker.getText (new byte[] { (byte) 0xC0 }, 0, 1));
        assertFalse (maker.test (new byte[] { (byte) 0xC0 }, 0, 1));
      }

      @Test
      @DisplayName ("a contagem de alfanumericos inclui A e Z, exclui as bordas")
      void alphanumericRangeIsInclusive ()
      {
        assertEquals (1, maker.countAlphanumericBytes (new byte[] { 0x41 }, 0, 1), "A");
        assertEquals (1, maker.countAlphanumericBytes (new byte[] { 0x5A }, 0, 1), "Z");

        assertEquals (0, maker.countAlphanumericBytes (new byte[] { 0x40 }, 0, 1), "@");
        assertEquals (0, maker.countAlphanumericBytes (new byte[] { 0x5B }, 0, 1), "[");
      }
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("os dois formatos se excluem para o mesmo conteudo")
  class Discrimination
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("um texto so passa no teste do proprio formato")
    void formatsAreMutuallyExclusive ()
    {
      EbcdicTextMaker ebcdic = new EbcdicTextMaker ();
      AsciiTextMaker ascii = new AsciiTextMaker ();

      byte[] asciiBytes =
          "TSO LOGON".getBytes (java.nio.charset.StandardCharsets.US_ASCII);
      byte[] ebcdicBytes = "TSO LOGON".getBytes (CP1047);

      assertTrue (ascii.test (asciiBytes, 0, asciiBytes.length));
      assertFalse (ebcdic.test (asciiBytes, 0, asciiBytes.length));

      assertTrue (ebcdic.test (ebcdicBytes, 0, ebcdicBytes.length));
      assertFalse (ascii.test (ebcdicBytes, 0, ebcdicBytes.length));
    }
  }
}
