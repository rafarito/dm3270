package com.bytezone.dm3270.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.bytezone.dm3270.orders.Order;

// -----------------------------------------------------------------------------------//
@DisplayName ("AIDCommand - resposta do terminal ao host")
class AIDCommandTest
// -----------------------------------------------------------------------------------//
{
  private static final Charset CP1047 = Charset.forName ("CP1047");

  // AID + cursor(0x5D 0x7F = posicao 1919) + SBA(posicao 0) + texto "AB"
  private static final byte[] ENTER_WITH_FIELD =
      { AIDCommand.AID_ENTER, (byte) 0x5D, (byte) 0x7F, //
        Order.SET_BUFFER_ADDRESS, 0x40, 0x40, //
        (byte) 0xC1, (byte) 0xC2 };

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("identificacao da tecla")
  class KeyIdentification
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "{0} -> {1}")
    @CsvSource ({ "0x7D, ENTR", "0xF1, PF1", "0xF9, PF9", "0x7A, PF10", "0x7C, PF12",
                  "0xC1, PF13", "0x4C, PF24", "0x6C, PA1", "0x6E, PA2", "0x6B, PA3",
                  "0x6D, CLR", "0x60, No AID" })
    @DisplayName ("nomeia todas as teclas AID conhecidas")
    void namesKnownKeys (String hex, String expectedName)
    {
      byte key = (byte) Integer.decode (hex).intValue ();
      byte[] buffer = { key, 0x40, 0x40 };

      AIDCommand command = new AIDCommand (buffer, 0, buffer.length);

      assertEquals (expectedName, command.getKeyName ());
      assertEquals ("AID : " + expectedName, command.getName ());
      assertEquals (key, command.getKeyCommand ());
    }

    @Test
    @DisplayName ("tecla desconhecida cai em 'Not found' em vez de estourar o indice")
    void unknownKey ()
    {
      byte[] buffer = { 0x77, 0x40, 0x40 };

      assertEquals ("Not found", new AIDCommand (buffer, 0, buffer.length).getKeyName ());
    }

    @ParameterizedTest (name = "getKey({0}) = {1}")
    @CsvSource ({ "ENTR, 0x7D", "PF1, 0xF1", "PF24, 0x4C", "PA1, 0x6C" })
    @DisplayName ("getKey faz o caminho inverso, do nome para o codigo")
    void mapsNameToKey (String name, String hex)
    {
      assertEquals ((byte) Integer.decode (hex).intValue (), AIDCommand.getKey (name));
    }

    @Test
    @DisplayName ("getKey devolve -1 para um nome inexistente")
    void unknownNameReturnsMinusOne ()
    {
      assertEquals ((byte) -1, AIDCommand.getKey ("PF99"));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("posicao do cursor")
  class CursorAddress
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le o endereco do cursor dos bytes 1 e 2")
    void readsCursorAddress ()
    {
      AIDCommand command =
          new AIDCommand (ENTER_WITH_FIELD, 0, ENTER_WITH_FIELD.length);

      assertNotNull (command.getBufferAddress ());
      assertEquals (1919, command.getBufferAddress ().getLocation ());
    }

    @Test
    @DisplayName ("resposta curta (so o AID) nao tem endereco de cursor")
    void shortReplyHasNoCursor ()
    {
      byte[] buffer = { AIDCommand.AID_PA1 };

      AIDCommand command = new AIDCommand (buffer, 0, buffer.length);

      assertNull (command.getBufferAddress ());
      assertEquals (0, command.countOrders ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("isPAKey")
  class PaKeys
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "{0} e uma PA key")
    @CsvSource ({ "0x6C", "0x6E", "0x6B" })
    @DisplayName ("PA1/PA2/PA3 numa resposta curta sao PA keys")
    void shortPaKeys (String hex)
    {
      byte[] buffer = { (byte) Integer.decode (hex).intValue () };

      assertTrue (new AIDCommand (buffer, 0, 1).isPAKey ());
    }

    @Test
    @DisplayName ("PA key com dados extras nao conta (veio de um ReadModifiedAll)")
    void paKeyWithDataIsNotShort ()
    {
      byte[] buffer = { AIDCommand.AID_PA1, 0x40, 0x40 };

      assertFalse (new AIDCommand (buffer, 0, buffer.length).isPAKey ());
    }

    @Test
    @DisplayName ("ENTER nunca e uma PA key")
    void enterIsNotPaKey ()
    {
      assertFalse (new AIDCommand (new byte[] { AIDCommand.AID_ENTER }, 0, 1).isPAKey ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("parsing das orders")
  class Orders
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("separa orders de controle e orders de texto")
    void splitsOrders ()
    {
      AIDCommand command =
          new AIDCommand (ENTER_WITH_FIELD, 0, ENTER_WITH_FIELD.length);

      assertEquals (2, command.countOrders (), "SBA + texto");
      assertEquals (1, command.countTextOrders ());
      assertArrayEquals ("AB".getBytes (CP1047), command.getText (0));
    }

    @Test
    @DisplayName ("le varios campos modificados numa unica resposta")
    void readsSeveralModifiedFields ()
    {
      byte[] buffer = { AIDCommand.AID_ENTER, 0x40, 0x40, //
                        Order.SET_BUFFER_ADDRESS, 0x40, 0x40, (byte) 0xC1, //
                        Order.SET_BUFFER_ADDRESS, 0x40, 0x50, (byte) 0xC2 };

      AIDCommand command = new AIDCommand (buffer, 0, buffer.length);

      assertEquals (4, command.countOrders ());
      assertEquals (2, command.countTextOrders ());
      assertArrayEquals ("A".getBytes (CP1047), command.getText (0));
      assertArrayEquals ("B".getBytes (CP1047), command.getText (1));
    }

    @Test
    @DisplayName ("iterar sobre o comando percorre as orders na ordem")
    void iteratesOrders ()
    {
      AIDCommand command =
          new AIDCommand (ENTER_WITH_FIELD, 0, ENTER_WITH_FIELD.length);

      int count = 0;
      for (Order order : command)
      {
        assertNotNull (order);
        count++;
      }

      assertEquals (2, count);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("matches - comparacao entre duas respostas")
  class Matches
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("respostas identicas casam")
    void identicalCommandsMatch ()
    {
      AIDCommand first = new AIDCommand (ENTER_WITH_FIELD, 0, ENTER_WITH_FIELD.length);
      AIDCommand second = new AIDCommand (ENTER_WITH_FIELD, 0, ENTER_WITH_FIELD.length);

      assertTrue (first.matches (second));
    }

    @Test
    @DisplayName ("a posicao do cursor e ignorada na comparacao")
    void ignoresCursorPosition ()
    {
      byte[] other = ENTER_WITH_FIELD.clone ();
      other[1] = 0x40;
      other[2] = 0x40;

      AIDCommand first = new AIDCommand (ENTER_WITH_FIELD, 0, ENTER_WITH_FIELD.length);
      AIDCommand second = new AIDCommand (other, 0, other.length);

      assertTrue (first.matches (second));
    }

    @Test
    @DisplayName ("dados diferentes nao casam")
    void differentDataDoesNotMatch ()
    {
      byte[] other = ENTER_WITH_FIELD.clone ();
      other[7] = (byte) 0xC3;

      AIDCommand first = new AIDCommand (ENTER_WITH_FIELD, 0, ENTER_WITH_FIELD.length);
      AIDCommand second = new AIDCommand (other, 0, other.length);

      assertFalse (first.matches (second));
    }

    @Test
    @DisplayName ("tamanhos diferentes nao casam")
    void differentLengthDoesNotMatch ()
    {
      AIDCommand first = new AIDCommand (ENTER_WITH_FIELD, 0, ENTER_WITH_FIELD.length);
      AIDCommand second = new AIDCommand (ENTER_WITH_FIELD, 0, 7);

      assertFalse (first.matches (second));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("scramble - mascara os dados digitados nos logs")
  class Scramble
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("substitui por 0x7B o texto dos campos modificados no buffer do comando")
    void masksModifiedFieldText ()
    {
      AIDCommand command =
          new AIDCommand (ENTER_WITH_FIELD, 0, ENTER_WITH_FIELD.length);

      command.scramble ();

      byte[] data = command.getData ();
      assertEquals (0x7B, data[6]);
      assertEquals (0x7B, data[7]);
    }

    @Test
    @DisplayName ("preserva o AID, o cursor e as orders de controle")
    void keepsControlBytes ()
    {
      AIDCommand command =
          new AIDCommand (ENTER_WITH_FIELD, 0, ENTER_WITH_FIELD.length);

      command.scramble ();

      byte[] data = command.getData ();
      assertArrayEquals (new byte[] { AIDCommand.AID_ENTER, (byte) 0x5D, (byte) 0x7F,
                                      Order.SET_BUFFER_ADDRESS, 0x40, 0x40 },
          java.util.Arrays.copyOf (data, 6));
    }

    @Test
    @DisplayName ("nao altera o buffer recebido pelo chamador")
    void doesNotTouchCallerBuffer ()
    {
      byte[] source = ENTER_WITH_FIELD.clone ();

      new AIDCommand (source, 0, source.length).scramble ();

      assertArrayEquals (ENTER_WITH_FIELD, source);
    }
  }
}
