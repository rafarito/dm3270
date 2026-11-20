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

// Instancia Console sem toolkit JavaFX; setModel nao toca widgets nem Stages.
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

    // Restaura BufferAddress.screenWidth (estado estatico global que o modelo 5 deixa em 132).
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

  // Comportamento preservado do switch sem break no case 5: configura 27x132 e depois avisa.
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

  // Comportamento preservado: modelo invalido nao sobrescreve alternateScreenDimensions.
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

    // TelnetState.setDoDeviceType rejeitaria 99, mas o switch nao o chama para modelo invalido.
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
