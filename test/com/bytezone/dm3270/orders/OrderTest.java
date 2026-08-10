package com.bytezone.dm3270.orders;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.bytezone.dm3270.attributes.StartFieldAttribute;

// -----------------------------------------------------------------------------------//
@DisplayName ("Order - parsing das orders do data stream 3270")
class OrderTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("fabrica Order.getOrder()")
  class Factory
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "{1} -> {2}")
    @CsvSource ({ //
        "0x1D, START_FIELD, StartFieldOrder", //
        "0x11, SET_BUFFER_ADDRESS, SetBufferAddressOrder", //
        "0x13, INSERT_CURSOR, InsertCursorOrder", //
        "0x05, PROGRAM_TAB, ProgramTabOrder", //
        "0x12, ERASE_UNPROTECTED, EraseUnprotectedToAddressOrder" })
    @DisplayName ("reconhece as orders de controle de buffer")
    void recognisesBufferControlOrders (String hex, String name, String expectedClass)
    {
      byte code = (byte) Integer.decode (hex).intValue ();
      byte[] buffer = { code, 0x40, 0x40, 0x40, 0x40, 0x40 };

      Order order = Order.getOrder (buffer, 0, buffer.length);

      assertEquals (expectedClass, order.getClass ().getSimpleName ());
      assertEquals (code, order.getType ());
    }

    @ParameterizedTest (name = "FCO {0}")
    @ValueSource (ints = { 0x00, 0x3F, 0x1C, 0x1E, 0x0C, 0x0D, 0x15, 0x19, 0xFF })
    @DisplayName ("reconhece as format control orders")
    void recognisesFormatControlOrders (int code)
    {
      byte[] buffer = { (byte) code, 0x40 };

      Order order = Order.getOrder (buffer, 0, buffer.length);

      assertInstanceOf (FormatControlOrder.class, order);
      assertEquals (1, order.size ());
    }

    @Test
    @DisplayName ("qualquer outro byte e tratado como texto")
    void unknownBytesBecomeText ()
    {
      byte[] buffer = "ABC".getBytes (java.nio.charset.Charset.forName ("CP1047"));

      Order order = Order.getOrder (buffer, 0, buffer.length);

      assertInstanceOf (TextOrder.class, order);
      assertTrue (order.isText ());
    }

    @Test
    @DisplayName ("orders recem-criadas nao vem rejeitadas")
    void ordersAreNotRejected ()
    {
      byte[] buffer = { Order.SET_BUFFER_ADDRESS, 0x40, 0x40 };
      assertFalse (Order.getOrder (buffer, 0, buffer.length).rejected ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("SetBufferAddressOrder")
  class SetBufferAddress
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("consome 3 bytes e extrai o endereco")
    void parsesThreeBytes ()
    {
      byte[] buffer = { Order.SET_BUFFER_ADDRESS, (byte) 0x5D, (byte) 0x7F, 0x40 };

      SetBufferAddressOrder order = new SetBufferAddressOrder (buffer, 0);

      assertEquals (3, order.size ());
      assertEquals (new BufferAddress ((byte) 0x5D, (byte) 0x7F).getLocation (),
          order.getBufferAddress ().getLocation ());
    }

    @Test
    @DisplayName ("construida a partir de uma posicao gera o buffer correto")
    void buildsFromLocation ()
    {
      SetBufferAddressOrder order = new SetBufferAddressOrder (0);

      assertArrayEquals (new byte[] { Order.SET_BUFFER_ADDRESS, 0x40, 0x40 },
          order.getBuffer ());
      assertEquals (0, order.getBufferAddress ().getLocation ());
    }

    @Test
    @DisplayName ("pack copia o buffer para o offset pedido")
    void packsIntoBuffer ()
    {
      SetBufferAddressOrder order = new SetBufferAddressOrder (80);
      byte[] target = new byte[5];

      int next = order.pack (target, 1);

      assertEquals (4, next);
      assertEquals (Order.SET_BUFFER_ADDRESS, target[1]);
      assertEquals (0, target[0]);
      assertEquals (0, target[4]);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("StartFieldOrder")
  class StartField
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("consome 2 bytes")
    void parsesTwoBytes ()
    {
      byte[] buffer = { Order.START_FIELD, (byte) 0x60, 0x40 };

      StartFieldOrder order = new StartFieldOrder (buffer, 0);

      assertEquals (2, order.size ());
      assertArrayEquals (new byte[] { Order.START_FIELD, (byte) 0x60 },
          order.getBuffer ());
    }

    @Test
    @DisplayName ("construida a partir de um atributo preserva o valor")
    void buildsFromAttribute ()
    {
      StartFieldAttribute attribute = new StartFieldAttribute ((byte) 0x60);

      StartFieldOrder order = new StartFieldOrder (attribute);

      assertArrayEquals (new byte[] { Order.START_FIELD, (byte) 0x60 },
          order.getBuffer ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("TextOrder")
  class Text
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("consome tudo ate encontrar a proxima order")
    void stopsAtNextOrder ()
    {
      // 'AB' seguido de SBA
      byte[] buffer =
          { (byte) 0xC1, (byte) 0xC2, Order.SET_BUFFER_ADDRESS, 0x40, 0x40 };

      TextOrder order = new TextOrder (buffer, 0, buffer.length);

      assertEquals (2, order.size ());
      assertEquals ("AB", order.getTextString ());
    }

    @Test
    @DisplayName ("consome ate o fim quando nao ha outra order")
    void consumesToEnd ()
    {
      byte[] buffer = { (byte) 0xC1, (byte) 0xC2, (byte) 0xC3 };

      TextOrder order = new TextOrder (buffer, 0, buffer.length);

      assertEquals (3, order.size ());
      assertEquals ("ABC", order.getTextString ());
    }

    @Test
    @DisplayName ("respeita o limite max mesmo com o buffer maior")
    void respectsMax ()
    {
      byte[] buffer = { (byte) 0xC1, (byte) 0xC2, (byte) 0xC3, (byte) 0xC4 };

      TextOrder order = new TextOrder (buffer, 0, 2);

      assertEquals (2, order.size ());
      assertEquals ("AB", order.getTextString ());
    }

    @Test
    @DisplayName ("sempre consome pelo menos um byte, mesmo comecando numa order")
    void alwaysConsumesOneByte ()
    {
      // getDataLength comeca a testar a partir de offset+1, entao o primeiro byte
      // e sempre absorvido - e isso que garante o avanco do ponteiro no parser
      byte[] buffer = { Order.SET_BUFFER_ADDRESS, Order.SET_BUFFER_ADDRESS };

      TextOrder order = new TextOrder (buffer, 0, buffer.length);

      assertEquals (1, order.size ());
    }

    @Test
    @DisplayName ("construida a partir de String codifica em EBCDIC")
    void buildsFromString ()
    {
      TextOrder order = new TextOrder ("LOGON");

      assertArrayEquals (
          "LOGON".getBytes (java.nio.charset.Charset.forName ("CP1047")),
          order.getBuffer ());
    }

    @Test
    @DisplayName ("scramble sobrescreve o buffer original com 0x7B")
    void scrambleOverwritesOriginal ()
    {
      byte[] original = { 0x40, (byte) 0xC1, (byte) 0xC2, Order.SET_BUFFER_ADDRESS };

      TextOrder order = new TextOrder (original, 1, original.length);
      order.scramble ();

      assertEquals (0x40, original[0], "byte antes do offset nao pode mudar");
      assertEquals (0x7B, original[1]);
      assertEquals (0x7B, original[2]);
      assertEquals (Order.SET_BUFFER_ADDRESS, original[3], "a proxima order fica intacta");
    }

    @Test
    @DisplayName ("toString devolve vazio para buffer sem conteudo")
    void emptyToString ()
    {
      assertEquals ("", new TextOrder ("").toString ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("RepeatToAddressOrder")
  class RepeatToAddress
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("consome 4 bytes no caso normal")
    void parsesFourBytes ()
    {
      byte[] buffer = { Order.REPEAT_TO_ADDRESS, 0x40, 0x40, (byte) 0xC1 };

      RepeatToAddressOrder order = new RepeatToAddressOrder (buffer, 0);

      assertEquals (4, order.size ());
      assertTrue (order.toString ().endsWith ("[A]"), order.toString ());
    }

    @Test
    @DisplayName ("consome 6 bytes quando ha graphics escape")
    void parsesSixBytesWithGraphicsEscape ()
    {
      byte[] buffer = { Order.REPEAT_TO_ADDRESS, 0x40, 0x40, Order.GRAPHICS_ESCAPE,
                        (byte) 0xC1, 0x00 };

      RepeatToAddressOrder order = new RepeatToAddressOrder (buffer, 0);

      assertEquals (6, order.size ());
    }

    @Test
    @DisplayName ("caractere nulo e exibido como espaco")
    void nullRepeatCharacterShownAsSpace ()
    {
      byte[] buffer = { Order.REPEAT_TO_ADDRESS, 0x40, 0x40, 0x00 };

      RepeatToAddressOrder order = new RepeatToAddressOrder (buffer, 0);

      assertTrue (order.toString ().endsWith ("[ ]"), order.toString ());
    }

    @ParameterizedTest (name = "byte {0} valido")
    @ValueSource (ints = { 0x00, 0x40, 0x41, 0xC1 })
    @DisplayName ("isValid aceita nulo e bytes imprimiveis")
    void acceptsPrintable (int value)
    {
      assertTrue (RepeatToAddressOrder.isValid ((byte) value));
    }

    @ParameterizedTest (name = "byte {0} invalido")
    @ValueSource (ints = { 0x01, 0x11, 0x3F })
    @DisplayName ("isValid rejeita bytes de controle")
    void rejectsControlBytes (int value)
    {
      assertFalse (RepeatToAddressOrder.isValid ((byte) value));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("FormatControlOrder")
  class FormatControl
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("agrupa orders iguais consecutivas")
    void matchesIdenticalPreviousOrder ()
    {
      byte[] buffer = { Order.FCO_NULL };

      FormatControlOrder first = new FormatControlOrder (buffer, 0);
      FormatControlOrder second = new FormatControlOrder (buffer, 0);

      assertTrue (second.matchesPreviousOrder (first));
    }

    @Test
    @DisplayName ("nao agrupa orders de tipos diferentes")
    void doesNotMatchDifferentOrder ()
    {
      FormatControlOrder nullOrder =
          new FormatControlOrder (new byte[] { Order.FCO_NULL }, 0);
      FormatControlOrder newlineOrder =
          new FormatControlOrder (new byte[] { Order.FCO_NEWLINE }, 0);

      assertFalse (newlineOrder.matchesPreviousOrder (nullOrder));
    }

    @Test
    @DisplayName ("mostra a contagem de duplicatas no toString")
    void showsDuplicateCount ()
    {
      FormatControlOrder order =
          new FormatControlOrder (new byte[] { Order.FCO_NULL }, 0);

      assertFalse (order.toString ().contains ("x "), order.toString ());

      order.incrementDuplicates ();
      order.incrementDuplicates ();

      assertTrue (order.toString ().contains ("x 3"), order.toString ());
    }
  }
}
