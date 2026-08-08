package com.bytezone.dm3270.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.bytezone.dm3270.orders.Order;

// -----------------------------------------------------------------------------------//
@DisplayName ("Command - fabrica e parsing dos comandos 3270")
class CommandTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("Command.getCommand() - comandos vindos do host")
  class OutboundFactory
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "{0} -> {1}")
    @CsvSource ({ //
        "0xF1, Write",                        // SNA
        "0x01, Write",                        // CCW
        "0xF5, Erase Write", //
        "0x05, Erase Write", //
        "0x7E, Erase Write Alternate", //
        "0x0D, Erase Write Alternate" })
    @DisplayName ("reconhece os comandos de escrita nas duas codificacoes")
    void recognisesWriteCommands (String hex, String expectedName)
    {
      byte code = (byte) Integer.decode (hex).intValue ();
      byte[] buffer = { code, 0x00 };

      Command command = Command.getCommand (buffer, 0, buffer.length);

      assertInstanceOf (WriteCommand.class, command);
      assertEquals (expectedName, command.getName ());
    }

    @ParameterizedTest (name = "{0} -> {1}")
    @CsvSource ({ "0xF2, Read Buffer", "0x02, Read Buffer", "0xF6, Read Modified",
                  "0x06, Read Modified", "0x6E, Read Modified All",
                  "0x0E, Read Modified All" })
    @DisplayName ("reconhece os comandos de leitura nas duas codificacoes")
    void recognisesReadCommands (String hex, String expectedName)
    {
      byte code = (byte) Integer.decode (hex).intValue ();
      byte[] buffer = { code };

      Command command = Command.getCommand (buffer, 0, buffer.length);

      assertInstanceOf (ReadCommand.class, command);
      assertEquals (expectedName, command.getName ());
    }

    @ParameterizedTest (name = "codigo {0}")
    @ValueSource (ints = { 0x6F, 0x0F })
    @DisplayName ("reconhece erase all unprotected")
    void recognisesEraseAllUnprotected (int code)
    {
      byte[] buffer = { (byte) code };

      Command command = Command.getCommand (buffer, 0, buffer.length);

      assertInstanceOf (EraseAllUnprotectedCommand.class, command);
      assertEquals ("Erase All Unprotected", command.getName ());
    }

    @Test
    @DisplayName ("devolve null e nao lanca excecao para comando desconhecido")
    void unknownCommandReturnsNull ()
    {
      assertNull (Command.getCommand (new byte[] { 0x77 }, 0, 1));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("Command.getReply() - comandos vindos do terminal")
  class InboundFactory
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("0x88 e um structured field, o resto e AID")
    void dispatchesReplies ()
    {
      byte[] structuredField = { AIDCommand.AID_STRUCTURED_FIELD };
      assertInstanceOf (ReadStructuredFieldCommand.class,
          Command.getReply (structuredField, 0, structuredField.length));

      byte[] aid = { AIDCommand.AID_ENTER, 0x40, 0x40 };
      assertInstanceOf (AIDCommand.class, Command.getReply (aid, 0, aid.length));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("WriteCommand")
  class Write
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le o WCC e as orders que vem depois dele")
    void parsesWccAndOrders ()
    {
      byte[] buffer = { Command.ERASE_WRITE_F5, (byte) 0xC3,        // comando + WCC
                        Order.SET_BUFFER_ADDRESS, 0x40, 0x40,       // SBA(0)
                        Order.START_FIELD, (byte) 0x60,             // SF protegido
                        (byte) 0xC1, (byte) 0xC2 };                 // texto "AB"

      WriteCommand command = new WriteCommand (buffer, 0, buffer.length);

      assertEquals (3, command.getOrdersList ().size ());
      assertEquals ("Erase Write", command.getName ());
    }

    @Test
    @DisplayName ("comando sem orders devolve lista vazia")
    void handlesCommandWithoutOrders ()
    {
      byte[] buffer = { Command.WRITE_F1, (byte) 0x02 };

      WriteCommand command = new WriteCommand (buffer, 0, buffer.length);

      assertTrue (command.getOrdersList ().isEmpty ());
      assertEquals ("Write", command.getName ());
    }

    @Test
    @DisplayName ("agrupa orders duplicadas consecutivas em vez de repeti-las")
    void collapsesDuplicateOrders ()
    {
      byte[] buffer = { Command.WRITE_F1, 0x00, Order.FCO_NULL, Order.FCO_NULL,
                        Order.FCO_NULL };

      WriteCommand command = new WriteCommand (buffer, 0, buffer.length);

      assertEquals (1, command.getOrdersList ().size (),
          "as tres FCO iguais viram uma order com duas duplicatas");
    }

    @Test
    @DisplayName ("le a partir do offset indicado, ignorando o prefixo")
    void honoursOffset ()
    {
      byte[] buffer = { 0x7F, 0x7F, Command.WRITE_F1, 0x00, (byte) 0xC1 };

      WriteCommand command = new WriteCommand (buffer, 2, 3);

      assertEquals (1, command.getOrdersList ().size ());
      assertArrayEquals (new byte[] { Command.WRITE_F1, 0x00, (byte) 0xC1 },
          command.getData ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("WriteStructuredFieldCommand")
  class WriteStructuredField
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("separa varios structured fields pelo campo de tamanho")
    void splitsStructuredFields ()
    {
      byte[] buffer = { Command.WRITE_STRUCTURED_FIELD_F3, //
                        0x00, 0x04, 0x03, 0x00,             // erase reset (4 bytes)
                        0x00, 0x05, 0x0B, 0x00, 0x00 };     // set window origin (5 bytes)

      WriteStructuredFieldCommand command =
          new WriteStructuredFieldCommand (buffer, 0, buffer.length);

      assertTrue (command.brief ().startsWith ("WSF (2):"), command.brief ());
      assertEquals ("Write SF", command.getName ());
    }

    @Test
    @DisplayName ("sem structured fields ainda assim se constroi")
    void handlesEmptyCommand ()
    {
      byte[] buffer = { Command.WRITE_STRUCTURED_FIELD_11 };

      WriteStructuredFieldCommand command =
          new WriteStructuredFieldCommand (buffer, 0, buffer.length);

      assertTrue (command.brief ().startsWith ("WSF (0):"));
      assertTrue (command.getReply ().isEmpty ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("brief()")
  class Brief
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("reduz o toString a uma unica linha")
    void reducesToOneLine ()
    {
      byte[] buffer = { Command.WRITE_F1, 0x00, Order.SET_BUFFER_ADDRESS, 0x40, 0x40 };

      Command command = new WriteCommand (buffer, 0, buffer.length);

      assertTrue (command.toString ().contains ("\n"), "o toString completo tem varias linhas");
      assertFalse (command.brief ().contains ("\n"));
      assertEquals ("Write", command.brief ());
    }

    @Test
    @DisplayName ("toString de uma unica linha e devolvido inalterado")
    void singleLineIsUnchanged ()
    {
      Command command = new ReadCommand (new byte[] { Command.READ_BUFFER_F2 }, 0, 1);

      assertEquals ("Read Buffer", command.brief ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("encapsulamento telnet herdado de AbstractBuffer")
  class TelnetEncoding
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("um comando com 0xF1 nao duplica esse byte (so 0xFF e escapado)")
    void doesNotEscapeF1 ()
    {
      Command command = new ReadCommand (new byte[] { Command.READ_MODIFIED_F6 }, 0, 1);

      assertArrayEquals (
          new byte[] { Command.READ_MODIFIED_F6, (byte) 0xFF, (byte) 0xEF },
          command.getTelnetData ());
    }

    @Test
    @DisplayName ("comando novo comeca sem reply")
    void startsWithoutReply ()
    {
      Command command = new ReadCommand (new byte[] { Command.READ_BUFFER_F2 }, 0, 1);

      assertNotNull (command.getReply ());
      assertTrue (command.getReply ().isEmpty ());
    }
  }
}
