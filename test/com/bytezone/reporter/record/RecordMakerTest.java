package com.bytezone.reporter.record;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// -----------------------------------------------------------------------------------//
@DisplayName ("RecordMaker - divisao de um dataset em registros")
class RecordMakerTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("FB - blocos fixos")
  class FixedBlock
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("divide o buffer em registros do tamanho declarado")
    void splitsIntoFixedRecords ()
    {
      FbRecordMaker maker = new FbRecordMaker (4);
      maker.setBuffer (bytes ("AAAABBBBCCCC"));

      List<Record> records = maker.getRecords ();

      assertEquals (3, records.size ());
      assertEquals (4, records.get (0).length);
      assertEquals (0, records.get (0).offset);
      assertEquals (4, records.get (1).offset);
      assertEquals (2, records.get (2).recordNumber);
    }

    @Test
    @DisplayName ("descarta o resto incompleto no fim do buffer")
    void discardsPartialTrailingRecord ()
    {
      FbRecordMaker maker = new FbRecordMaker (4);
      maker.setBuffer (bytes ("AAAABB"));

      assertEquals (1, maker.getRecords ().size ());
    }

    @Test
    @DisplayName ("remove os nulos a direita de cada registro")
    void trimsTrailingNulls ()
    {
      FbRecordMaker maker = new FbRecordMaker (4);
      maker.setBuffer (new byte[] { 'A', 'B', 0, 0, 'C', 'D', 'E', 'F' });

      List<Record> records = maker.getRecords ();

      assertEquals (2, records.size ());
      assertEquals (2, records.get (0).length, "os dois nulos foram cortados");
      assertEquals (4, records.get (1).length);
    }

    @Test
    @DisplayName ("join reconstroi um buffer com registros de tamanho fixo")
    void joinRebuildsBuffer ()
    {
      FbRecordMaker maker = new FbRecordMaker (4);
      byte[] original = bytes ("AAAABBBBCCCC");
      maker.setBuffer (original);

      FbRecordMaker rebuilder = new FbRecordMaker (4);
      rebuilder.setRecords (maker.getRecords ());

      assertArrayEquals (original, rebuilder.getBuffer ());
    }

    @Test
    @DisplayName ("createSampleRecords limita a analise aos primeiros bytes")
    void createSampleRecordsLimitsWork ()
    {
      FbRecordMaker maker = new FbRecordMaker (4);
      maker.setBuffer (bytes ("AAAABBBBCCCCDDDD"));

      assertEquals (2, maker.createSampleRecords (8).size ());
      assertEquals (4, maker.createSampleRecords (100).size (),
          "amostra maior que o buffer usa tudo");
    }

    @Test
    @DisplayName ("FB80 tem peso maior que os demais na deteccao de formato")
    void fb80IsPreferred ()
    {
      assertTrue (new FbRecordMaker (80).weight () > new FbRecordMaker (132).weight ());
      assertEquals (80, new FbRecordMaker (80).getRecordLength ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("LF - registros terminados por 0x0A")
  class LineFeed
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("quebra em cada line feed")
    void splitsOnLineFeed ()
    {
      LfRecordMaker maker = new LfRecordMaker ();
      maker.setBuffer (bytes ("um\ndois\ntres\n"));

      List<Record> records = maker.getRecords ();

      assertEquals (3, records.size ());
      assertEquals (2, records.get (0).length);
      assertEquals (4, records.get (1).length);
      assertEquals (4, records.get (2).length);
    }

    @Test
    @DisplayName ("a ultima linha sem terminador vira registro mesmo assim")
    void keepsUnterminatedLastLine ()
    {
      LfRecordMaker maker = new LfRecordMaker ();
      maker.setBuffer (bytes ("um\ndois"));

      assertEquals (2, maker.getRecords ().size ());
    }

    @Test
    @DisplayName ("linhas vazias geram registros de tamanho zero")
    void emptyLinesBecomeEmptyRecords ()
    {
      LfRecordMaker maker = new LfRecordMaker ();
      maker.setBuffer (bytes ("\n\n"));

      List<Record> records = maker.getRecords ();

      assertEquals (2, records.size ());
      assertEquals (0, records.get (0).length);
    }

    @Test
    @DisplayName ("split/join faz round trip")
    void roundTrip ()
    {
      byte[] original = bytes ("um\ndois\ntres\n");
      LfRecordMaker maker = new LfRecordMaker ();
      maker.setBuffer (original);

      LfRecordMaker rebuilder = new LfRecordMaker ();
      rebuilder.setRecords (maker.getRecords ());

      assertArrayEquals (original, rebuilder.getBuffer ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("CR/LF - registros terminados por 0x0D 0x0A")
  class CarriageReturnLineFeed
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("quebra em cada par CR LF, descartando os dois bytes")
    void splitsOnCrLf ()
    {
      CrlfRecordMaker maker = new CrlfRecordMaker ();
      maker.setBuffer (bytes ("um\r\ndois\r\n"));

      List<Record> records = maker.getRecords ();

      assertEquals (2, records.size ());
      assertEquals (2, records.get (0).length);
      assertEquals (4, records.get (1).length);
    }

    @Test
    @DisplayName ("um LF solto nao quebra o registro")
    void loneLineFeedIsNotASeparator ()
    {
      CrlfRecordMaker maker = new CrlfRecordMaker ();
      maker.setBuffer (bytes ("um\ndois\r\n"));

      assertEquals (1, maker.getRecords ().size ());
    }

    @Test
    @DisplayName ("ignora o 0x1A solitario que o IND$FILE acrescenta no fim")
    void ignoresTrailingEofMarker ()
    {
      CrlfRecordMaker maker = new CrlfRecordMaker ();
      maker.setBuffer (new byte[] { 'a', 'b', 0x0D, 0x0A, 0x1A });

      List<Record> records = maker.getRecords ();

      assertEquals (1, records.size ());
      assertEquals (2, records.get (0).length);
    }

    @Test
    @DisplayName ("split/join faz round trip")
    void roundTrip ()
    {
      byte[] original = bytes ("um\r\ndois\r\n");
      CrlfRecordMaker maker = new CrlfRecordMaker ();
      maker.setBuffer (original);

      CrlfRecordMaker rebuilder = new CrlfRecordMaker ();
      rebuilder.setRecords (maker.getRecords ());

      assertArrayEquals (original, rebuilder.getBuffer ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("RDW - record descriptor word")
  class RecordDescriptorWord
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le o tamanho nos dois primeiros bytes de cada registro")
    void readsLengthPrefix ()
    {
      // RDW = tamanho total (incluindo os 4 bytes de cabecalho) + 2 bytes zerados
      byte[] buffer = { 0x00, 0x06, 0x00, 0x00, 'A', 'B', //
                        0x00, 0x07, 0x00, 0x00, 'C', 'D', 'E' };

      RdwRecordMaker maker = new RdwRecordMaker ();
      maker.setBuffer (buffer);

      List<Record> records = maker.getRecords ();

      assertEquals (2, records.size ());
      assertEquals (2, records.get (0).length);
      assertEquals (3, records.get (1).length);
    }

    @Test
    @DisplayName ("para ao encontrar um tamanho zerado")
    void stopsOnZeroLength ()
    {
      byte[] buffer = { 0x00, 0x06, 0x00, 0x00, 'A', 'B', 0x00, 0x00, 0x00, 0x00 };

      RdwRecordMaker maker = new RdwRecordMaker ();
      maker.setBuffer (buffer);

      assertEquals (1, maker.getRecords ().size ());
    }

    @Test
    @DisplayName ("para quando os bytes de preenchimento nao sao zero")
    void stopsOnNonZeroFiller ()
    {
      byte[] buffer = { 0x00, 0x06, 0x00, 0x01, 'A', 'B' };

      RdwRecordMaker maker = new RdwRecordMaker ();
      maker.setBuffer (buffer);

      assertEquals (0, maker.getRecords ().size ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("VB - blocos variaveis")
  class VariableBlock
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le o preenchimento antes do tamanho (ordem inversa do RDW)")
    void readsFillerThenLength ()
    {
      byte[] buffer = { 0x00, 0x00, 0x00, 0x06, 'A', 'B', //
                        0x00, 0x00, 0x00, 0x07, 'C', 'D', 'E' };

      VbRecordMaker maker = new VbRecordMaker ();
      maker.setBuffer (buffer);

      List<Record> records = maker.getRecords ();

      assertEquals (2, records.size ());
      assertEquals (2, records.get (0).length);
      assertEquals (3, records.get (1).length);
    }

    @Test
    @DisplayName ("para quando o preenchimento nao e zero")
    void stopsOnNonZeroFiller ()
    {
      byte[] buffer = { 0x00, 0x01, 0x00, 0x06, 'A', 'B' };

      VbRecordMaker maker = new VbRecordMaker ();
      maker.setBuffer (buffer);

      assertEquals (0, maker.getRecords ().size ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("Record")
  class RecordBehaviour
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("countTrailingNulls conta apenas os nulos do fim")
    void countsTrailingNulls ()
    {
      byte[] buffer = { 'A', 0, 'B', 0, 0, 0 };

      assertEquals (3, new Record (buffer, 0, 6, 0).countTrailingNulls ());
      assertEquals (0, new Record (buffer, 0, 3, 0).countTrailingNulls ());
    }

    @Test
    @DisplayName ("registro todo nulo conta todos os bytes")
    void allNulls ()
    {
      assertEquals (4, new Record (new byte[4], 0, 4, 0).countTrailingNulls ());
    }

    @Test
    @DisplayName ("registro vazio conta zero")
    void emptyRecord ()
    {
      assertEquals (0, new Record (new byte[4], 0, 0, 0).countTrailingNulls ());
    }
  }

  // ---------------------------------------------------------------------------------//
  private static byte[] bytes (String text)
  // ---------------------------------------------------------------------------------//
  {
    return text.getBytes (StandardCharsets.ISO_8859_1);
  }
}
