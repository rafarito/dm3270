package com.bytezone.dm3270.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.bytezone.dm3270.display.ScreenDimensions;
import com.bytezone.dm3270.replyfield.QueryReplyField;
import com.bytezone.dm3270.streams.TelnetState;
import com.bytezone.dm3270.structuredfields.StructuredField;

// -----------------------------------------------------------------------------------//
@DisplayName ("ReadStructuredFieldCommand - resposta de capacidades ao host")
class ReadStructuredFieldCommandTest
// -----------------------------------------------------------------------------------//
{
  // Monta um RSF: o AID de campo estruturado seguido de campos com prefixo de tamanho.
  private static byte[] rsf (byte[]... fields)
  {
    ByteArrayOutputStream out = new ByteArrayOutputStream ();
    out.write (AIDCommand.AID_STRUCTURED_FIELD);

    for (byte[] field : fields)
    {
      int length = field.length + 2;
      out.write ((length >> 8) & 0xFF);
      out.write (length & 0xFF);
      out.write (field, 0, field.length);
    }

    return out.toByteArray ();
  }

  private static byte[] queryReply (byte type, int... rest)
  {
    byte[] field = new byte[rest.length + 2];
    field[0] = StructuredField.QUERY_REPLY;
    field[1] = type;
    for (int i = 0; i < rest.length; i++)
      field[i + 2] = (byte) rest[i];

    return field;
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("resposta construida a partir do estado da sessao")
  class BuiltReply
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("declara dez query replies, incluindo o resumo")
    void listsTenReplies ()
    {
      ReadStructuredFieldCommand command =
          new ReadStructuredFieldCommand (new TelnetState ());

      assertEquals (10, count (command.toString (), "Type       :"),
                    command.toString ());
    }

    @Test
    @DisplayName ("o primeiro byte e o AID de campo estruturado")
    void startsWithAid ()
    {
      ReadStructuredFieldCommand command =
          new ReadStructuredFieldCommand (new TelnetState ());

      assertEquals (AIDCommand.AID_STRUCTURED_FIELD, command.getData ()[0]);
      assertEquals ("Read SF", command.getName ());
    }

    @ParameterizedTest (name = "modelo {0} -> {1} x {2}")
    @CsvSource ({ "2, 24, 80", "3, 32, 80", "4, 43, 80", "5, 27, 132" })
    @DisplayName ("a area utilizavel segue o modelo negociado")
    void followsNegotiatedModel (int model, int rows, int columns)
    {
      TelnetState telnetState = new TelnetState ();
      telnetState.setDeviceType ("IBM-3278-" + model + "-E");

      ReadStructuredFieldCommand command =
          new ReadStructuredFieldCommand (telnetState);

      ScreenDimensions dimensions = command.getScreenDimensions ();

      assertNotNull (dimensions);
      assertEquals (rows, dimensions.rows);
      assertEquals (columns, dimensions.columns);
    }

    @Test
    @DisplayName ("a resposta se identifica como dm3270")
    void identifiesItself ()
    {
      ReadStructuredFieldCommand command =
          new ReadStructuredFieldCommand (new TelnetState ());

      assertTrue (command.toString ().contains ("dm3270"), command.toString ());
    }

    @Test
    @DisplayName ("todas as replies enviadas constam do resumo")
    void everythingIsListedInTheSummary ()
    {
      ReadStructuredFieldCommand command =
          new ReadStructuredFieldCommand (new TelnetState ());

      String text = command.toString ();

      assertFalse (text.contains ("** missing **"), text);
      assertFalse (text.contains ("Not listed in Summary"), text);
    }

    @Test
    @DisplayName ("a assinatura MD5 identifica a versao do cliente")
    void reportsSignature ()
    {
      TelnetState telnetState = new TelnetState ();
      telnetState.setDeviceType ("IBM-3278-2-E");

      ReadStructuredFieldCommand command =
          new ReadStructuredFieldCommand (telnetState);

      String text = command.toString ();

      assertTrue (text.contains ("Checksum     :"), text);
      assertTrue (text.contains ("Client name  :"), text);
      assertTrue (command.getClientName ().isPresent ());
    }

    @Test
    @DisplayName ("a resposta gerada pode ser lida de volta")
    void generatedReplyIsParseable ()
    {
      byte[] buffer =
          new ReadStructuredFieldCommand (new TelnetState ()).getData ();

      ReadStructuredFieldCommand parsed = new ReadStructuredFieldCommand (buffer);

      assertEquals (10, count (parsed.toString (), "Type       :"), parsed.toString ());
      assertNotNull (parsed.getScreenDimensions ());
    }

    @Test
    @DisplayName ("process nao precisa de tela")
    void processIsANoOp ()
    {
      new ReadStructuredFieldCommand (new TelnetState ()).process (null);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("resposta lida de uma sessao gravada")
  class ParsedReply
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le um unico query reply")
    void readsSingleReply ()
    {
      // data[3] declara os pares de cor, e cada par ocupa dois bytes a partir de data[4]
      byte[] buffer = rsf (queryReply (QueryReplyField.COLOR_QUERY_REPLY, 0x00, 0x02,
                                       0x00, 0xF4, 0xF1, 0xF1));

      ReadStructuredFieldCommand command = new ReadStructuredFieldCommand (buffer);

      assertTrue (command.toString ().contains ("Color"), command.toString ());
      assertEquals (1, count (command.toString (), "Type       :"),
                    command.toString ());
    }

    @Test
    @DisplayName ("le varios query replies em sequencia")
    void readsSeveralReplies ()
    {
      byte[] buffer = rsf (queryReply (QueryReplyField.SUMMARY_QUERY_REPLY, 0x80, 0x86),
                           queryReply (QueryReplyField.COLOR_QUERY_REPLY, 0x00, 0x00));

      ReadStructuredFieldCommand command = new ReadStructuredFieldCommand (buffer);

      assertEquals (2, count (command.toString (), "Type       :"),
                    command.toString ());
      assertFalse (command.toString ().contains ("** missing **"),
                   command.toString ());
    }

    @Test
    @DisplayName ("uma area utilizavel na resposta define as dimensoes da tela")
    void readsScreenDimensions ()
    {
      byte[] usableArea = queryReply (QueryReplyField.USABLE_AREA_REPLY, //
                                      0x01, 0x00, 0x00, 0x50, 0x00, 0x18, 0x00, //
                                      0x00, 0x0A, 0x00, 0x02, 0x00, 0x04, 0x00, 0x03, //
                                      0x09, 0x0C, 0x07, 0x80);

      ReadStructuredFieldCommand command =
          new ReadStructuredFieldCommand (rsf (usableArea));

      assertEquals (24, command.getScreenDimensions ().rows);
      assertEquals (80, command.getScreenDimensions ().columns);
    }

    @Test
    @DisplayName ("sem area utilizavel as dimensoes ficam indefinidas")
    void noScreenDimensions ()
    {
      byte[] buffer =
          rsf (queryReply (QueryReplyField.COLOR_QUERY_REPLY, 0x00, 0x00));

      assertNull (new ReadStructuredFieldCommand (buffer).getScreenDimensions ());
    }

    @Test
    @DisplayName ("uma assinatura desconhecida e reportada como Unknown")
    void unknownClientSignature ()
    {
      byte[] buffer =
          rsf (queryReply (QueryReplyField.COLOR_QUERY_REPLY, 0x00, 0x00));

      ReadStructuredFieldCommand command = new ReadStructuredFieldCommand (buffer);

      assertEquals ("Unknown", command.getClientName ().get ());
    }

    @Test
    @DisplayName ("um campo estruturado que nao e query reply cai no generico")
    void unknownStructuredField ()
    {
      byte[] field = { 0x4A, 0x00, 0x00 };

      ReadStructuredFieldCommand command = new ReadStructuredFieldCommand (rsf (field));

      assertTrue (command.toString ().contains ("Unknown SF"), command.toString ());
      // sem query replies nao ha nome de cliente
      assertFalse (command.getClientName ().isPresent ());
    }

    @Test
    @DisplayName ("um RSF vazio nao tem campo nenhum")
    void emptyCommand ()
    {
      byte[] buffer = { AIDCommand.AID_STRUCTURED_FIELD };

      ReadStructuredFieldCommand command = new ReadStructuredFieldCommand (buffer);

      assertEquals ("RSF (0):", command.toString ());
      assertFalse (command.getClientName ().isPresent ());
    }

    @Test
    @DisplayName ("um AID diferente viola a assercao")
    void rejectsWrongAid ()
    {
      byte[] buffer = { 0x7D, 0x00, 0x03, (byte) 0x81 };

      assertThrows (AssertionError.class,
                    () -> new ReadStructuredFieldCommand (buffer));
    }

    @Test
    @DisplayName ("le a partir de um deslocamento dentro de um buffer maior")
    void readsFromOffset ()
    {
      byte[] inner =
          rsf (queryReply (QueryReplyField.COLOR_QUERY_REPLY, 0x00, 0x00));
      byte[] buffer = new byte[inner.length + 3];
      System.arraycopy (inner, 0, buffer, 3, inner.length);

      ReadStructuredFieldCommand command =
          new ReadStructuredFieldCommand (buffer, 3, inner.length);

      assertTrue (command.toString ().contains ("Color"), command.toString ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("toHex")
  class Hex
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("converte cada byte em dois digitos maiusculos")
    void formatsBytes ()
    {
      assertEquals ("00010A0FFF7F",
                    ReadStructuredFieldCommand.toHex (new byte[] { 0x00, 0x01, 0x0A,
                                                                   0x0F, (byte) 0xFF,
                                                                   0x7F }));
    }

    @Test
    @DisplayName ("um array vazio devolve string vazia")
    void handlesEmptyArray ()
    {
      assertEquals ("", ReadStructuredFieldCommand.toHex (new byte[0]));
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
