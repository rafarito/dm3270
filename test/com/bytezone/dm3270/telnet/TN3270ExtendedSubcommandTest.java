package com.bytezone.dm3270.telnet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.InvalidParameterException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.bytezone.dm3270.buffers.Buffer;
import com.bytezone.dm3270.streams.TelnetState;
import com.bytezone.dm3270.telnet.TN3270ExtendedSubcommand.Function;
import com.bytezone.dm3270.telnet.TN3270ExtendedSubcommand.SubType;
import com.bytezone.dm3270.telnet.TelnetSubcommand.SubcommandType;

// -----------------------------------------------------------------------------------//
@DisplayName ("TN3270ExtendedSubcommand - negociacao do subprotocolo TN3270E")
class TN3270ExtendedSubcommandTest
// -----------------------------------------------------------------------------------//
{
  private static final int IAC = 0xFF;
  private static final int SB = 0xFA;
  private static final int SE = 0xF0;
  private static final int TN3270E = 0x28;

  private static final int DEVICE_TYPE = 2;
  private static final int FUNCTIONS = 3;
  private static final int IS = 4;
  private static final int REQUEST = 7;
  private static final int SEND = 8;

  private static final int CONNECT = 1;         // separa o device type do nome da LU

  // nenhum ramo de process() usa a tela
  private static final com.bytezone.dm3270.display.Screen NO_SCREEN = null;

  private TelnetState telnetState;

  @BeforeEach
  void setUp ()
  {
    telnetState = new TelnetState ();
  }

  // ---------------------------------------------------------------------------------//
  //  Construcao dos buffers
  // ---------------------------------------------------------------------------------//

  // Monta IAC SB TN3270E <conteudo> IAC SE. As entradas String entram em ASCII.
  private TN3270ExtendedSubcommand subcommand (Object... content)
  {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream ();
    out.write (IAC);
    out.write (SB);
    out.write (TN3270E);

    for (Object item : content)
      if (item instanceof String text)
        for (char c : text.toCharArray ())
          out.write (c);
      else
        out.write ((Integer) item);

    out.write (IAC);
    out.write (SE);

    byte[] buffer = out.toByteArray ();

    return new TN3270ExtendedSubcommand (buffer, 0, buffer.length, telnetState);
  }

  private static byte[] replyOf (TN3270ExtendedSubcommand subcommand)
  {
    Buffer buffer = subcommand.getReply ()
        .orElseThrow ( () -> new AssertionError ("nenhuma resposta foi gerada"));

    return buffer.getData ();
  }

  private static String ascii (byte[] buffer, int offset, int length)
  {
    return new String (buffer, offset, length, java.nio.charset.StandardCharsets.US_ASCII);
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("SEND DEVICE-TYPE")
  class SendDeviceType
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("o host pergunta qual e o nosso terminal")
    void parsesRequest ()
    {
      TN3270ExtendedSubcommand sub = subcommand (SEND, DEVICE_TYPE);

      assertEquals (SubcommandType.SEND, sub.getSubcommandType ());
      assertEquals (SubType.DEVICE_TYPE, sub.getSubtype ());
      assertEquals ("SEND DEVICE_TYPE", sub.toString ());
      assertEquals ("SEND DEVICE_TYPE", sub.getName ());
    }

    @Test
    @DisplayName ("a resposta declara o modelo configurado")
    void repliesWithConfiguredModel ()
    {
      TN3270ExtendedSubcommand sub = subcommand (SEND, DEVICE_TYPE);

      sub.process (NO_SCREEN);

      byte[] reply = replyOf (sub);

      assertEquals (IAC, reply[0] & 0xFF);
      assertEquals (SB, reply[1] & 0xFF);
      assertEquals (TN3270E, reply[2] & 0xFF);
      assertEquals (DEVICE_TYPE, reply[3]);
      assertEquals (REQUEST, reply[4]);
      assertEquals ("IBM-3278-2-E", ascii (reply, 5, reply.length - 7));
      assertEquals (IAC, reply[reply.length - 2] & 0xFF);
      assertEquals (SE, reply[reply.length - 1] & 0xFF);
    }

    @Test
    @DisplayName ("a resposta segue o modelo escolhido nas preferencias")
    void followsPreference ()
    {
      telnetState.setDoDeviceType (5);
      TN3270ExtendedSubcommand sub = subcommand (SEND, DEVICE_TYPE);

      sub.process (NO_SCREEN);

      byte[] reply = replyOf (sub);

      assertEquals ("IBM-3278-5-E", ascii (reply, 5, reply.length - 7));
    }

    @Test
    @DisplayName ("a resposta e um subcomando valido, pronto para reenvio")
    void replyIsParsable ()
    {
      TN3270ExtendedSubcommand sub = subcommand (SEND, DEVICE_TYPE);

      sub.process (NO_SCREEN);

      Buffer reply = sub.getReply ().orElseThrow ();

      assertTrue (reply instanceof TN3270ExtendedSubcommand);
      TN3270ExtendedSubcommand parsed = (TN3270ExtendedSubcommand) reply;
      assertEquals (SubcommandType.DEVICE_TYPE, parsed.getSubcommandType ());
      assertEquals (SubType.REQUEST, parsed.getSubtype ());
      assertEquals ("IBM-3278-2-E", parsed.getValue ());
    }

    @Test
    @DisplayName ("um SEND de outro tipo e ignorado em vez de quebrar")
    void sendOfUnknownTypeIsIgnored ()
    {
      // o construtor so reconhece SEND DEVICE-TYPE; qualquer outro deixa subType null,
      // e process () sai sem fazer nada em vez de estourar no switch
      TN3270ExtendedSubcommand sub = subcommand (SEND, FUNCTIONS);

      assertEquals (SubcommandType.SEND, sub.getSubcommandType ());
      assertNull (sub.getSubtype ());
      assertEquals ("SEND null", sub.toString ());

      sub.process (NO_SCREEN);

      assertFalse (sub.getReply ().isPresent ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("DEVICE-TYPE REQUEST")
  class DeviceTypeRequest
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le o terminal pedido pelo cliente")
    void parsesTerminal ()
    {
      TN3270ExtendedSubcommand sub = subcommand (DEVICE_TYPE, REQUEST, "IBM-3278-2-E");

      assertEquals (SubcommandType.DEVICE_TYPE, sub.getSubcommandType ());
      assertEquals (SubType.REQUEST, sub.getSubtype ());
      assertEquals ("IBM-3278-2-E", sub.getValue ());
      assertEquals ("DEVICE_TYPE REQUEST IBM-3278-2-E", sub.toString ());
    }

    @Test
    @DisplayName ("um pedido de device type nao gera resposta")
    void doesNotReply ()
    {
      TN3270ExtendedSubcommand sub = subcommand (DEVICE_TYPE, REQUEST, "IBM-3278-3-E");

      sub.process (NO_SCREEN);

      assertFalse (sub.getReply ().isPresent ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("DEVICE-TYPE IS")
  class DeviceTypeIs
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le o terminal e a LU atribuidos pelo host")
    void parsesTerminalAndLu ()
    {
      TN3270ExtendedSubcommand sub =
          subcommand (DEVICE_TYPE, IS, "IBM-3278-2-E", CONNECT, "LU0001");

      assertEquals (SubcommandType.DEVICE_TYPE, sub.getSubcommandType ());
      assertEquals (SubType.IS, sub.getSubtype ());
      assertEquals ("IBM-3278-2-E", sub.getValue ());
      assertEquals ("DEVICE_TYPE IS IBM-3278-2-E (LU0001)", sub.toString ());
    }

    @Test
    @DisplayName ("o device type informado define a tela secundaria")
    void updatesSecondaryScreen ()
    {
      TN3270ExtendedSubcommand sub =
          subcommand (DEVICE_TYPE, IS, "IBM-3278-4-E", CONNECT, "LU0001");

      sub.process (NO_SCREEN);

      assertEquals (43, telnetState.getSecondary ().rows);
      assertEquals (80, telnetState.getSecondary ().columns);
    }

    @Test
    @DisplayName ("aceito o device type, pedimos as tres funcoes")
    void requestsFunctions ()
    {
      TN3270ExtendedSubcommand sub =
          subcommand (DEVICE_TYPE, IS, "IBM-3278-2-E", CONNECT, "LU0001");

      sub.process (NO_SCREEN);

      assertArrayEquals (new byte[] { (byte) IAC, (byte) SB, (byte) TN3270E,
                                      (byte) FUNCTIONS, (byte) REQUEST, 0x00, 0x02, 0x04,
                                      (byte) IAC, (byte) SE },
                         replyOf (sub));
    }

    @Test
    @DisplayName ("sem o byte CONNECT o valor para antes do IAC SE")
    void valueStopsBeforeTheTrailer ()
    {
      // o laco procura o separador 0x01 e, quando nao encontra, o fallback desconta os
      // dois bytes de fechamento do subcomando
      TN3270ExtendedSubcommand sub = subcommand (DEVICE_TYPE, IS, "IBM-3278-2-E");

      assertEquals ("IBM-3278-2-E", sub.getValue ());
    }

    @Test
    @DisplayName ("um device type sem CONNECT tambem define a tela secundaria")
    void deviceTypeWithoutConnectStillSetsTheScreen ()
    {
      // agora que o valor sai limpo, o modelo 4 e reconhecido mesmo sem o byte CONNECT
      TN3270ExtendedSubcommand sub = subcommand (DEVICE_TYPE, IS, "IBM-3278-4-E");

      sub.process (NO_SCREEN);

      assertEquals (43, telnetState.getSecondary ().rows);
      assertEquals (80, telnetState.getSecondary ().columns);
    }

    @Test
    @DisplayName ("uma LU vazia nao e propagada")
    void emptyLuIsNotPropagated ()
    {
      // CONNECT imediatamente antes do IAC SE: length - ptr - 3 = 0
      TN3270ExtendedSubcommand sub =
          subcommand (DEVICE_TYPE, IS, "IBM-3278-2-E", CONNECT);

      sub.process (NO_SCREEN);

      assertEquals ("DEVICE_TYPE IS IBM-3278-2-E", sub.toString ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("FUNCTIONS")
  class Functions
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le as tres funcoes suportadas")
    void parsesAllThree ()
    {
      TN3270ExtendedSubcommand sub = subcommand (FUNCTIONS, IS, 0x00, 0x02, 0x04);

      assertEquals (SubcommandType.FUNCTIONS, sub.getSubcommandType ());
      assertEquals (SubType.IS, sub.getSubtype ());
      assertEquals ("BIND, RESPONSES, SYSREQ", sub.getFunctions ());
      assertTrue (sub.doesFunction (Function.BIND_IMAGE));
      assertTrue (sub.doesFunction (Function.RESPONSES));
      assertTrue (sub.doesFunction (Function.SYSREQ));
    }

    @Test
    @DisplayName ("uma lista parcial nao inclui o que nao foi pedido")
    void parsesSubset ()
    {
      TN3270ExtendedSubcommand sub = subcommand (FUNCTIONS, IS, 0x00);

      assertEquals ("BIND", sub.getFunctions ());
      assertTrue (sub.doesFunction (Function.BIND_IMAGE));
      assertFalse (sub.doesFunction (Function.SYSREQ));
    }

    @Test
    @DisplayName ("uma lista vazia e valida")
    void parsesEmptyList ()
    {
      TN3270ExtendedSubcommand sub = subcommand (FUNCTIONS, IS);

      assertEquals ("", sub.getFunctions ());
      assertFalse (sub.doesFunction (Function.BIND_IMAGE));
      assertEquals ("FUNCTIONS IS : ", sub.toString ());
    }

    @Test
    @DisplayName ("um IS confirma as funcoes no estado da sessao")
    void isUpdatesState ()
    {
      TN3270ExtendedSubcommand sub = subcommand (FUNCTIONS, IS, 0x00, 0x02, 0x04);

      sub.process (NO_SCREEN);

      assertTrue (telnetState.toString ().contains ("BIND_IMAGE"), "funcoes nao gravadas");
      assertFalse (sub.getReply ().isPresent (), "um IS nao precisa de resposta");
    }

    @Test
    @DisplayName ("uma contraproposta do host e aceita como está")
    void requestIsEchoedBackAsIs ()
    {
      TN3270ExtendedSubcommand sub = subcommand (FUNCTIONS, REQUEST, 0x00, 0x02);

      sub.process (NO_SCREEN);

      // a resposta e o mesmo buffer com REQUEST trocado por IS
      assertArrayEquals (new byte[] { (byte) IAC, (byte) SB, (byte) TN3270E,
                                      (byte) FUNCTIONS, (byte) IS, 0x00, 0x02,
                                      (byte) IAC, (byte) SE },
                         replyOf (sub));
    }

    @Test
    @DisplayName ("a contraproposta aceita chega parseada como IS")
    void echoIsParsedAsIs ()
    {
      TN3270ExtendedSubcommand sub = subcommand (FUNCTIONS, REQUEST, 0x00, 0x04);

      sub.process (NO_SCREEN);

      TN3270ExtendedSubcommand reply =
          (TN3270ExtendedSubcommand) sub.getReply ().orElseThrow ();

      assertEquals (SubType.IS, reply.getSubtype ());
      assertEquals ("BIND, SYSREQ", reply.getFunctions ());
    }

    @ParameterizedTest (name = "funcao {0}")
    @ValueSource (ints = { 0x01, 0x03, 0x05, 0x7F })
    @DisplayName ("uma funcao desconhecida e recusada")
    void rejectsUnknownFunction (int function)
    {
      InvalidParameterException e =
          assertThrows (InvalidParameterException.class,
                        () -> subcommand (FUNCTIONS, IS, function));

      assertTrue (e.getMessage ().startsWith ("Unknown function"), e.getMessage ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("subcomandos invalidos")
  class Invalid
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "subcomando {0}")
    @ValueSource (ints = { 0x00, 0x01, 0x05, 0x06, 0x09 })
    @DisplayName ("um subcomando fora dos tres conhecidos e recusado")
    void rejectsUnknownSubcommand (int subcommandType)
    {
      InvalidParameterException e =
          assertThrows (InvalidParameterException.class,
                        () -> subcommand (subcommandType, IS));

      assertTrue (e.getMessage ().startsWith ("Unknown Extended"), e.getMessage ());
    }

    @Test
    @DisplayName ("doesFunction devolve falso quando nao houve lista de funcoes")
    void doesFunctionWithoutList ()
    {
      // functions so e criado nos subcomandos FUNCTIONS
      TN3270ExtendedSubcommand sub = subcommand (SEND, DEVICE_TYPE);

      assertFalse (sub.doesFunction (Function.BIND_IMAGE));
      assertFalse (sub.doesFunction (Function.RESPONSES));
      assertFalse (sub.doesFunction (Function.SYSREQ));
    }

    @Test
    @DisplayName ("um DEVICE-TYPE de subtipo desconhecido e ignorado em process")
    void deviceTypeWithUnknownSubtype ()
    {
      TN3270ExtendedSubcommand sub = subcommand (DEVICE_TYPE, 0x09, "IBM-3278-2-E");

      assertNull (sub.getSubtype ());

      sub.process (NO_SCREEN);

      assertFalse (sub.getReply ().isPresent ());
      // a tela secundaria segue no default, porque nada foi processado
      assertEquals (24, telnetState.getSecondary ().rows);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("serializacao")
  class Serialisation
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("getTelnetData devolve os bytes originais")
    void telnetDataIsVerbatim ()
    {
      TN3270ExtendedSubcommand sub = subcommand (FUNCTIONS, IS, 0x00, 0x02, 0x04);

      assertArrayEquals (new byte[] { (byte) IAC, (byte) SB, (byte) TN3270E,
                                      (byte) FUNCTIONS, (byte) IS, 0x00, 0x02, 0x04,
                                      (byte) IAC, (byte) SE },
                         sub.getTelnetData ());
    }

    @Test
    @DisplayName ("size conta o buffer inteiro")
    void sizeCountsEverything ()
    {
      assertEquals (10, subcommand (FUNCTIONS, IS, 0x00, 0x02, 0x04).size ());
      assertEquals (7, subcommand (SEND, DEVICE_TYPE).size ());
    }
  }
}
