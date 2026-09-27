package com.bytezone.dm3270.display;

import static com.bytezone.dm3270.commands.AIDCommand.AID_ENTER;
import static com.bytezone.dm3270.commands.AIDCommand.AID_PA1;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import com.bytezone.dm3270.attributes.Attribute;
import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.datasets.DatasetStore;
import com.bytezone.dm3270.orders.BufferAddress;
import com.bytezone.dm3270.orders.Order;
import com.bytezone.dm3270.plugins.RecordingPluginsStage;
import com.bytezone.dm3270.runtime.TerminalFunction;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.streams.TelnetState;
import com.bytezone.dm3270.structuredfields.SetReplyModeSF;
import com.bytezone.dm3270.testing.JavaFxToolkit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/*
 * A RESPOSTA QUE A Screen MONTA PARA O HOST: o AID, o reply mode e o Read Modified All.
 *
 * O ScreenPacker empacota, mas quem guarda o estado e decide com que argumentos chama-lo e
 * a Screen: o AID corrente, o cursor, o modo e os tipos do Set Reply Mode, o flag
 * temporario do Read Modified All e a troca de modo em volta da gravacao do historico. Nada
 * disso tinha teste. Esta classe existe para o ciclo C5 poder tirar esse estado da Screen
 * sem mudar os bytes que saem.
 *
 * A tela e montada pelo caminho de verdade, um Erase Write processado pela Screen, com cinco
 * campos: protegido, desprotegido COM o MDT ligado, desprotegido sem, um protegido com um
 * Set Attribute de realce (o unico atributo de caractere da tela, e por isso o que torna os
 * tipos do reply mode observaveis) e mais um protegido.
 *
 * O que se observa e so a superficie publica - getData () da resposta, getAID (), pause () e
 * o HistoryScreen corrente. Os comprimentos fixados abaixo foram medidos antes do refactor, e
 * cada um diz em comentario de onde vem.
 */
