package com.bytezone.dm3270.structuredfields;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.commands.ReadPartitionQuery;
import com.bytezone.dm3270.commands.WriteStructuredFieldCommand;

// -----------------------------------------------------------------------------------//
@DisplayName ("StructuredField - campos estruturados do comando WSF")
class StructuredFieldTest
// -----------------------------------------------------------------------------------//
{
  private static final int WSF = 0xF3;            // Write Structured Field

  // ---------------------------------------------------------------------------------//
  //  Construcao dos buffers
  // ---------------------------------------------------------------------------------//

  // Monta um WSF com um unico campo estruturado. O tamanho de dois bytes que precede
  // cada campo inclui os proprios dois bytes.
  private static WriteStructuredFieldCommand wsf (int... field)
  {
    return wsf (new int[][] { field });
  }

  private static WriteStructuredFieldCommand wsf (int[][] fields)
  {
    ByteArrayOutputStream out = new ByteArrayOutputStream ();
    out.write (WSF);

    for (int[] field : fields)
    {
      int length = field.length + 2;
      out.write ((length >> 8) & 0xFF);
      out.write (length & 0xFF);
      for (int value : field)
        out.write (value);
    }

    byte[] buffer = out.toByteArray ();

    return new WriteStructuredFieldCommand (buffer, 0, buffer.length);
  }

  // Um campo estruturado isolado, sem o envelope do WSF.
  private static byte[] field (int... values)
  {
    byte[] buffer = new byte[values.length];
    for (int i = 0; i < values.length; i++)
      buffer[i] = (byte) values[i];

    return buffer;
  }

  // Um comando Write minimo: WCC seguido de um SBA para a origem.
  private static int[] writeCommand ()
  {
    return new int[] { 0xF1, 0xC3, 0x11, 0x40, 0x40 };
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("dispatch do WSF")
  class Dispatch
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("um WSF vazio nao tem campo nenhum")
    void emptyCommand ()
    {
      byte[] buffer = { (byte) WSF };

      WriteStructuredFieldCommand command =
          new WriteStructuredFieldCommand (buffer, 0, 1);

      assertEquals ("WSF (0):", command.toString ());
      assertEquals ("WSF (0):", command.brief ());
      assertEquals ("Write SF", command.getName ());
      assertFalse (command.getReply ().isPresent ());
    }

    @Test
    @DisplayName ("o codigo 11 tambem identifica um WSF")
    void alternateOpcode ()
    {
      byte[] buffer = { 0x11, 0x00, 0x04, StructuredField.ERASE_RESET, 0x00 };

      WriteStructuredFieldCommand command =
          new WriteStructuredFieldCommand (buffer, 0, buffer.length);

      assertTrue (command.toString ().contains ("Erase/Reset"));
    }

    @Test
    @DisplayName ("um opcode que nao e WSF viola a assercao")
    void rejectsOtherCommands ()
    {
      byte[] buffer = { (byte) 0xF1, 0x00, 0x04, 0x03, 0x00 };

      assertThrows (AssertionError.class,
                    () -> new WriteStructuredFieldCommand (buffer, 0, buffer.length));
    }

    @Test
    @DisplayName ("varios campos no mesmo comando sao lidos em sequencia")
    void readsSeveralFields ()
    {
      WriteStructuredFieldCommand command = wsf (new int[][] {
          { StructuredField.ERASE_RESET, 0x00 },
          { StructuredField.SET_REPLY_MODE, 0x00, 0x01, 0xC0 },
          { StructuredField.ERASE_RESET, 0xC0 } });

      assertTrue (command.brief ().startsWith ("WSF (3):"), command.brief ());

      String report = command.toString ();

      assertEquals (2, count (report, "Erase/Reset"), report);
      assertEquals (1, count (report, "Set Reply Mode"), report);
      assertEquals (1, count (report, "(DEFAULT)"), report);
      assertEquals (1, count (report, "(ALTERNATE)"), report);
    }

    @ParameterizedTest (name = "tipo {0}")
    @ValueSource (ints = { 0x00, 0x0E, 0x06, 0x4A, 0x7F })
    @DisplayName ("tipos sem tratamento proprio caem no campo genérico")
    void unknownTypesFallBack (int type)
    {
      WriteStructuredFieldCommand command = wsf (type, 0x00, 0x00);

      assertTrue (command.toString ().contains ("Unknown SF"), command.toString ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("EraseResetSF")
  class EraseReset
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "flags {0} -> {1}")
    @CsvSource ({ "0x00, DEFAULT", "0x3F, DEFAULT", "0x40, ALTERNATE",
                  "0x80, ALTERNATE", "0xC0, ALTERNATE", "0xFF, ALTERNATE" })
    @DisplayName ("os dois bits altos escolhem tela default ou alternativa")
    void readsSizeFromFlags (int flags, String expected)
    {
      byte[] buffer = field (StructuredField.ERASE_RESET, flags);
      EraseResetSF sf = new EraseResetSF (buffer, 0, buffer.length);

      assertTrue (sf.toString ().contains ("(" + expected + ")"), sf.toString ());
      assertTrue (sf.toString ().startsWith ("Struct Field : 03 Erase/Reset"));
    }

    @Test
    @DisplayName ("o valor bruto das flags aparece no relatorio")
    void showsRawFlags ()
    {
      byte[] buffer = field (StructuredField.ERASE_RESET, 0x80);

      assertTrue (new EraseResetSF (buffer, 0, buffer.length).toString ()
          .contains ("flags     : 80"));
    }

    @Test
    @DisplayName ("um tipo diferente de 03 viola a assercao")
    void rejectsWrongType ()
    {
      byte[] buffer = field (0x04, 0x00);

      assertThrows (AssertionError.class,
                    () -> new EraseResetSF (buffer, 0, buffer.length));
    }

    @Test
    @DisplayName ("process nao faz nada e nao precisa de tela")
    void processIsANoOp ()
    {
      byte[] buffer = field (StructuredField.ERASE_RESET, 0x00);
      EraseResetSF sf = new EraseResetSF (buffer, 0, buffer.length);

      sf.process (null);

      assertFalse (sf.getReply ().isPresent ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("SetReplyModeSF")
  class SetReplyMode
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "modo {0} -> {1}")
    @CsvSource ({ "0x00, Field", "0x01, 'Extended field'", "0x02, Character" })
    @DisplayName ("os tres modos de resposta")
    void describesMode (int mode, String expected)
    {
      byte[] buffer = field (StructuredField.SET_REPLY_MODE, 0x00, mode);
      SetReplyModeSF sf = new SetReplyModeSF (buffer, 0, buffer.length);

      assertTrue (sf.toString ().contains (expected + " mode"), sf.toString ());
    }

    @Test
    @DisplayName ("os tipos de atributo declarados saem nomeados")
    void listsAttributeTypes ()
    {
      // 0xC0 = start field, 0x41 = highlight, 0x42 = foreground
      byte[] buffer =
          field (StructuredField.SET_REPLY_MODE, 0x00, 0x02, 0xC0, 0x41, 0x42);
      SetReplyModeSF sf = new SetReplyModeSF (buffer, 0, buffer.length);

      String report = sf.toString ();

      assertEquals (3, report.split ("type      :").length - 1, report);
      assertTrue (report.contains ("type      : C0"), report);
    }

    @Test
    @DisplayName ("sem tipos declarados o relatorio tem so duas linhas")
    void noTypes ()
    {
      byte[] buffer = field (StructuredField.SET_REPLY_MODE, 0x00, 0x00);

      String report = new SetReplyModeSF (buffer, 0, buffer.length).toString ();

      assertFalse (report.contains ("type      :"), report);
    }

    @Test
    @DisplayName ("a particao aparece no relatorio")
    void showsPartition ()
    {
      byte[] buffer = field (StructuredField.SET_REPLY_MODE, 0x7F, 0x00);

      assertTrue (new SetReplyModeSF (buffer, 0, buffer.length).toString ()
          .contains ("partition : 7F"));
    }

    @ParameterizedTest (name = "modo {0}")
    @ValueSource (ints = { 0x03, 0x10, 0x7F })
    @DisplayName ("um modo fora dos tres conhecidos e descrito pelo valor bruto")
    void unknownModeIsDescribed (int mode)
    {
      // a tabela `modes` tem tres entradas; um codigo fora dela nao derruba o relatorio
      byte[] buffer = field (StructuredField.SET_REPLY_MODE, 0x00, mode);
      SetReplyModeSF sf = new SetReplyModeSF (buffer, 0, buffer.length);

      assertTrue (sf.toString ()
          .contains (String.format ("mode      : %02X Unknown (%02X) mode", mode, mode)),
                  sf.toString ());
    }

    @Test
    @DisplayName ("um tipo diferente de 09 viola a assercao")
    void rejectsWrongType ()
    {
      byte[] buffer = field (0x0A, 0x00, 0x00);

      assertThrows (AssertionError.class,
                    () -> new SetReplyModeSF (buffer, 0, buffer.length));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("Outbound3270DS")
  class Outbound
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("embrulha um comando Write comum")
    void wrapsWriteCommand ()
    {
      int[] payload = new int[writeCommand ().length + 2];
      payload[0] = StructuredField.OUTBOUND_3270DS;
      payload[1] = 0x00;                          // particao
      System.arraycopy (writeCommand (), 0, payload, 2, writeCommand ().length);

      byte[] buffer = field (payload);
      Outbound3270DS sf = new Outbound3270DS (buffer, 0, buffer.length);

      assertTrue (sf.toString ().contains ("Outbound3270DS"), sf.toString ());
      assertTrue (sf.toString ().contains ("partition : 00"), sf.toString ());
      assertTrue (sf.brief ().startsWith ("Out3270:"), sf.brief ());
    }

    @Test
    @DisplayName ("a particao precisa caber em 7 bits")
    void rejectsHighPartitionBit ()
    {
      int[] payload = new int[writeCommand ().length + 2];
      payload[0] = StructuredField.OUTBOUND_3270DS;
      payload[1] = 0x80;                          // bit alto ligado
      System.arraycopy (writeCommand (), 0, payload, 2, writeCommand ().length);

      byte[] buffer = field (payload);

      assertThrows (AssertionError.class,
                    () -> new Outbound3270DS (buffer, 0, buffer.length));
    }

    @Test
    @DisplayName ("um tipo diferente de 40 viola a assercao")
    void rejectsWrongType ()
    {
      byte[] buffer = field (0x41, 0x00, 0xF1, 0xC3);

      assertThrows (AssertionError.class,
                    () -> new Outbound3270DS (buffer, 0, buffer.length));
    }

    @Test
    @DisplayName ("chega ao WSF pelo dispatch do tipo 40")
    void reachedThroughDispatch ()
    {
      int[] payload = new int[writeCommand ().length + 2];
      payload[0] = StructuredField.OUTBOUND_3270DS;
      payload[1] = 0x00;
      System.arraycopy (writeCommand (), 0, payload, 2, writeCommand ().length);

      assertTrue (wsf (payload).toString ().contains ("Outbound3270DS"));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("ReadPartitionSF")
  class ReadPartition
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "data[2] = {0}")
    @ValueSource (ints = { 0x02, 0x03 })
    @DisplayName ("a particao FF com query 02 ou 03 embrulha um ReadPartitionQuery")
    void wrapsQuery (int queryType)
    {
      byte[] buffer =
          field (StructuredField.READ_PARTITION, 0xFF, queryType, 0x02);
      ReadPartitionSF sf = new ReadPartitionSF (buffer, 0, buffer.length);

      assertTrue (sf.toString ().contains ("Read Partition"), sf.toString ());
      assertTrue (sf.toString ().contains ("partition : FF"), sf.toString ());
    }

    @Test
    @DisplayName ("uma particao normal embrulha um comando Read")
    void wrapsReadCommand ()
    {
      // 0xF2 = Read Buffer
      byte[] buffer = field (StructuredField.READ_PARTITION, 0x00, 0xF2);
      ReadPartitionSF sf = new ReadPartitionSF (buffer, 0, buffer.length);

      assertTrue (sf.toString ().contains ("partition : 00"), sf.toString ());
    }

    @Test
    @DisplayName ("uma query desconhecida deixa o comando nulo")
    void unknownQueryLeavesNoCommand ()
    {
      byte[] buffer = field (StructuredField.READ_PARTITION, 0xFF, 0x01, 0x00);
      ReadPartitionSF sf = new ReadPartitionSF (buffer, 0, buffer.length);

      // o toString imprime o comando, que nesse caso e null
      assertTrue (sf.toString ().contains ("null"), sf.toString ());
    }

    @Test
    @DisplayName ("brief() descreve o comando embrulhado enquanto nao houver resposta")
    void briefWorksWithoutAReply ()
    {
      byte[] buffer = field (StructuredField.READ_PARTITION, 0xFF, 0x02, 0x02);
      ReadPartitionSF sf = new ReadPartitionSF (buffer, 0, buffer.length);

      assertTrue (sf.brief ().startsWith ("ReadPT:"), sf.brief ());
    }

    @Test
    @DisplayName ("uma particao normal com bit alto ligado viola a assercao")
    void rejectsHighPartitionBit ()
    {
      byte[] buffer = field (StructuredField.READ_PARTITION, 0x80, 0xF2);

      assertThrows (AssertionError.class,
                    () -> new ReadPartitionSF (buffer, 0, buffer.length));
    }

    @Test
    @DisplayName ("um tipo diferente de 01 viola a assercao")
    void rejectsWrongType ()
    {
      byte[] buffer = field (0x02, 0xFF, 0x02, 0x02);

      assertThrows (AssertionError.class,
                    () -> new ReadPartitionSF (buffer, 0, buffer.length));
    }

    @Test
    @DisplayName ("o ReadPartitionQuery embrulhado exige particao FF")
    void queryNeedsPartitionFF ()
    {
      byte[] buffer = field (StructuredField.READ_PARTITION, 0x00, 0x02, 0x02);

      assertThrows (AssertionError.class,
                    () -> new ReadPartitionQuery (buffer, 0, buffer.length));
    }

    @Test
    @DisplayName ("um ReadPartitionQuery ainda nao processado nao tem nome")
    void queryHasNoNameBeforeProcessing ()
    {
      byte[] buffer = field (StructuredField.READ_PARTITION, 0xFF, 0x02, 0x02);
      Command command = new ReadPartitionQuery (buffer, 0, buffer.length);

      assertEquals (null, command.getName ());
      assertEquals ("null", command.toString ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("DefaultStructuredField")
  class Unknown
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("imprime o tipo e um dump do conteudo")
    void dumpsContent ()
    {
      byte[] buffer = field (0x4A, 0x01, 0x02, 0x03);
      DefaultStructuredField sf = new DefaultStructuredField (buffer, 0, buffer.length);

      String report = sf.toString ();

      assertTrue (report.startsWith ("Unknown SF   : 4A"), report);
      assertTrue (report.contains ("4A 01 02 03"), report);
    }

    @Test
    @DisplayName ("process apenas registra o tipo e nao usa a tela")
    void processIsHarmless ()
    {
      byte[] buffer = field (0x4A, 0x00);

      new DefaultStructuredField (buffer, 0, buffer.length).process (null);
    }

    @Test
    @DisplayName ("o brief herdado repete o toString")
    void briefRepeatsToString ()
    {
      byte[] buffer = field (0x4A, 0x00);
      DefaultStructuredField sf = new DefaultStructuredField (buffer, 0, buffer.length);

      assertEquals (sf.toString (), sf.brief ());
    }
  }

  // ---------------------------------------------------------------------------------//
  private static int count (String text, String needle)
  // ---------------------------------------------------------------------------------//
  {
    int total = 0;
    for (int index = text.indexOf (needle); index >= 0;
        index = text.indexOf (needle, index + needle.length ()))
      total++;

    return total;
  }
}
