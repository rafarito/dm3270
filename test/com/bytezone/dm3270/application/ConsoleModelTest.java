package com.bytezone.dm3270.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import com.bytezone.dm3270.orders.BufferAddress;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.utilities.Site;
import com.bytezone.dm3270.utilities.SiteValue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/*
 * O setModel do Console, e os DOIS defeitos que ele preserva de proposito.
 *
 * Sao o item 1 do BACKLOG-DEFEITOS.md, e ate este commit estavam DESCONGELADOS - o backlog
 * afirmava que "a caracterizacao de setModel" os cobria, e o passo 8 mediu que ela nunca
 * existiu: "grep -rn setModel test/" nao devolvia nada. Este e o primeiro teste a ve-los.
 *
 * NAO HA @ExtendWith (JavaFxToolkit.class), e isso e o ponto. O Console estende
 * javafx.application.Application, mas "new Console ()" nao toca o toolkit: os inicializadores
 * estaticos sao um Logger, um int e um SiteValue - classe que nao tem UM import -, e os de
 * instancia sao new ScreenDimensions (24, 80) e new TelnetState (), os dois sem JavaFX. O
 * CLAUDE.md dizia que "nada na suite instancia um Console"; agora instancia, e de graca.
 *
 * O QUE ELE NAO ALCANCA, e vale dizer: so o setModel. startSelectedFunction, setConsolePane,
 * setSpyPane e savePreferences dependem de optionStage, primaryStage e pluginsStage, que so
 * existem depois de start (Stage) - e start termina mostrando janela. O setModel e o unico
 * metodo do Console que e decisao em vez de fiacao, e por isso o unico que roda num Console
 * nu.
 *
 * COMO ELE OBSERVA: pelos tres acessores de pacote que a costura abriu, mais o log. O
 * doDeviceType () do TelnetState ja era publico (TelnetState:389) - metade do efeito sempre
 * esteve visivel, e ninguem olhava.
 */
