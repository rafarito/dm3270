package com.bytezone.dm3270.orders;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.bytezone.dm3270.attributes.Attribute;
import com.bytezone.dm3270.attributes.StartFieldAttribute;

// -----------------------------------------------------------------------------------//
@DisplayName ("Orders estendidas - SFE, MF e GE")
class ExtendedOrderTest
// -----------------------------------------------------------------------------------//
{
  private static final byte XA_START_FIELD = (byte) 0xC0;
  private static final byte XA_HIGHLIGHTING = 0x41;
  private static final byte XA_FGCOLOR = 0x42;
  private static final byte XA_BGCOLOR = 0x45;

  private static byte[] bytes (int... values)
  {
    byte[] buffer = new byte[values.length];
    for (int i = 0; i < values.length; i++)
      buffer[i] = (byte) values[i];

    return buffer;
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("StartFieldExtendedOrder")
  class StartFieldExtended
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le um unico par: o atributo de inicio de campo")
    void readsStartFieldOnly ()
    {
      byte[] buffer = bytes (Order.START_FIELD_EXTENDED, 0x01, 0xC0, 0xF0);

      StartFieldExtendedOrder order = new StartFieldExtendedOrder (buffer, 0);

      assertArrayEquals (buffer, order.getBuffer ());
      assertTrue (order.toString ().startsWith ("SFE :"), order.toString ());
    }

    @Test
    @DisplayName ("le os atributos estendidos que acompanham o campo")
    void readsExtendedAttributes ()
    {
      // start field + destaque + cor de frente
      byte[] buffer = bytes (Order.START_FIELD_EXTENDED, 0x03, //
                             0xC0, 0xF0, //
                             XA_HIGHLIGHTING, 0xF1, //
                             XA_FGCOLOR, 0xF4);

      StartFieldExtendedOrder order = new StartFieldExtendedOrder (buffer, 0);

      String text = order.toString ();

      assertEquals (3, text.split ("\n").length, text);
      assertArrayEquals (buffer, order.getBuffer ());
    }

    @Test
    @DisplayName ("o atributo de inicio de campo pode vir em qualquer posicao")
    void startFieldCanComeLast ()
    {
      byte[] buffer = bytes (Order.START_FIELD_EXTENDED, 0x02, //
                             XA_BGCOLOR, 0xF1, //
                             0xC0, 0xF0);

      StartFieldExtendedOrder order = new StartFieldExtendedOrder (buffer, 0);

      // a cor de fundo entra na lista de atributos, o start field vai para o cabecalho
      assertEquals (2, order.toString ().split ("\n").length, order.toString ());
    }

    @Test
    @DisplayName ("le a order a partir de um deslocamento")
    void readsFromOffset ()
    {
      byte[] buffer = bytes (0xFF, 0xFF, Order.START_FIELD_EXTENDED, 0x01, 0xC0, 0xF0);

      StartFieldExtendedOrder order = new StartFieldExtendedOrder (buffer, 2);

      assertArrayEquals (bytes (Order.START_FIELD_EXTENDED, 0x01, 0xC0, 0xF0),
                         order.getBuffer ());
    }

    @Test
    @DisplayName ("sem atributo de inicio de campo a order e invalida")
    void needsAStartFieldAttribute ()
    {
      byte[] buffer = bytes (Order.START_FIELD_EXTENDED, 0x01, XA_HIGHLIGHTING, 0xF1);

      assertThrows (AssertionError.class,
                    () -> new StartFieldExtendedOrder (buffer, 0));
    }

    @Test
    @DisplayName ("um atributo nao suportado tambem derruba a order")
    void rejectsUnsupportedAttribute ()
    {
      // 0x43 (charset) devolve Optional.empty ()
      byte[] buffer = bytes (Order.START_FIELD_EXTENDED, 0x02, 0xC0, 0xF0, 0x43, 0xF1);

      assertThrows (AssertionError.class,
                    () -> new StartFieldExtendedOrder (buffer, 0));
    }

    @Test
    @DisplayName ("um primeiro byte diferente de 29 viola a assercao")
    void rejectsWrongOrder ()
    {
      byte[] buffer = bytes (0x1D, 0x01, 0xC0, 0xF0);

      assertThrows (AssertionError.class,
                    () -> new StartFieldExtendedOrder (buffer, 0));
    }

    @Test
    @DisplayName ("a order que nos montamos declara o atributo de inicio de campo")
    void buildsFromStartFieldAttribute ()
    {
      StartFieldAttribute attribute = new StartFieldAttribute ((byte) 0xF0);

      StartFieldExtendedOrder order = new StartFieldExtendedOrder (attribute);

      assertEquals (4, order.getBuffer ().length);
      assertEquals (Order.START_FIELD_EXTENDED, order.getBuffer ()[0]);
      assertEquals (0x01, order.getBuffer ()[1]);
    }

    @Test
    @DisplayName ("a order que montamos com atributos extras conta todos os pares")
    void buildsWithExtraAttributes ()
    {
      StartFieldAttribute startField = new StartFieldAttribute ((byte) 0xF0);
      List<Attribute> attributes = new ArrayList<> ();
      attributes.add (Attribute.getAttribute (XA_HIGHLIGHTING, (byte) 0xF1).get ());
      attributes.add (Attribute.getAttribute (XA_FGCOLOR, (byte) 0xF4).get ());

      StartFieldExtendedOrder order =
          new StartFieldExtendedOrder (startField, attributes);

      assertEquals (8, order.getBuffer ().length);       // 2 + 3 pares de 2 bytes
      assertEquals (0x03, order.getBuffer ()[1]);
    }

    @Test
    @DisplayName ("a order que montamos pode ser lida de volta pela fabrica")
    void builtOrderIsParseable ()
    {
      StartFieldAttribute startField = new StartFieldAttribute ((byte) 0xF0);
      List<Attribute> attributes = new ArrayList<> ();
      attributes.add (Attribute.getAttribute (XA_HIGHLIGHTING, (byte) 0xF1).get ());

      byte[] buffer = new StartFieldExtendedOrder (startField, attributes).getBuffer ();

      Order parsed = Order.getOrder (buffer, 0, buffer.length);

      assertTrue (parsed instanceof StartFieldExtendedOrder, parsed.getClass ().getName ());
      assertArrayEquals (buffer, parsed.getBuffer ());
    }

    @Test
    @DisplayName ("antes de ser processada a order nao tem posicao na tela")
    void hasNoLocationBeforeProcessing ()
    {
      byte[] buffer = bytes (Order.START_FIELD_EXTENDED, 0x01, 0xC0, 0xF0);

      // o toString so mostra a posicao depois do process (), que exige uma tela
      assertFalse (new StartFieldExtendedOrder (buffer, 0).toString ().contains ("("),
                   "a posicao nao deveria aparecer");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("ModifyFieldOrder")
  class ModifyField
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le os pares de atributo declarados")
    void readsAttributePairs ()
    {
      byte[] buffer = bytes (Order.MODIFY_FIELD, 0x02, //
                             XA_HIGHLIGHTING, 0xF1, //
                             XA_FGCOLOR, 0xF4);

      ModifyFieldOrder order = new ModifyFieldOrder (buffer, 0);

      assertArrayEquals (buffer, order.getBuffer ());
    }

    @Test
    @DisplayName ("aceita zero pares")
    void readsEmptyOrder ()
    {
      byte[] buffer = bytes (Order.MODIFY_FIELD, 0x00);

      ModifyFieldOrder order = new ModifyFieldOrder (buffer, 0);

      assertEquals (2, order.getBuffer ().length);
    }

    @Test
    @DisplayName ("le a order a partir de um deslocamento")
    void readsFromOffset ()
    {
      byte[] buffer = bytes (0x11, 0x40, Order.MODIFY_FIELD, 0x01, XA_BGCOLOR, 0xF1);

      ModifyFieldOrder order = new ModifyFieldOrder (buffer, 2);

      assertArrayEquals (bytes (Order.MODIFY_FIELD, 0x01, XA_BGCOLOR, 0xF1),
                         order.getBuffer ());
    }

    @Test
    @DisplayName ("um atributo nao suportado derruba a order")
    void rejectsUnsupportedAttribute ()
    {
      byte[] buffer = bytes (Order.MODIFY_FIELD, 0x01, 0x43, 0xF1);

      assertThrows (AssertionError.class, () -> new ModifyFieldOrder (buffer, 0));
    }

    @Test
    @DisplayName ("um primeiro byte diferente de 2C viola a assercao")
    void rejectsWrongOrder ()
    {
      byte[] buffer = bytes (0x1D, 0x01, XA_HIGHLIGHTING, 0xF1);

      assertThrows (AssertionError.class, () -> new ModifyFieldOrder (buffer, 0));
    }

    @Test
    @DisplayName ("a fabrica reconhece a order pelo codigo 2C")
    void factoryRecognisesIt ()
    {
      byte[] buffer = bytes (Order.MODIFY_FIELD, 0x01, XA_HIGHLIGHTING, 0xF1);

      assertTrue (Order.getOrder (buffer, 0, buffer.length) instanceof ModifyFieldOrder);
    }

    @Test
    @DisplayName ("o atributo de inicio de campo tambem e aceito aqui")
    void acceptsStartFieldAttribute ()
    {
      byte[] buffer = bytes (Order.MODIFY_FIELD, 0x01, XA_START_FIELD, 0xF0);

      assertArrayEquals (buffer, new ModifyFieldOrder (buffer, 0).getBuffer ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("GraphicsEscapeOrder")
  class GraphicsEscape
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "codigo {0}")
    @ValueSource (ints = { 0x00, 0x40, 0xC1, 0xFF })
    @DisplayName ("guarda o codigo grafico que veio depois do escape")
    void readsCode (int code)
    {
      byte[] buffer = bytes (Order.GRAPHICS_ESCAPE, code);

      GraphicsEscapeOrder order = new GraphicsEscapeOrder (buffer, 0);

      assertArrayEquals (buffer, order.getBuffer ());
      assertEquals (String.format ("GE  : %02X ", code), order.toString ());
    }

    @Test
    @DisplayName ("le a order a partir de um deslocamento")
    void readsFromOffset ()
    {
      byte[] buffer = bytes (0x11, 0x40, Order.GRAPHICS_ESCAPE, 0xC1);

      GraphicsEscapeOrder order = new GraphicsEscapeOrder (buffer, 2);

      assertArrayEquals (bytes (Order.GRAPHICS_ESCAPE, 0xC1), order.getBuffer ());
    }

    @Test
    @DisplayName ("duas orders com o mesmo codigo se agrupam")
    void matchesSameCode ()
    {
      byte[] buffer = bytes (Order.GRAPHICS_ESCAPE, 0xC1);
      GraphicsEscapeOrder first = new GraphicsEscapeOrder (buffer, 0);
      GraphicsEscapeOrder second = new GraphicsEscapeOrder (buffer, 0);

      assertTrue (second.matchesPreviousOrder (first));
    }

    @Test
    @DisplayName ("codigos diferentes nao se agrupam")
    void doesNotMatchOtherCode ()
    {
      GraphicsEscapeOrder first =
          new GraphicsEscapeOrder (bytes (Order.GRAPHICS_ESCAPE, 0xC1), 0);
      GraphicsEscapeOrder second =
          new GraphicsEscapeOrder (bytes (Order.GRAPHICS_ESCAPE, 0xC2), 0);

      assertFalse (second.matchesPreviousOrder (first));
    }

    @Test
    @DisplayName ("uma order de outro tipo nunca se agrupa")
    void doesNotMatchOtherOrderType ()
    {
      GraphicsEscapeOrder order =
          new GraphicsEscapeOrder (bytes (Order.GRAPHICS_ESCAPE, 0xC1), 0);
      Order other = Order.getOrder (bytes (Order.INSERT_CURSOR), 0, 1);

      assertFalse (order.matchesPreviousOrder (other));
    }

    @Test
    @DisplayName ("o contador de repeticoes aparece no toString")
    void showsDuplicateCount ()
    {
      byte[] buffer = bytes (Order.GRAPHICS_ESCAPE, 0xC1);
      GraphicsEscapeOrder first = new GraphicsEscapeOrder (buffer, 0);
      GraphicsEscapeOrder second = new GraphicsEscapeOrder (buffer, 0);

      first.incrementDuplicates ();
      first.incrementDuplicates ();

      assertEquals ("GE  : C1 x 3", first.toString ());
      assertEquals ("GE  : C1 ", second.toString ());
    }

    @Test
    @DisplayName ("um primeiro byte diferente de 08 viola a assercao")
    void rejectsWrongOrder ()
    {
      byte[] buffer = bytes (0x1D, 0xC1);

      assertThrows (AssertionError.class, () -> new GraphicsEscapeOrder (buffer, 0));
    }

    @Test
    @DisplayName ("a fabrica reconhece a order pelo codigo 08")
    void factoryRecognisesIt ()
    {
      byte[] buffer = bytes (Order.GRAPHICS_ESCAPE, 0xC1);

      assertTrue (
          Order.getOrder (buffer, 0, buffer.length) instanceof GraphicsEscapeOrder);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("SetAttributeOrder")
  class SetAttribute
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "atributo {0}")
    @CsvSource ({ "0x41, 0xF1", "0x42, 0xF4", "0x45, 0xF2", "0x00, 0x00" })
    @DisplayName ("le o par de atributo declarado")
    void readsAttributePair (int code, int value)
    {
      byte[] buffer = bytes (Order.SET_ATTRIBUTE, code, value);

      Order order = Order.getOrder (buffer, 0, buffer.length);

      assertTrue (order instanceof SetAttributeOrder, order.getClass ().getName ());
      assertArrayEquals (buffer, order.getBuffer ());
    }
  }
}
