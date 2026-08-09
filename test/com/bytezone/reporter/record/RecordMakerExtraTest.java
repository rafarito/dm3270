package com.bytezone.reporter.record;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.bytezone.reporter.text.AsciiTextMaker;
import com.bytezone.reporter.text.EbcdicTextMaker;
import com.bytezone.reporter.text.TextMaker;

// -----------------------------------------------------------------------------------//
@DisplayName ("RecordMaker - formatos ainda sem cobertura (Single, CR, NVB, Ravel)")
class RecordMakerExtraTest
// -----------------------------------------------------------------------------------//
{
  private static final TextMaker EBCDIC = new EbcdicTextMaker ();
  private static final TextMaker ASCII = new AsciiTextMaker ();

  private static byte[] ascii (String text)
  {
    return text.getBytes (java.nio.charset.StandardCharsets.ISO_8859_1);
  }

  private static byte ebcdic (char c)
  {
    return (byte) EbcdicTextMaker.asc2ebc[c];
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("SingleRecordMaker")
  class Single
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("o arquivo inteiro vira um registro so")
    void oneRecordForEverything ()
    {
      SingleRecordMaker maker = new SingleRecordMaker ();
      maker.setBuffer (ascii ("linha 1\nlinha 2\nlinha 3"));

      List<Record> records = maker.getRecords ();

      assertEquals (1, records.size ());
      assertEquals (0, records.get (0).offset);
      assertEquals (23, records.get (0).length);
      assertEquals (0, records.get (0).recordNumber);
    }

    @Test
    @DisplayName ("um buffer vazio ainda gera um registro de tamanho zero")
    void emptyBuffer ()
    {
      SingleRecordMaker maker = new SingleRecordMaker ();
      maker.setBuffer (new byte[0]);

      assertEquals (1, maker.getRecords ().size ());
      assertEquals (0, maker.getRecords ().get (0).length);
    }

    @Test
    @DisplayName ("createSampleRecords com amostra menor recorta o buffer")
    void sampleIsShorter ()
    {
      SingleRecordMaker maker = new SingleRecordMaker ();
      maker.setBuffer (new byte[100]);

      assertEquals (10, maker.createSampleRecords (10).get (0).length);
    }

    @Test
    @DisplayName ("createSampleRecords maior que o buffer devolve o registro inteiro")
    void sampleCoversEverything ()
    {
      SingleRecordMaker maker = new SingleRecordMaker ();
      maker.setBuffer (new byte[10]);

      assertEquals (10, maker.createSampleRecords (100).get (0).length);
      assertSameRecords (maker.getRecords (), maker.createSampleRecords (100));
    }

    @Test
    @DisplayName ("o peso baixo faz o formato perder de qualquer outro")
    void weighsLittle ()
    {
      assertEquals (0.1, new SingleRecordMaker ().weight ());
      assertEquals ("One record", new SingleRecordMaker ().toString ());
    }

    @Test
    @DisplayName ("join devolve o conteudo do registro unico")
    void joinRebuildsBuffer ()
    {
      SingleRecordMaker maker = new SingleRecordMaker ();
      byte[] original = ascii ("linha 1\nlinha 2");
      maker.setBuffer (original);

      SingleRecordMaker rebuilder = new SingleRecordMaker ();
      rebuilder.setRecords (maker.getRecords ());

      assertArrayEquals (original, rebuilder.getBuffer ());
    }

    @Test
    @DisplayName ("join de uma lista vazia devolve buffer vazio")
    void joinOfNothing ()
    {
      SingleRecordMaker maker = new SingleRecordMaker ();
      maker.setRecords (new ArrayList<> ());

      assertEquals (0, maker.getBuffer ().length);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("CrRecordMaker")
  class CarriageReturn
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("quebra em cada 0x0D e descarta o separador")
    void splitsOnCarriageReturn ()
    {
      CrRecordMaker maker = new CrRecordMaker ();
      maker.setBuffer (ascii ("um\rdois\rtres"));

      List<Record> records = maker.getRecords ();

      assertEquals (3, records.size ());
      assertEquals ("um", text (records.get (0)));
      assertEquals ("dois", text (records.get (1)));
      assertEquals ("tres", text (records.get (2)));
    }

    @Test
    @DisplayName ("a ultima linha sem terminador vira registro mesmo assim")
    void keepsUnterminatedLastLine ()
    {
      CrRecordMaker maker = new CrRecordMaker ();
      maker.setBuffer (ascii ("um\rdois"));

      assertEquals (2, maker.getRecords ().size ());
      assertEquals ("dois", text (maker.getRecords ().get (1)));
    }

    @Test
    @DisplayName ("um terminador no fim nao cria registro vazio extra")
    void trailingSeparator ()
    {
      CrRecordMaker maker = new CrRecordMaker ();
      maker.setBuffer (ascii ("um\r"));

      assertEquals (1, maker.getRecords ().size ());
    }

    @Test
    @DisplayName ("0x0D consecutivos geram registros vazios")
    void emptyRecords ()
    {
      CrRecordMaker maker = new CrRecordMaker ();
      maker.setBuffer (ascii ("um\r\r\rdois"));

      List<Record> records = maker.getRecords ();

      assertEquals (4, records.size ());
      assertEquals (0, records.get (1).length);
      assertEquals (0, records.get (2).length);
    }

    @Test
    @DisplayName ("um LF nao e separador para este formato")
    void lineFeedIsNotASeparator ()
    {
      CrRecordMaker maker = new CrRecordMaker ();
      maker.setBuffer (ascii ("um\ndois"));

      assertEquals (1, maker.getRecords ().size ());
    }

    @Test
    @DisplayName ("os registros sao numerados em sequencia")
    void numbersRecordsInOrder ()
    {
      // AsaReport e NatloadReport tratam o registro 0 de forma especial, entao a
      // numeracao precisa estar certa para o formato ser reconhecido
      CrRecordMaker maker = new CrRecordMaker ();
      maker.setBuffer (ascii ("um\rdois\rtres"));

      List<Record> records = maker.getRecords ();

      assertEquals (3, records.size ());
      for (int i = 0; i < records.size (); i++)
        assertEquals (i, records.get (i).recordNumber);
    }

    @Test
    @DisplayName ("join devolve os registros separados por 0x0D")
    void joinRebuildsBuffer ()
    {
      CrRecordMaker maker = new CrRecordMaker ();
      maker.setBuffer (ascii ("um\rdois\r"));
      List<Record> records = maker.getRecords ();

      CrRecordMaker rebuilder = new CrRecordMaker ();
      rebuilder.setRecords (records);

      assertArrayEquals (ascii ("um\rdois\r"), rebuilder.getBuffer ());
    }

    @Test
    @DisplayName ("nome e peso")
    void describesItself ()
    {
      assertEquals ("CR", new CrRecordMaker ().toString ());
      assertEquals (0.9, new CrRecordMaker ().weight ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("NvbRecordMaker")
  class Nvb
  // ---------------------------------------------------------------------------------//
  {
    // Um dataset NVB e uma sequencia de cabecalhos de 24 bytes; cada cabecalho declara
    // nas posicoes 18..20 quantas linhas de 94 bytes vem depois dele.
    private byte[] dataset (String lineCount, int sourceLines)
    {
      byte[] buffer = new byte[24 + 24 + sourceLines * 94];

      buffer[0] = (byte) 0xFF;                // assinatura do formato
      buffer[7] = (byte) 0xFF;
      buffer[14] = (byte) 0xFF;

      buffer[24] = 0x01;                      // segundo cabecalho: nem 0x00 nem 0xFF
      for (int i = 0; i < lineCount.length (); i++)
        buffer[24 + 18 + i] = ebcdic (lineCount.charAt (i));

      return buffer;
    }

    @Test
    @DisplayName ("le o cabecalho e as linhas de fonte que ele declara")
    void splitsHeadersAndSource ()
    {
      NvbRecordMaker maker = new NvbRecordMaker ();
      maker.setBuffer (dataset ("002", 2));

      List<Record> records = maker.getRecords ();

      assertEquals (4, records.size ());
      assertEquals (24, records.get (0).length);       // primeiro cabecalho
      assertEquals (24, records.get (1).length);       // segundo cabecalho
      assertEquals (94, records.get (2).length);       // linha de fonte
      assertEquals (94, records.get (3).length);
    }

    @Test
    @DisplayName ("os registros sao numerados em sequencia")
    void numbersRecords ()
    {
      NvbRecordMaker maker = new NvbRecordMaker ();
      maker.setBuffer (dataset ("001", 1));

      List<Record> records = maker.getRecords ();

      for (int i = 0; i < records.size (); i++)
        assertEquals (i, records.get (i).recordNumber);
    }

    @Test
    @DisplayName ("um cabecalho sem linhas declaradas nao consome fonte")
    void zeroLinesDeclared ()
    {
      NvbRecordMaker maker = new NvbRecordMaker ();
      maker.setBuffer (dataset ("000", 0));

      assertEquals (2, maker.getRecords ().size ());
    }

    @ParameterizedTest (name = "byte {0} fora da assinatura")
    @ValueSource (ints = { 0, 7, 14 })
    @DisplayName ("sem os tres 0xFF da assinatura nao ha registros")
    void requiresSignature (int position)
    {
      byte[] buffer = dataset ("001", 1);
      buffer[position] = 0x00;

      NvbRecordMaker maker = new NvbRecordMaker ();
      maker.setBuffer (buffer);

      assertTrue (maker.getRecords ().isEmpty ());
    }

    @Test
    @DisplayName ("um buffer menor que 16 bytes e recusado de imediato")
    void tooShort ()
    {
      NvbRecordMaker maker = new NvbRecordMaker ();
      maker.setBuffer (new byte[] { (byte) 0xFF, 0, 0, 0, 0, 0, 0, (byte) 0xFF });

      assertTrue (maker.getRecords ().isEmpty ());
    }

    @Test
    @DisplayName ("um contador de linhas ilegivel descarta tudo o que foi lido")
    void invalidLineCountDiscardsEverything ()
    {
      NvbRecordMaker maker = new NvbRecordMaker ();
      maker.setBuffer (dataset ("ABC", 1));

      assertTrue (maker.getRecords ().isEmpty ());
    }

    @Test
    @DisplayName ("um buffer truncado no meio de um registro para antes de estourar")
    void stopsOnTruncatedRecord ()
    {
      byte[] full = dataset ("002", 2);
      byte[] truncated = Arrays.copyOf (full, full.length - 50);   // corta a ultima linha

      NvbRecordMaker maker = new NvbRecordMaker ();
      maker.setBuffer (truncated);

      assertEquals (3, maker.getRecords ().size ());
    }

    @Test
    @DisplayName ("join concatena os registros sem separador")
    void joinConcatenates ()
    {
      NvbRecordMaker maker = new NvbRecordMaker ();
      byte[] original = dataset ("001", 1);
      maker.setBuffer (original);

      NvbRecordMaker rebuilder = new NvbRecordMaker ();
      rebuilder.setRecords (maker.getRecords ());

      assertArrayEquals (original, rebuilder.getBuffer ());
    }

    @Test
    @DisplayName ("nome e peso default")
    void describesItself ()
    {
      assertEquals ("NVB", new NvbRecordMaker ().toString ());
      assertEquals (1.0, new NvbRecordMaker ().weight ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("RavelRecordMaker")
  class Ravel
  // ---------------------------------------------------------------------------------//
  {
    // O formato Ravel termina cada registro com FF 01 e o arquivo com FF 02; um 0xFF
    // dentro do conteudo aparece duplicado.
    private byte[] stream (int... values)
    {
      byte[] buffer = new byte[values.length];
      for (int i = 0; i < values.length; i++)
        buffer[i] = (byte) values[i];

      return buffer;
    }

    @Test
    @DisplayName ("quebra em cada marcador FF 01")
    void splitsOnEndOfRecord ()
    {
      RavelRecordMaker maker = new RavelRecordMaker ();
      maker.setBuffer (stream (0x41, 0x42, 0xFF, 0x01, 0x43, 0x44, 0xFF, 0x01, 0xFF,
                              0x02));

      List<Record> records = maker.getRecords ();

      assertEquals (2, records.size ());
      assertEquals (2, records.get (0).length);
      assertEquals (2, records.get (1).length);
      assertEquals (0, records.get (0).recordNumber);
      assertEquals (1, records.get (1).recordNumber);
    }

    @Test
    @DisplayName ("FF 02 encerra a leitura")
    void stopsOnEndOfFile ()
    {
      RavelRecordMaker maker = new RavelRecordMaker ();
      maker.setBuffer (stream (0x41, 0xFF, 0x01, 0xFF, 0x02, 0x42, 0x43, 0xFF, 0x01));

      // o que vem depois do FF 02 e ignorado
      assertEquals (1, maker.getRecords ().size ());
    }

    @Test
    @DisplayName ("FF FF no conteudo representa um unico 0xFF")
    void unescapesDoubledFF ()
    {
      RavelRecordMaker maker = new RavelRecordMaker ();
      maker.setBuffer (stream (0x41, 0xFF, 0xFF, 0x42, 0xFF, 0x01, 0xFF, 0x02));

      List<Record> records = maker.getRecords ();

      assertEquals (1, records.size ());
      assertEquals (3, records.get (0).length);
      assertArrayEquals (stream (0x41, 0xFF, 0x42),
                         Arrays.copyOfRange (records.get (0).buffer,
                                             records.get (0).offset,
                                             records.get (0).offset + 3));
    }

    @Test
    @DisplayName ("um FF seguido de outro byte encerra a leitura")
    void unknownEscapeStops ()
    {
      RavelRecordMaker maker = new RavelRecordMaker ();
      maker.setBuffer (stream (0x41, 0xFF, 0x03, 0x42, 0xFF, 0x01, 0xFF, 0x02));

      assertTrue (maker.getRecords ().isEmpty ());
    }

    @Test
    @DisplayName ("um registro vazio e valido")
    void emptyRecord ()
    {
      RavelRecordMaker maker = new RavelRecordMaker ();
      maker.setBuffer (stream (0xFF, 0x01, 0xFF, 0x02));

      assertEquals (1, maker.getRecords ().size ());
      assertEquals (0, maker.getRecords ().get (0).length);
    }

    @Test
    @DisplayName ("sem marcador de fim de registro nada e emitido")
    void needsEndOfRecordMarker ()
    {
      RavelRecordMaker maker = new RavelRecordMaker ();
      maker.setBuffer (stream (0x41, 0x42, 0x43));

      assertTrue (maker.getRecords ().isEmpty ());
    }

    @Test
    @DisplayName ("join reconstroi um arquivo sem 0xFF no conteudo")
    void joinWithoutEscapes ()
    {
      RavelRecordMaker maker = new RavelRecordMaker ();
      byte[] original = stream (0x41, 0x42, 0xFF, 0x01, 0x43, 0xFF, 0x01, 0xFF, 0x02);
      maker.setBuffer (original);
      List<Record> records = maker.getRecords ();

      RavelRecordMaker rebuilder = new RavelRecordMaker ();
      rebuilder.setRecords (records);

      assertArrayEquals (original, rebuilder.getBuffer ());
    }

    @Test
    @DisplayName ("join duplica o 0xFF do conteudo sem tocar o buffer de origem")
    void joinEscapesIntoTheTargetBuffer ()
    {
      // packRecord () recebe o buffer de destino por parametro; usar o campo `buffer`
      // gravaria por cima dos dados de origem
      RavelRecordMaker maker = new RavelRecordMaker ();
      byte[] source = new byte[32];
      maker.setBuffer (source);

      byte[] content = stream (0x41, 0xFF, 0x42);      // contem 0xFF: precisa de escape
      List<Record> records = new ArrayList<> ();
      records.add (new Record (content, 0, content.length, 0));

      byte[] result = maker.join (records);

      // conteudo com o 0xFF duplicado, fim de registro e fim de arquivo
      assertArrayEquals (stream (0x41, 0xFF, 0xFF, 0x42, 0xFF, 0x01, 0xFF, 0x02),
                         result);
      assertArrayEquals (new byte[32], source, "o buffer de origem foi alterado");
    }

    @Test
    @DisplayName ("join funciona sem buffer de origem")
    void joinWithoutSourceBuffer ()
    {
      RavelRecordMaker maker = new RavelRecordMaker ();

      byte[] content = stream (0x41, 0xFF, 0x42);
      List<Record> records = new ArrayList<> ();
      records.add (new Record (content, 0, content.length, 0));

      // getBuffer () chama join () justamente quando buffer ainda e null
      maker.setRecords (records);

      assertArrayEquals (stream (0x41, 0xFF, 0xFF, 0x42, 0xFF, 0x01, 0xFF, 0x02),
                         maker.getBuffer ());
    }

    @Test
    @DisplayName ("um registro com escape sobrevive ao round trip")
    void escapedRecordRoundTrips ()
    {
      RavelRecordMaker maker = new RavelRecordMaker ();
      byte[] original = stream (0x41, 0xFF, 0xFF, 0x42, 0xFF, 0x01, 0xFF, 0x02);
      maker.setBuffer (original);

      RavelRecordMaker rebuilder = new RavelRecordMaker ();
      rebuilder.setRecords (maker.getRecords ());

      assertArrayEquals (original, rebuilder.getBuffer ());
    }

    @Test
    @DisplayName ("nome e peso")
    void describesItself ()
    {
      assertEquals ("Ravel", new RavelRecordMaker ().toString ());
      assertEquals (1.1, new RavelRecordMaker ().weight ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("Record.toHex")
  class RecordHexDump
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("um registro vazio devolve apenas o deslocamento")
    void emptyRecord ()
    {
      Record record = new Record (new byte[10], 4, 0, 0);

      assertEquals ("000004", record.toHex (ASCII));
    }

    @Test
    @DisplayName ("uma linha traz deslocamento, hex e texto")
    void singleLine ()
    {
      byte[] buffer = ascii ("ABC");

      assertEquals ("000000  41 42 43                                         ABC",
                    new Record (buffer, 0, 3, 0).toHex (ASCII));
    }

    @Test
    @DisplayName ("o dump nao deixa resto de quebra de linha no fim")
    void noTrailingLineSeparator ()
    {
      // o metodo remove o separador inteiro, e nao um unico caractere: no Windows o
      // separador tem dois
      String dump = new Record (ascii ("ABC"), 0, 3, 0).toHex (ASCII);

      assertTrue (dump.endsWith ("ABC"), dump.replace ("\r", "<CR>"));
    }

    @Test
    @DisplayName ("dezesseis bytes cabem numa linha")
    void exactlyOneLine ()
    {
      byte[] buffer = ascii ("ABCDEFGHIJKLMNOP");

      String[] lines = new Record (buffer, 0, 16, 0).toHex (ASCII).split ("\\R");

      assertEquals (1, lines.length);
      assertTrue (lines[0].endsWith ("ABCDEFGHIJKLMNOP"), lines[0]);
    }

    @Test
    @DisplayName ("o decimo setimo byte comeca uma linha nova")
    void wrapsAfterSixteenBytes ()
    {
      byte[] buffer = ascii ("ABCDEFGHIJKLMNOPQ");

      String[] lines = new Record (buffer, 0, 17, 0).toHex (ASCII).split ("\\R");

      assertEquals (2, lines.length);
      assertTrue (lines[0].startsWith ("000000"), lines[0]);
      assertTrue (lines[1].startsWith ("000010"), lines[1]);
      assertTrue (lines[1].endsWith ("Q"), lines[1]);
    }

    @Test
    @DisplayName ("o deslocamento das linhas parte do offset do registro")
    void offsetIsAbsolute ()
    {
      byte[] buffer = new byte[40];

      String[] lines = new Record (buffer, 20, 20, 0).toHex (ASCII).split ("\\R");

      assertTrue (lines[0].startsWith ("000014"), lines[0]);    // 20 em hexadecimal
      assertTrue (lines[1].startsWith ("000024"), lines[1]);    // 36 em hexadecimal
    }

    @Test
    @DisplayName ("o texto sai na codificacao do TextMaker recebido")
    void textFollowsTheTextMaker ()
    {
      byte[] buffer = { ebcdic ('A'), ebcdic ('B'), ebcdic ('C') };
      Record record = new Record (buffer, 0, 3, 0);

      assertTrue (record.toHex (EBCDIC).endsWith ("ABC"));
      assertFalse (record.toHex (ASCII).endsWith ("ABC"));
    }

    @Test
    @DisplayName ("toString resume numero, deslocamento e tamanho")
    void describesItself ()
    {
      Record record = new Record (new byte[300], 256, 20, 3);

      assertEquals ("    3    256     20  0100  0014", record.toString ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("FbRecordMaker e o corte de nulos")
  class FixedBlockNullTrimming
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("um registro inteiro de nulos vira um registro de tamanho zero")
    void allNullRecordIsTrimmedToNothing ()
    {
      // a guarda do laco de aparo e `reclen > 0`: ao consumir o registro inteiro ele
      // para, em vez de ler um byte antes do inicio do buffer
      FbRecordMaker maker = new FbRecordMaker (4);
      maker.setBuffer (new byte[8]);

      List<Record> records = maker.getRecords ();

      assertEquals (2, records.size ());
      assertEquals (0, records.get (0).length);
      assertEquals (0, records.get (1).length);
    }

    @Test
    @DisplayName ("vale para todos os tamanhos que aparam nulos")
    void everyTrimmingLengthSurvives ()
    {
      for (int recordLength : new int[] { 80, 132, 252 })
      {
        FbRecordMaker maker = new FbRecordMaker (recordLength);
        maker.setBuffer (new byte[recordLength]);

        List<Record> records = maker.getRecords ();

        assertEquals (1, records.size (), "FB" + recordLength);
        assertEquals (0, records.get (0).length, "FB" + recordLength);
      }
    }

    @Test
    @DisplayName ("o FB63 nao apara nulos, entao aceita um registro todo nulo")
    void fb63SurvivesNullRecords ()
    {
      FbRecordMaker maker = new FbRecordMaker (63);
      maker.setBuffer (new byte[126]);

      List<Record> records = maker.getRecords ();

      assertEquals (2, records.size ());
      assertEquals (63, records.get (0).length);
    }

    @Test
    @DisplayName ("um registro com pelo menos um byte nao nulo e aparado sem estourar")
    void trimsUpToTheFirstNonNull ()
    {
      byte[] buffer = new byte[8];
      buffer[0] = 0x41;
      buffer[4] = 0x42;

      FbRecordMaker maker = new FbRecordMaker (4);
      maker.setBuffer (buffer);

      List<Record> records = maker.getRecords ();

      assertEquals (2, records.size ());
      assertEquals (1, records.get (0).length);
      assertEquals (1, records.get (1).length);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("auxiliares do DefaultRecordMaker")
  class Helpers
  // ---------------------------------------------------------------------------------//
  {
    private final FbRecordMaker maker = new FbRecordMaker (80);

    @Test
    @DisplayName ("hasNumbers aceita apenas bytes dentro da faixa")
    void hasNumbersChecksRange ()
    {
      byte[] buffer = { 0x30, 0x31, 0x39 };            // '0', '1', '9' em ASCII

      assertTrue (maker.hasNumbers (buffer, 0, 3, 0x30, 0x39));
      assertFalse (maker.hasNumbers (buffer, 0, 3, 0x31, 0x39));
      assertFalse (maker.hasNumbers (buffer, 0, 3, 0x30, 0x38));
    }

    @Test
    @DisplayName ("hasNumbers de tamanho zero e sempre verdadeiro")
    void hasNumbersOnEmptyRange ()
    {
      assertTrue (maker.hasNumbers (new byte[] { 0x00 }, 0, 0, 0x30, 0x39));
    }

    @Test
    @DisplayName ("countTrailingSpaces conta so o que esta no fim")
    void countsTrailingSpaces ()
    {
      byte[] buffer = { 0x40, 0x41, 0x40, 0x40 };      // espaco EBCDIC nas pontas

      assertEquals (2, maker.countTrailingSpaces (buffer, 0, 4, (byte) 0x40));
      assertEquals (0, maker.countTrailingSpaces (buffer, 0, 2, (byte) 0x40));
    }

    @Test
    @DisplayName ("um registro inteiro de espacos conta todos os bytes")
    void allSpaces ()
    {
      byte[] buffer = { 0x40, 0x40, 0x40 };

      assertEquals (3, maker.countTrailingSpaces (buffer, 0, 3, (byte) 0x40));
    }
  }

  // ---------------------------------------------------------------------------------//
  //  Auxiliares
  // ---------------------------------------------------------------------------------//

  private static String text (Record record)
  {
    return new String (record.buffer, record.offset, record.length,
                       java.nio.charset.StandardCharsets.ISO_8859_1);
  }

  private static void assertSameRecords (List<Record> expected, List<Record> actual)
  {
    assertEquals (expected.size (), actual.size ());
    for (int i = 0; i < expected.size (); i++)
    {
      assertEquals (expected.get (i).offset, actual.get (i).offset);
      assertEquals (expected.get (i).length, actual.get (i).length);
    }
  }
}