// -----------------------------------------------------------------------------------//
@DisplayName ("Console.setModel - o item 1 do backlog, congelado")
class ConsoleModelTest
// -----------------------------------------------------------------------------------//
{
  private Console console;
  private Logger consoleLogger;
  private ListAppender<ILoggingEvent> warnings;

  // ---------------------------------------------------------------------------------//
  @BeforeEach
  void createConsole ()
  // ---------------------------------------------------------------------------------//
  {
    console = new Console ();

    warnings = new ListAppender<> ();
    warnings.start ();

    consoleLogger = (Logger) LoggerFactory.getLogger (Console.class);
    consoleLogger.addAppender (warnings);
  }

  // ---------------------------------------------------------------------------------//
  @AfterEach
  void detachAppender ()
  // ---------------------------------------------------------------------------------//
  {
    // A captura de log e estado global do logger. Nao destacar contamina outras classes.
    consoleLogger.detachAppender (warnings);
    warnings.stop ();

    // E o construtor de ScreenDimensions chama BufferAddress.setScreenWidth (columns), que e
    // estado estatico GLOBAL (relatorio, 5.22). O modelo 5 o deixa em 132, e o Surefire roda
    // as classes numa JVM so, em ordem nao especificada.
    new ScreenDimensions (24, 80);
  }

  // ---------------------------------------------------------------------------------//
  private static Site siteWithModel (int model)
  // ---------------------------------------------------------------------------------//
  {
    return new SiteValue ("host", "localhost", 23, true, model, false, false, false, "");
  }

  // ---------------------------------------------------------------------------------//
  private List<String> warningMessages ()
  // ---------------------------------------------------------------------------------//
  {
    return warnings.list.stream ().map (ILoggingEvent::getFormattedMessage).toList ();
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("os modelos validos")
  class ValidModels
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @ParameterizedTest (name = "modelo {0} configura {1}x{2} e negocia {3}")
    @CsvSource ({ "2, 24, 80, IBM-3278-2-E", "3, 32, 80, IBM-3278-3-E",
                  "4, 43, 80, IBM-3278-4-E" })
    @DisplayName ("configuram a dimensao, negociam o tipo, e nao reclamam")
    void configureWithoutComplaining (int model, int rows, int columns, String deviceType)
    // -------------------------------------------------------------------------------//
    {
      console.setModel (siteWithModel (model));

      ScreenDimensions dimensions = console.alternateScreenDimensions ();
      assertNotNull (dimensions);
      assertEquals (rows, dimensions.rows);
      assertEquals (columns, dimensions.columns);

      assertEquals (deviceType, console.telnetState ().doDeviceType ());
      assertEquals (List.of (), warningMessages ());
    }
  }

  /*
   * O DEFEITO, METADE UM: o modelo 5 e valido - 27x132 - e o switch original nao tinha break
   * no case 5, entao ele configurava-se corretamente e SO ENTAO caia no default e reclamava
   * de si mesmo. A ordem esta preservada de proposito, e o comentario de Console.java:187
   * manda preserva-la: configura primeiro, reclama depois.
   */
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("o modelo 5 - valido, e mesmo assim reclamado")
  class ModelFive
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("configura 27x132 E reclama - nessa ordem")
    void configuresAndThenComplains ()
    // -------------------------------------------------------------------------------//
    {
      console.setModel (siteWithModel (5));

      ScreenDimensions dimensions = console.alternateScreenDimensions ();
      assertEquals (27, dimensions.rows);
      assertEquals (132, dimensions.columns);
      assertEquals ("IBM-3278-5-E", console.telnetState ().doDeviceType ());

      assertEquals (List.of ("Invalid model number: 5"), warningMessages ());
    }

    /*
     * E o efeito global chega ate o BufferAddress, que e como o ScreenDimensionsTest ja
     * observa esse estado estatico: a mesma posicao passa a ser descrita noutra linha/coluna.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("e o efeito colateral global do ScreenDimensions acontece de verdade")
    void mutatesTheGlobalScreenWidth ()
    // -------------------------------------------------------------------------------//
    {
      console.setModel (siteWithModel (5));

      assertEquals ("0132 001/000 : C2 C4", new BufferAddress (132).toString ());
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("o aviso sai do logger do Console, e o nome do logger e observavel")
    void warnsUnderTheConsoleLogger ()
    // -------------------------------------------------------------------------------//
    {
      console.setModel (siteWithModel (5));

      assertEquals (1, warnings.list.size ());
      assertEquals ("com.bytezone.dm3270.application.Console",
          warnings.list.get (0).getLoggerName ());
    }
  }

  /*
   * O DEFEITO, METADE DOIS: o ramo invalido nao atribui alternateScreenDimensions, que e
   * campo de INSTANCIA reaproveitado entre lancamentos na mesma JVM. Um modelo invalido herda
   * a dimensao do lancamento anterior.
   */
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("os modelos invalidos")
  class InvalidModels
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @ParameterizedTest (name = "modelo {0}")
    @ValueSource (ints = { 0, 1, 6, 99, -1 })
    @DisplayName ("reclamam, e nao tocam dimensao nem tipo negociado")
    void complainWithoutConfiguring (int model)
    // -------------------------------------------------------------------------------//
    {
      String before = console.telnetState ().doDeviceType ();

      console.setModel (siteWithModel (model));

      assertNull (console.alternateScreenDimensions ());
      assertEquals (before, console.telnetState ().doDeviceType ());
      assertEquals (List.of ("Invalid model number: " + model), warningMessages ());
    }

    /*
     * setDoDeviceType lanca IllegalArgumentException fora de 0..5 (TelnetState:431). Ela
     * nunca escapa do setModel porque a chamada esta atras do isPresent () - e o 6 e o 99
     * acima sao a prova, ja que o teste nao declara throws e passaria a falhar se ela subisse.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("o modelo invalido nunca chega ao setDoDeviceType")
    void neverReachTheTelnetStateGuard ()
    // -------------------------------------------------------------------------------//
    {
      console.setModel (siteWithModel (99));

      assertEquals ("IBM-3278-2-E", console.telnetState ().doDeviceType (),
          "continua o valor que o construtor do TelnetState poe em TelnetState:71");
    }

    /*
     * ESTE E O CASO QUE PROVA A HERANCA, e ele TEM de ser um metodo so: duas chamadas no
     * MESMO Console. Dividido em dois @Test, cada um receberia um Console novo e a heranca
     * desapareceria - e ela e justamente o defeito.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("HERANCA: depois de um modelo valido, o invalido fica com a dimensao dele")
    void anInvalidModelInheritsThePreviousDimensions ()
    // -------------------------------------------------------------------------------//
    {
      console.setModel (siteWithModel (5));
      ScreenDimensions afterModelFive = console.alternateScreenDimensions ();

      console.setModel (siteWithModel (6));

      assertSame (afterModelFive, console.alternateScreenDimensions (),
          "o ramo invalido nao atribui, entao a dimensao do lancamento anterior fica");
      assertEquals (132, console.alternateScreenDimensions ().columns);
      assertTrue (warningMessages ().contains ("Invalid model number: 6"));
    }
  }
}