// -----------------------------------------------------------------------------------//
@ExtendWith (JavaFxToolkit.class)
@DisplayName ("Screen - a resposta ao host (AID, reply mode, Read Modified)")
class ScreenReplyTest
// -----------------------------------------------------------------------------------//
{
  private static final ScreenDimensions MODEL_2 = new ScreenDimensions (24, 80);

  private static final byte ERASE_WRITE = 0x05;
  private static final byte WCC_RESET_KEYBOARD = (byte) 0xC2;
  private static final byte SF = 0x1D;
  private static final byte SA = 0x28;
  private static final byte PROTECTED = 0x20;
  private static final byte UNPROTECTED_MODIFIED = 0x01;
  private static final byte UNPROTECTED = 0x00;

  // o que a Screen passa ao gravar o historico - a copia da constante de la
  private static final byte[] SAVE_SCREEN_REPLY_TYPES =
      { Attribute.XA_HIGHLIGHTING, Attribute.XA_FGCOLOR, Attribute.XA_CHARSET,
        Attribute.XA_BGCOLOR, Attribute.XA_TRANSPARENCY };

  @TempDir
  private Path pluginsDirectory;

  private Preferences prefs;
  private RecordingPluginsStage pluginsStage;
  private Screen screen;

  private Logger screenLogger;
  private ListAppender<ILoggingEvent> warnings;

  // ---------------------------------------------------------------------------------//
  @BeforeEach
  void createScreen ()
  // ---------------------------------------------------------------------------------//
  {
    prefs = Preferences.userRoot ().node ("dm3270-test/" + UUID.randomUUID ());
    pluginsStage =
        JavaFxToolkit.onFxThread ( () -> new RecordingPluginsStage (prefs, pluginsDirectory));
    screen = JavaFxToolkit.onFxThread ( () -> new Screen (MODEL_2, null, prefs,
        TerminalFunction.TERMINAL, pluginsStage, null, new TelnetState (), DatasetStore.NONE));

    warnings = new ListAppender<> ();
    warnings.start ();
    screenLogger = (Logger) LoggerFactory.getLogger (Screen.class);
    screenLogger.addAppender (warnings);
  }

  // ---------------------------------------------------------------------------------//
  @AfterEach
  void cleanUp () throws Exception
  // ---------------------------------------------------------------------------------//
  {
    screenLogger.detachAppender (warnings);
    warnings.stop ();

    pluginsStage.closeClassLoader ();
    prefs.removeNode ();
    prefs.flush ();
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("a tela nasce com AID zero, e nao NO_AID_SPECIFIED")
  void theInitialAidIsZero ()
  // ---------------------------------------------------------------------------------//
  {
    assertEquals (0, screen.getAID ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("setAID e getAID guardam o AID")
  void setAidIsRemembered ()
  // ---------------------------------------------------------------------------------//
  {
    screen.setAID (AID_ENTER);

    assertEquals (AID_ENTER, screen.getAID ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("Read Modified traz AID, cursor e so o campo com MDT")
  void readModifiedSendsOnlyTheModifiedField ()
  // ---------------------------------------------------------------------------------//
  {
    buildScreen ();
    screen.setAID (AID_ENTER);

    byte[] data = onFx (screen::readModifiedFields).getData ();

    byte[] cursor = address (screen.getScreenCursor ().getLocation ());
    byte[] field = address (4);          // o campo desprotegido modificado comeca em 4
    assertArrayEquals (new byte[] { AID_ENTER, cursor[0], cursor[1],  //
                                    Order.SET_BUFFER_ADDRESS, field[0], field[1],  //
                                    (byte) 0xC2, (byte) 0xC2 },
        data);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("Read Modified (F6) e o mesmo que o Read Modified sem tipo")
  void readModifiedF6IsThePlainReadModified ()
  // ---------------------------------------------------------------------------------//
  {
    buildScreen ();
    screen.setAID (AID_ENTER);

    byte[] plain = onFx (screen::readModifiedFields).getData ();
    byte[] f6 = onFx ( () -> screen.readModifiedFields (Command.READ_MODIFIED_F6)).getData ();

    assertArrayEquals (plain, f6);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("com PA1, Read Modified so devolve o AID")
  void aPaKeySendsOnlyTheAid ()
  // ---------------------------------------------------------------------------------//
  {
    buildScreen ();
    screen.setAID (AID_PA1);

    assertArrayEquals (new byte[] { AID_PA1 },
        onFx ( () -> screen.readModifiedFields (Command.READ_MODIFIED_F6)).getData ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("Read Modified All com PA1 manda o campo, e so naquela chamada")
  void readModifiedAllIsOnlyForThatCall ()
  // ---------------------------------------------------------------------------------//
  {
    buildScreen ();
    screen.setAID (AID_PA1);

    byte[] all =
        onFx ( () -> screen.readModifiedFields (Command.READ_MODIFIED_ALL_6E)).getData ();
    byte[] after = onFx (screen::readModifiedFields).getData ();

    assertEquals (8, all.length, "AID, cursor, SBA, endereco e os dois bytes do campo");
    assertEquals (AID_PA1, all[0]);
    assertEquals (Order.SET_BUFFER_ADDRESS, all[3]);
    assertArrayEquals (new byte[] { AID_PA1 }, after);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("um tipo de Read desconhecido devolve nulo e avisa no log da Screen")
  void anUnknownReadTypeReturnsNull ()
  // ---------------------------------------------------------------------------------//
  {
    buildScreen ();

    assertNull (onFx ( () -> screen.readModifiedFields (Command.READ_BUFFER_F2)));

    assertEquals (1, warnings.list.size ());
    assertEquals ("Unknown type in Screen.readModifiedFields()",
        warnings.list.get (0).getFormattedMessage ());
    assertEquals ("com.bytezone.dm3270.display.Screen", warnings.list.get (0).getLoggerName ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("sem Set Reply Mode, Read Buffer responde em modo de campo")
  void readBufferDefaultsToFieldMode ()
  // ---------------------------------------------------------------------------------//
  {
    buildScreen ();
    screen.setAID (AID_ENTER);

    byte[] data = onFx (screen::readBuffer).getData ();

    byte[] cursor = address (screen.getScreenCursor ().getLocation ());
    // AID + cursor, uma posicao por byte, e um byte a mais por Start Field (SF + atributo)
    assertEquals (3 + MODEL_2.size + 5, data.length);
    assertEquals (AID_ENTER, data[0]);
    assertEquals (cursor[0], data[1]);
    assertEquals (cursor[1], data[2]);
    assertEquals (Order.START_FIELD, data[3]);
    assertEquals ((byte) 0xC1, data[5]);
    assertEquals (0, count (data, SA), "modo de campo nao manda atributo de caractere");
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("em modo de campo estendido, os Start Field saem como SFE")
  void extendedFieldModeSendsStartFieldExtended ()
  // ---------------------------------------------------------------------------------//
  {
    buildScreen ();
    screen.setReplyMode (SetReplyModeSF.RM_EXTENDED_FIELD, new byte[0]);

    byte[] data = onFx (screen::readBuffer).getData ();

    assertEquals (Order.START_FIELD_EXTENDED, data[3]);
    assertEquals (0, count (data, SA));
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("em modo de caractere, so os tipos pedidos viram Set Attribute")
  void characterModeSendsOnlyTheRequestedTypes ()
  // ---------------------------------------------------------------------------------//
  {
    buildScreen ();

    screen.setReplyMode (SetReplyModeSF.RM_CHARACTER, new byte[0]);
    byte[] withoutTypes = onFx (screen::readBuffer).getData ();

    screen.setReplyMode (SetReplyModeSF.RM_CHARACTER,
        new byte[] { Attribute.XA_HIGHLIGHTING });
    byte[] withHighlighting = onFx (screen::readBuffer).getData ();

    assertEquals (0, count (withoutTypes, SA));
    assertTrue (count (withHighlighting, SA) > 0);
    assertTrue (withHighlighting.length > withoutTypes.length);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("gravar o historico usa modo de caractere e devolve o modo e os tipos de antes")
  void recordingRestoresTheReplyMode ()
  // ---------------------------------------------------------------------------------//
  {
    buildScreen ();
    screen.setAID (AID_ENTER);

    // o modo que o host pediu: caractere, mas sem realce
    screen.setReplyMode (SetReplyModeSF.RM_CHARACTER, new byte[0]);
    byte[] before = onFx (screen::readBuffer).getData ();

    onFx ( () ->
    {
      screen.hostWriteCompleted (false, reply -> {});
      return null;
    });

    assertArrayEquals (before, onFx (screen::readBuffer).getData (),
        "o modo e os tipos do host tinham de voltar depois da gravacao");

    // o historico guardou a tela no modo da gravacao, com realce
    screen.setReplyMode (SetReplyModeSF.RM_CHARACTER, SAVE_SCREEN_REPLY_TYPES);
    AIDCommand asRecorded = onFx (screen::readBuffer);
    screen.setReplyMode (SetReplyModeSF.RM_FIELD, new byte[0]);
    AIDCommand asField = onFx (screen::readBuffer);

    HistoryManager history = onFx (screen::pause).orElseThrow ();
    assertTrue (history.current ().matches (asRecorded));
    assertFalse (history.current ().matches (asField));
  }

  /*
   * O Erase Write da tela de teste. O WCC destrava o teclado e a Screen grava a tela no
   * historico - e o caminho do checkRecording, no modo de fabrica.
   */
  // ---------------------------------------------------------------------------------//
  private void buildScreen ()
  // ---------------------------------------------------------------------------------//
  {
    byte[] buffer = { ERASE_WRITE, WCC_RESET_KEYBOARD,                              //
                      SF, PROTECTED, (byte) 0xC1, (byte) 0xC1,                      // 0
                      SF, UNPROTECTED_MODIFIED, (byte) 0xC2, (byte) 0xC2,           // 3
                      SF, UNPROTECTED, (byte) 0xC3, (byte) 0xC3,                    // 6
                      SF, PROTECTED, SA, Attribute.XA_HIGHLIGHTING, (byte) 0xF1,    // 9
                      (byte) 0xC4, (byte) 0xC4,                                     //
                      SF, PROTECTED, (byte) 0xC5, (byte) 0xC5 };                    // 12
    onFx ( () ->
    {
      Command.getCommand (buffer, 0, buffer.length).process (screen);
      return null;
    });
    assertEquals (5, screen.getFieldCount ());
  }

  // ---------------------------------------------------------------------------------//
  private static byte[] address (int location)
  // ---------------------------------------------------------------------------------//
  {
    byte[] buffer = new byte[2];
    new BufferAddress (location).packAddress (buffer, 0);
    return buffer;
  }

  // ---------------------------------------------------------------------------------//
  private static int count (byte[] data, byte value)
  // ---------------------------------------------------------------------------------//
  {
    int total = 0;
    for (byte b : data)
      if (b == value)
        total++;
    return total;
  }

  // ---------------------------------------------------------------------------------//
  private static <T> T onFx (JavaFxToolkit.FxSupplier<T> supplier)
  // ---------------------------------------------------------------------------------//
  {
    return JavaFxToolkit.onFxThread (supplier);
  }
}
