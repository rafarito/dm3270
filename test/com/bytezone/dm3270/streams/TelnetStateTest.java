package com.bytezone.dm3270.streams;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.telnet.TN3270ExtendedSubcommand.Function;

// -----------------------------------------------------------------------------------//
@DisplayName ("TelnetState - preferencias e estado negociado da sessao")
class TelnetStateTest
// -----------------------------------------------------------------------------------//
{
  private TelnetState telnetState;

  @BeforeEach
  void setUp ()
  {
    telnetState = new TelnetState ();
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("preferencias iniciais")
  class Defaults
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("o construtor liga todas as preferencias")
    void everythingPreferred ()
    {
      assertTrue (telnetState.do3270Extended ());
      assertTrue (telnetState.doEOR ());
      assertTrue (telnetState.doBinary ());
      assertTrue (telnetState.doTerminalType ());
    }

    @Test
    @DisplayName ("o modelo default e o 3278-2-E")
    void defaultDeviceType ()
    {
      assertEquals ("IBM-3278-2-E", telnetState.doDeviceType ());
    }

    @Test
    @DisplayName ("a tela primaria e 24 x 80 e nao muda")
    void primaryScreen ()
    {
      ScreenDimensions primary = telnetState.getPrimary ();

      assertEquals (24, primary.rows);
      assertEquals (80, primary.columns);

      telnetState.setDeviceType ("IBM-3278-4-E");            // muda a secundaria

      assertSame (primary, telnetState.getPrimary ());
    }

    @Test
    @DisplayName ("nada foi negociado ainda")
    void nothingNegotiated ()
    {
      // os getters de status combinam o valor negociado com does3270Extended, que
      // comeca falso — logo tudo comeca falso
      assertFalse (telnetState.does3270Extended ());
      assertFalse (telnetState.doesEOR ());
      assertFalse (telnetState.doesBinary ());
      assertFalse (telnetState.doesTerminalType ());
      assertEquals ("", telnetState.getTerminal ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("preferencias mutaveis")
  class Preferences
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("cada preferencia e independente das outras")
    void independentFlags ()
    {
      telnetState.setDo3270Extended (false);
      telnetState.setDoEOR (false);

      assertFalse (telnetState.do3270Extended ());
      assertFalse (telnetState.doEOR ());
      assertTrue (telnetState.doBinary ());
      assertTrue (telnetState.doTerminalType ());

      telnetState.setDoBinary (false);
      telnetState.setDoTerminalType (false);

      assertFalse (telnetState.doBinary ());
      assertFalse (telnetState.doTerminalType ());
    }

    @ParameterizedTest (name = "modelo {0} -> {1}")
    @CsvSource ({ "2, IBM-3278-2-E", "3, IBM-3278-3-E", "4, IBM-3278-4-E",
                  "5, IBM-3278-5-E" })
    @DisplayName ("o numero do modelo escolhe o nome do terminal")
    void mapsModelToTerminalName (int modelNo, String expected)
    {
      telnetState.setDoDeviceType (modelNo);

      assertEquals (expected, telnetState.doDeviceType ());
    }

    @ParameterizedTest (name = "modelo {0}")
    @ValueSource (ints = { 0, 1 })
    @DisplayName ("os modelos 0 e 1 nao existem e devolvem string vazia")
    void modelsZeroAndOneAreEmpty (int modelNo)
    {
      telnetState.setDoDeviceType (modelNo);

      assertEquals ("", telnetState.doDeviceType ());
    }

    @ParameterizedTest (name = "modelo {0}")
    @ValueSource (ints = { -1, 6, 99 })
    @DisplayName ("um modelo fora da tabela e recusado com mensagem clara")
    void modelOutOfRange (int modelNo)
    {
      IllegalArgumentException e =
          assertThrows (IllegalArgumentException.class,
                        () -> telnetState.setDoDeviceType (modelNo));

      assertTrue (e.getMessage ().contains ("Modelo de terminal invalido"),
                  e.getMessage ());
      // a preferencia anterior continua valendo
      assertEquals ("IBM-3278-2-E", telnetState.doDeviceType ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("estado negociado")
  class NegotiatedState
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("3270 extended vale por si so")
    void extendedIsDirect ()
    {
      telnetState.setDoes3270Extended (true);

      assertTrue (telnetState.does3270Extended ());

      telnetState.setDoes3270Extended (false);

      assertFalse (telnetState.does3270Extended ());
    }

    @Test
    @DisplayName ("EOR, binario e tipo de terminal vem de graca com o 3270 extended")
    void extendedImpliesTheRest ()
    {
      // TN3270E ja pressupoe binario, EOR e tipo de terminal: os getters fazem OR
      telnetState.setDoes3270Extended (true);

      assertTrue (telnetState.doesEOR ());
      assertTrue (telnetState.doesBinary ());
      assertTrue (telnetState.doesTerminalType ());
    }

    @Test
    @DisplayName ("sem o extended cada opcao precisa ser negociada")
    void withoutExtendedEachOptionStandsAlone ()
    {
      telnetState.setDoes3270Extended (false);
      telnetState.setDoesEOR (true);

      assertTrue (telnetState.doesEOR ());
      assertFalse (telnetState.doesBinary ());
      assertFalse (telnetState.doesTerminalType ());

      telnetState.setDoesBinary (true);
      telnetState.setDoesTerminalType (true);

      assertTrue (telnetState.doesBinary ());
      assertTrue (telnetState.doesTerminalType ());
    }

    @Test
    @DisplayName ("com TN3270E ligado, recusar EOR ou binario nao tem efeito")
    void extendedImpliesTheOthersByProtocol ()
    {
      // Nao e um descuido: a RFC 2355 define o TN3270E sobre transmissao binaria com
      // marcacao de fim de registro, entao um host que negociou TN3270E ja concordou com
      // as duas. O OR nos getters e o que garante essa regra.
      telnetState.setDoes3270Extended (true);
      telnetState.setDoesEOR (false);
      telnetState.setDoesBinary (false);
      telnetState.setDoesTerminalType (false);

      assertTrue (telnetState.doesEOR ());
      assertTrue (telnetState.doesBinary ());
      assertTrue (telnetState.doesTerminalType ());

      // desligando o TN3270E as recusas individuais voltam a valer
      telnetState.setDoes3270Extended (false);

      assertFalse (telnetState.doesEOR ());
      assertFalse (telnetState.doesBinary ());
      assertFalse (telnetState.doesTerminalType ());
    }

    @Test
    @DisplayName ("guarda o nome do terminal informado pelo host")
    void storesTerminal ()
    {
      telnetState.setTerminal ("IBM-3278-2-E");

      assertEquals ("IBM-3278-2-E", telnetState.getTerminal ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("tela secundaria por modelo")
  class SecondaryScreen
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "{0} -> {1} x {2}")
    @CsvSource ({ "IBM-3278-2-E, 24, 80", "IBM-3278-3-E, 32, 80",
                  "IBM-3278-4-E, 43, 80", "IBM-3278-5-E, 27, 132" })
    @DisplayName ("cada modelo tem seu tamanho alternativo")
    void mapsDeviceTypeToDimensions (String deviceType, int rows, int columns)
    {
      telnetState.setDeviceType (deviceType);

      assertEquals (rows, telnetState.getSecondary ().rows);
      assertEquals (columns, telnetState.getSecondary ().columns);
    }

    @ParameterizedTest (name = "modelo [{0}]")
    @ValueSource (strings = { "IBM-3279-2-E", "IBM-3278-2", "", "qualquer coisa" })
    @DisplayName ("um modelo desconhecido cai no default 24 x 80")
    void unknownDeviceTypeFallsBack (String deviceType)
    {
      telnetState.setDeviceType ("IBM-3278-5-E");            // primeiro sai do default
      telnetState.setDeviceType (deviceType);

      assertEquals (24, telnetState.getSecondary ().rows);
      assertEquals (80, telnetState.getSecondary ().columns);
    }

    @Test
    @DisplayName ("a secundaria e trocada por uma nova instancia a cada chamada")
    void replacesInstance ()
    {
      ScreenDimensions before = telnetState.getSecondary ();

      telnetState.setDeviceType ("IBM-3278-3-E");

      assertFalse (before == telnetState.getSecondary ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("funcoes e LU")
  class FunctionsAndLu
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("guarda a lista de funcoes negociada")
    void storesFunctions ()
    {
      List<Function> functions =
          Arrays.asList (Function.BIND_IMAGE, Function.RESPONSES, Function.SYSREQ);

      telnetState.setFunctions (functions);

      assertTrue (telnetState.toString ().contains ("BIND_IMAGE"));
      assertTrue (telnetState.toString ().contains ("SYSREQ"));
    }

    @Test
    @DisplayName ("aceita o nome da LU atribuido pelo host")
    void storesLogicalUnit ()
    {
      // setLogicalUnit nao tem getter: o teste garante apenas que nao explode
      telnetState.setLogicalUnit ("LU0001");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("contadores de IO")
  class IoCounters
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("sem trafego o resumo diz que nao ha nada a informar")
    void noTraffic ()
    {
      assertEquals ("Nothing to report", telnetState.getSummary ());
    }

    @Test
    @DisplayName ("so escritas ainda nao produzem resumo")
    void writesOnly ()
    {
      telnetState.write (new byte[10]);

      assertEquals ("Nothing to report", telnetState.getSummary ());
    }

    @Test
    @DisplayName ("setLastAccess funciona antes de a thread de keep-alive subir")
    void lastAccessWorksWithoutTheThread ()
    {
      // lastAccess e criado na construcao, e nao dentro de run ()
      telnetState.setLastAccess (LocalDateTime.now (), 100);
      telnetState.setLastAccess (LocalDateTime.now (), 200);
      telnetState.write (new byte[50]);

      assertArrayEquals (new int[] { 2, 300, 150 },
                         numbers (telnetState.getSummary (), "Reads"));
      assertArrayEquals (new int[] { 1, 50, 50 },
                         numbers (telnetState.getSummary (), "Writes"));
    }

    @Test
    @DisplayName ("write funciona sem servidor e sem thread")
    void writeWithoutServer ()
    {
      telnetState.write (new byte[] { 0x01, 0x02 });
      telnetState.write (new byte[] { 0x03 });

      // 2 escritas, 3 bytes: sem leituras o resumo continua vazio
      assertEquals ("Nothing to report", telnetState.getSummary ());
    }

    @Test
    @DisplayName ("com leituras e escritas o resumo traz totais e medias")
    void summarisesBothDirections ()
    {
      telnetState.setLastAccess (LocalDateTime.now (), 100);
      telnetState.setLastAccess (LocalDateTime.now (), 200);
      telnetState.write (new byte[50]);

      String summary = telnetState.getSummary ();

      // 2 leituras, 300 bytes, media 150
      assertArrayEquals (new int[] { 2, 300, 150 }, numbers (summary, "Reads"));
      // 1 escrita, 50 bytes, media 50
      assertArrayEquals (new int[] { 1, 50, 50 }, numbers (summary, "Writes"));
    }

    @Test
    @DisplayName ("a ultima linha soma as duas direcoes")
    void totalsBothDirections ()
    {
      telnetState.setLastAccess (LocalDateTime.now (), 150);
      telnetState.write (new byte[50]);

      // 1 leitura + 1 escrita = 2 operacoes, 200 bytes, media 100
      String[] lines = telnetState.getSummary ().split ("\n");

      assertArrayEquals (new int[] { 2, 200, 100 },
                         numbersIn (lines[lines.length - 1]));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("thread de keep-alive")
  class KeepAlive
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("close sem thread nao faz nada")
    void closeWithoutThread ()
    {
      telnetState.close ();               // nao deve lancar nada
    }

    @Test
    @DisplayName ("close encerra a thread de keep-alive")
    @Timeout (10)
    void runThenClose () throws InterruptedException
    {
      TelnetState state = new TelnetState ();
      state.setTerminalServer (null);

      state.close ();
      Thread.sleep (50);

      // depois do close a thread sai do laco; um novo close continua sendo seguro
      state.close ();
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("toString")
  class ToString
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("lista as sete linhas de status")
    void listsStatus ()
    {
      telnetState.setDoes3270Extended (true);
      telnetState.setTerminal ("IBM-3278-2-E");
      telnetState.setDeviceType ("IBM-3278-2-E");

      String report = telnetState.toString ();

      assertEquals (7, report.split ("\n").length);
      assertTrue (report.contains ("3270 ext ........ true"), report);
      assertTrue (report.contains ("terminal ........ IBM-3278-2-E"), report);
      assertTrue (report.contains ("device type ..... IBM-3278-2-E"), report);
    }

    @Test
    @DisplayName ("mostra os campos ainda nao preenchidos")
    void showsEmptyFields ()
    {
      String report = telnetState.toString ();

      assertTrue (report.contains ("3270 ext ........ false"), report);
      assertTrue (report.contains ("functions ....... null"), report);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("listeners")
  class Listeners
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("aceita e remove listeners sem duplicar")
    void addsAndRemoves ()
    {
      List<TelnetState> seen = new ArrayList<> ();
      TelnetStateListener listener = seen::add;

      telnetState.addTelnetStateListener (listener);
      telnetState.addTelnetStateListener (listener);      // ignorado
      telnetState.removeTelnetStateListener (listener);
      telnetState.removeTelnetStateListener (listener);   // ignorado

      assertTrue (seen.isEmpty ());
    }

    @Test
    @DisplayName ("cada mudanca de estado notifica os listeners")
    void listenersAreNotified ()
    {
      List<TelnetState> seen = new ArrayList<> ();
      telnetState.addTelnetStateListener (seen::add);

      telnetState.setDoes3270Extended (true);
      telnetState.setDoesEOR (true);
      telnetState.setDoesBinary (true);
      telnetState.setDoesTerminalType (true);
      telnetState.setDeviceType ("IBM-3278-3-E");
      telnetState.setTerminal ("IBM-3278-3-E");
      telnetState.setLogicalUnit ("LU0001");
      telnetState.setFunctions (Arrays.asList (Function.BIND_IMAGE));

      assertEquals (8, seen.size ());
      // o listener sempre recebe a propria instancia que mudou
      for (TelnetState reported : seen)
        assertSame (telnetState, reported);
    }

    @Test
    @DisplayName ("um listener removido para de receber as mudancas")
    void removedListenerStopsReceiving ()
    {
      List<TelnetState> seen = new ArrayList<> ();
      TelnetStateListener listener = seen::add;

      telnetState.addTelnetStateListener (listener);
      telnetState.setDoes3270Extended (true);
      telnetState.removeTelnetStateListener (listener);
      telnetState.setDoesEOR (true);

      assertEquals (1, seen.size ());
    }

    @Test
    @DisplayName ("as preferencias nao disparam evento: elas nao vem do host")
    void preferencesDoNotNotify ()
    {
      List<TelnetState> seen = new ArrayList<> ();
      telnetState.addTelnetStateListener (seen::add);

      telnetState.setDo3270Extended (false);
      telnetState.setDoEOR (false);
      telnetState.setDoBinary (false);
      telnetState.setDoTerminalType (false);
      telnetState.setDoDeviceType (3);

      assertTrue (seen.isEmpty ());
    }
  }

  // ---------------------------------------------------------------------------------//
  //  Auxiliares
  // ---------------------------------------------------------------------------------//

  // Os numeros do resumo saem formatados com %,d — o separador de milhar depende do
  // locale, entao o helper remove tudo que nao e digito antes de converter.
  private static int[] numbers (String summary, String label)
  {
    for (String line : summary.split ("\n"))
      if (line.startsWith (label))
        return numbersIn (line.substring (label.length ()));

    throw new AssertionError ("linha ausente no resumo: " + label);
  }

  private static int[] numbersIn (String line)
  {
    String[] parts = line.trim ().split ("\\s+");
    int[] values = new int[parts.length];

    for (int i = 0; i < parts.length; i++)
      values[i] = Integer.parseInt (parts[i].replaceAll ("[^0-9]", ""));

    return values;
  }

}
