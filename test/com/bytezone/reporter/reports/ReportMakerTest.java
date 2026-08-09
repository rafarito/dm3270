package com.bytezone.reporter.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.bytezone.reporter.record.Record;
import com.bytezone.reporter.text.AsciiTextMaker;
import com.bytezone.reporter.text.EbcdicTextMaker;
import com.bytezone.reporter.text.TextMaker;

// -----------------------------------------------------------------------------------//
@DisplayName ("ReportMaker - heuristica que reconhece o formato de um dataset")
class ReportMakerTest
// -----------------------------------------------------------------------------------//
{
  private static final TextMaker ASCII = new AsciiTextMaker ();
  private static final TextMaker EBCDIC = new EbcdicTextMaker ();

  // ---------------------------------------------------------------------------------//
  //  Construcao dos registros
  // ---------------------------------------------------------------------------------//

  private static Record ascii (String text)
  {
    return ascii (text, 0);
  }

  private static Record ascii (String text, int recordNumber)
  {
    byte[] buffer = text.getBytes (java.nio.charset.StandardCharsets.ISO_8859_1);

    return new Record (buffer, 0, buffer.length, recordNumber);
  }

  private static Record ebcdic (String text)
  {
    return ebcdic (text, 0);
  }

  private static Record ebcdic (String text, int recordNumber)
  {
    byte[] buffer = new byte[text.length ()];
    for (int i = 0; i < text.length (); i++)
      buffer[i] = (byte) EbcdicTextMaker.asc2ebc[text.charAt (i)];

    return new Record (buffer, 0, buffer.length, recordNumber);
  }

  private static Record bytes (int recordNumber, int... values)
  {
    byte[] buffer = new byte[values.length];
    for (int i = 0; i < values.length; i++)
      buffer[i] = (byte) values[i];

    return new Record (buffer, 0, buffer.length, recordNumber);
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("HexReport")
  class Hex
  // ---------------------------------------------------------------------------------//
  {
    private final HexReport report = new HexReport (true, true);

    @Test
    @DisplayName ("aceita qualquer registro: o dump hexadecimal sempre funciona")
    void acceptsEverything ()
    {
      assertTrue (report.test (ascii ("texto normal"), ASCII));
      assertTrue (report.test (bytes (0, 0x00, 0xFF, 0x7F), ASCII));
      assertTrue (report.test (bytes (0), EBCDIC));
    }

    @Test
    @DisplayName ("declara as opcoes de layout que recebeu")
    void reportsLayoutOptions ()
    {
      assertTrue (report.newlineBetweenRecords ());
      assertTrue (report.allowSplitRecords ());
      assertEquals ("HEX", report.toString ());
    }

    @Test
    @DisplayName ("um HexReport sem quebras nem divisao tambem e valido")
    void otherLayoutOptions ()
    {
      HexReport plain = new HexReport (false, false);

      assertFalse (plain.newlineBetweenRecords ());
      assertFalse (plain.allowSplitRecords ());
    }

    @Test
    @DisplayName ("o peso do hex e o menor de todos: e sempre o ultimo recurso")
    void weightsLast ()
    {
      // empatado na pontuacao, o formato de maior peso ganha — e o hex aceita tudo
      assertEquals (0.1, report.weight ());
      assertEquals (1.0, new TextReport (false, false).weight ());
      assertEquals (1.1, new AsaReport (false, false).weight ());
      assertEquals (1.1, new NatloadReport (false, false).weight ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("TextReport")
  class Text
  // ---------------------------------------------------------------------------------//
  {
    private final TextReport report = new TextReport (false, false);

    @Test
    @DisplayName ("aceita um registro de texto legivel")
    void acceptsReadableText ()
    {
      assertTrue (report.test (ascii ("LINHA DE TEXTO"), ASCII));
      assertTrue (report.test (ebcdic ("LINHA DE TEXTO"), EBCDIC));
    }

    @Test
    @DisplayName ("recusa bytes de controle")
    void rejectsControlBytes ()
    {
      assertFalse (report.test (bytes (0, 0x01, 0x02, 0x03), ASCII));
    }

    @Test
    @DisplayName ("recusa texto na codificacao errada")
    void rejectsWrongEncoding ()
    {
      assertFalse (report.test (ebcdic ("LINHA DE TEXTO"), ASCII));
      assertFalse (report.test (ascii ("LINHA DE TEXTO"), EBCDIC));
    }

    @Test
    @DisplayName ("um registro acima de 200 bytes nao e tratado como texto")
    void rejectsLongRecords ()
    {
      assertTrue (report.test (ascii ("A".repeat (200)), ASCII));
      assertFalse (report.test (ascii ("A".repeat (201)), ASCII));
    }

    @Test
    @DisplayName ("um registro vazio passa no teste de texto")
    void acceptsEmptyRecord ()
    {
      assertTrue (report.test (bytes (0), ASCII));
    }

    @Test
    @DisplayName ("nome e opcoes de layout")
    void describesItself ()
    {
      assertEquals ("Text", report.toString ());
      assertFalse (report.newlineBetweenRecords ());
      assertFalse (report.allowSplitRecords ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("AsaReport")
  class Asa
  // ---------------------------------------------------------------------------------//
  {
    private final AsaReport report = new AsaReport (false, true);

    @ParameterizedTest (name = "controle [{0}]")
    @ValueSource (chars = { ' ', '0', '1', '-', 'V' })
    @DisplayName ("aceita os cinco caracteres de controle de carro")
    void acceptsCarriageControl (char control)
    {
      assertTrue (report.test (ebcdic (control + "TEXTO DO RELATORIO", 1), EBCDIC));
    }

    @ParameterizedTest (name = "controle [{0}]")
    @ValueSource (chars = { 'A', '2', '9', '+', '*' })
    @DisplayName ("recusa qualquer outro primeiro caractere")
    void rejectsOtherFirstCharacters (char first)
    {
      assertFalse (report.test (ebcdic (first + "TEXTO DO RELATORIO", 1), EBCDIC));
    }

    @Test
    @DisplayName ("um registro vazio e aceito sem olhar o conteudo")
    void acceptsEmptyRecord ()
    {
      assertTrue (report.test (bytes (0), EBCDIC));
      assertTrue (report.test (bytes (5), ASCII));
    }

    @Test
    @DisplayName ("recusa registros acima de 200 bytes")
    void rejectsLongRecords ()
    {
      assertFalse (report.test (ebcdic (" " + "A".repeat (200), 1), EBCDIC));
    }

    @Test
    @DisplayName ("o controle correto nao salva um corpo ilegivel")
    void stillChecksTheBody ()
    {
      byte[] buffer = new byte[] { 0x40, 0x01, 0x02 };      // espaco EBCDIC + controle

      assertFalse (report.test (new Record (buffer, 0, buffer.length, 1), EBCDIC));
    }

    @Test
    @DisplayName ("o primeiro registro comecando com quatro digitos 0/1 e um listing")
    void rejectsProgramListing ()
    {
      // uma listagem de programa comeca com numeracao de linha, nao com controle ASA
      assertFalse (report.test (ebcdic ("0101 PROGRAMA", 0), EBCDIC));
    }

    @Test
    @DisplayName ("tres digitos nao caracterizam um listing")
    void threeDigitsAreNotAListing ()
    {
      assertTrue (report.test (ebcdic ("010A PROGRAMA", 0), EBCDIC));
    }

    @Test
    @DisplayName ("a deteccao de listing vale apenas para o registro zero")
    void listingCheckOnlyOnFirstRecord ()
    {
      assertTrue (report.test (ebcdic ("0101 PROGRAMA", 1), EBCDIC));
    }

    @Test
    @DisplayName ("um registro zero curto nao passa pela deteccao de listing")
    void shortFirstRecordSkipsListingCheck ()
    {
      // a verificacao exige mais de 4 bytes
      assertTrue (report.test (ebcdic ("0101", 0), EBCDIC));
    }

    @Test
    @DisplayName ("nome e opcoes de layout")
    void describesItself ()
    {
      assertEquals ("ASA", report.toString ());
      assertFalse (report.newlineBetweenRecords ());
      assertTrue (report.allowSplitRecords ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("NatloadReport")
  class Natload
  // ---------------------------------------------------------------------------------//
  {
    private final NatloadReport report = new NatloadReport (false, false);

    @Test
    @DisplayName ("o registro zero precisa do header FF NAT")
    void firstRecordNeedsHeader ()
    {
      byte[] buffer = new byte[4];
      buffer[0] = (byte) 0xFF;
      buffer[1] = (byte) EbcdicTextMaker.asc2ebc['N'];
      buffer[2] = (byte) EbcdicTextMaker.asc2ebc['A'];
      buffer[3] = (byte) EbcdicTextMaker.asc2ebc['T'];

      assertTrue (report.test (new Record (buffer, 0, 4, 0), EBCDIC));
    }

    @Test
    @DisplayName ("um registro zero sem o header e recusado")
    void firstRecordWithoutHeader ()
    {
      assertFalse (report.test (ebcdic ("NAT", 0), EBCDIC));      // falta o 0xFF
      assertFalse (report.test (bytes (0, 0xFF, 0x40, 0x40, 0x40), EBCDIC));
    }

    @ParameterizedTest (name = "prefixo {0} {1}")
    @CsvSource ({ "0xFF, 0xFF", "0x00, 0x00", "0x00, 0xFF" })
    @DisplayName ("os prefixos de controle sao aceitos direto")
    void acceptsControlPrefixes (int first, int second)
    {
      assertTrue (report.test (bytes (1, first, second, 0x40, 0x40), EBCDIC));
    }

    @Test
    @DisplayName ("dois espacos EBCDIC no inicio nao sao um registro natload")
    void rejectsTwoSpaces ()
    {
      assertFalse (report.test (bytes (1, 0x40, 0x40, 0x40), EBCDIC));
    }

    @Test
    @DisplayName ("registros curtos ou longos demais sao recusados")
    void rejectsSizeExtremes ()
    {
      assertFalse (report.test (bytes (1, 0xFF), EBCDIC));               // 1 byte
      assertFalse (report.test (bytes (1, new int[253]), EBCDIC));       // 253 bytes
    }

    @Test
    @DisplayName ("dois bytes acima de 95 fazem o teste olhar so os 16 primeiros bytes")
    void highPrefixChecksSixteenBytes ()
    {
      // 0xC1 0xC2 = 'A' 'B' em EBCDIC, ambos > 95
      byte[] buffer = new byte[20];
      Arrays.fill (buffer, (byte) 0xC1);
      buffer[18] = 0x01;                     // depois do 16o byte: nao e examinado

      assertTrue (report.test (new Record (buffer, 0, buffer.length, 1), EBCDIC));
    }

    @Test
    @DisplayName ("um registro de 63 bytes com numeracao de linha e aceito sem teste")
    void sixtyThreeBytesIsAlwaysValid ()
    {
      byte[] buffer = new byte[63];
      buffer[0] = 0x00;
      buffer[1] = 0x10;                      // i1 = 0, i2 = 16: nenhum passa de 95
      buffer[30] = 0x01;                     // lixo que seria recusado no teste normal

      assertTrue (report.test (new Record (buffer, 0, 63, 1), EBCDIC));
    }

    @Test
    @DisplayName ("fora dos 63 bytes o corpo depois da numeracao precisa ser legivel")
    void otherLengthsTestTheBody ()
    {
      byte[] valid = new byte[10];
      valid[0] = 0x00;
      valid[1] = 0x10;
      Arrays.fill (valid, 2, valid.length, (byte) 0x40);   // espacos EBCDIC

      assertTrue (report.test (new Record (valid, 0, valid.length, 1), EBCDIC));

      byte[] invalid = valid.clone ();
      invalid[5] = 0x01;

      assertFalse (report.test (new Record (invalid, 0, invalid.length, 1), EBCDIC));
    }

    @Test
    @DisplayName ("nome e opcoes de layout")
    void describesItself ()
    {
      assertEquals ("Natload", report.toString ());
      assertFalse (report.newlineBetweenRecords ());
      assertFalse (report.allowSplitRecords ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("Page")
  class Pages
  // ---------------------------------------------------------------------------------//
  {
    private final List<Record> records = new ArrayList<> ();

    Pages ()
    {
      for (int i = 0; i < 5; i++)
        records.add (ascii ("registro " + i, i));
    }

    @Test
    @DisplayName ("guarda a faixa de registros que a pagina cobre")
    void keepsRecordRange ()
    {
      Page page = new Page (records, 1, 3);

      assertEquals (1, page.getFirstRecordIndex ());
      assertEquals (3, page.getLastRecordIndex ());
    }

    @Test
    @DisplayName ("os deslocamentos comecam em zero")
    void offsetsStartAtZero ()
    {
      Page page = new Page (records, 0, 4);

      assertEquals (0, page.getFirstRecordOffset ());
      assertEquals (0, page.getLastRecordOffset ());
    }

    @Test
    @DisplayName ("os deslocamentos marcam registros partidos entre paginas")
    void offsetsMarkSplitRecords ()
    {
      Page page = new Page (records, 0, 4);

      page.setFirstRecordOffset (10);
      page.setLastRecordOffset (25);

      assertEquals (10, page.getFirstRecordOffset ());
      assertEquals (25, page.getLastRecordOffset ());
    }

    @Test
    @DisplayName ("toString resume a pagina numa linha")
    void describesItself ()
    {
      Page page = new Page (records, 1, 3);
      page.setLastRecordOffset (7);

      assertEquals ("Records:    5, first:    1 (    0), last:    3 (    7)",
                    page.toString ());
    }
  }
}
