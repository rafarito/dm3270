package com.bytezone.dm3270.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.DatasetStore;
import com.bytezone.dm3270.datasets.Member;
import com.bytezone.dm3270.datasets.StoreListener;
import com.bytezone.dm3270.plugins.RecordingPluginsStage;
import com.bytezone.dm3270.runtime.TerminalFunction;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.streams.TelnetState;
import com.bytezone.dm3270.streams.TelnetStateListener;
import com.bytezone.dm3270.testing.JavaFxToolkit;

// Construir Screen toca ~/dm3270/files via ReporterNode (nao isolado pelo no de Preferences).
// -----------------------------------------------------------------------------------//
@ExtendWith (JavaFxToolkit.class)
@DisplayName ("Screen - o construtor, e a ordem que ele exige")
class ScreenConstructionTest
// -----------------------------------------------------------------------------------//
{
  private static final ScreenDimensions MODEL_2 = new ScreenDimensions (24, 80);

  private static final byte ERASE_WRITE = 0x05;
  private static final byte WCC_RESET_KEYBOARD = (byte) 0xC2;
  private static final byte SF = 0x1D;
  private static final byte UNPROTECTED = 0x00;

  @TempDir
  private Path pluginsDirectory;

  private Preferences prefs;
  private TelnetState telnetState;
  private RecordingPluginsStage pluginsStage;
  private RecordingDatasetStore datasetStore;

  // ---------------------------------------------------------------------------------//
  @BeforeEach
  void createCollaborators ()
  // ---------------------------------------------------------------------------------//
  {
    // Preferences e global por JVM e PluginsStage escreve na construcao; isola por caso.
    prefs = Preferences.userRoot ().node ("dm3270-test/" + UUID.randomUUID ());

    telnetState = new TelnetState ();
    datasetStore = new RecordingDatasetStore ();
    pluginsStage =
        JavaFxToolkit.onFxThread ( () -> new RecordingPluginsStage (prefs, pluginsDirectory));
  }

  // ---------------------------------------------------------------------------------//
  @AfterEach
  void removePreferencesNode () throws Exception
  // ---------------------------------------------------------------------------------//
  {
    // Fecha eventual URLClassLoader caso haja JARs na pasta de plugins.
    pluginsStage.closeClassLoader ();

    prefs.removeNode ();
    prefs.flush ();

    // Restaura largura 80 no estado estatico global alterado por ScreenDimensions(27, 132).
    new ScreenDimensions (24, 80);
  }

  // ---------------------------------------------------------------------------------//
  private Screen screen (ScreenDimensions alternate)
  // ---------------------------------------------------------------------------------//
  {
    return JavaFxToolkit.onFxThread ( () -> new Screen (MODEL_2, alternate, prefs,
        TerminalFunction.TERMINAL, pluginsStage, null, telnetState, datasetStore));
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("constroi, e os colaboradores do construtor nascem")
  void constructs ()
  // ---------------------------------------------------------------------------------//
  {
    Screen screen = screen (null);

    assertNotNull (screen.getScreenCursor ());
    assertNotNull (screen.getScreenSelection ());
    assertNotNull (screen.getFieldManager ());
    assertNotNull (screen.getScreenWatcher ());
    assertNotNull (screen.getFontManager ());
    assertNotNull (screen.getAssistantStage ());
    assertNotNull (screen.getConsoleLogStage ());
    assertNotNull (screen.getSystemMessage ());
    assertNotNull (screen.getTransferManager ());
    assertNotNull (screen.getPen ());
    assertNotNull (screen.getScreenPositions ());
    assertSame (telnetState, screen.getTelnetState ());
    assertEquals (TerminalFunction.TERMINAL, screen.getFunction ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("sem dimensao alternativa, a corrente E a default - a mesma instancia")
  void defaultDimensionsAreTheSameInstance ()
  // ---------------------------------------------------------------------------------//
  {
    Screen screen = screen (null);

    assertSame (MODEL_2, screen.getScreenDimensions ());
  }

  // pluginsStage.setScreen(this) chama getScreenDimensions(); exige currentScreen ja definido.
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("o PluginsStage ve uma tela utilizavel dentro do construtor dela")
  void thePluginHostSeesAUsableScreenDuringConstruction ()
  // ---------------------------------------------------------------------------------//
  {
    screen (null);

    assertEquals (List.of ("setScreen"), pluginsStage.calls);
    assertSame (MODEL_2, pluginsStage.dimensionsSeenDuringConstruction);
  }

  // Campo + WCC de destravamento satisfazem HostWriteCompletion para acionar processPluginAuto.
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("o PluginsStage recebido e o que roda os plugins depois de uma escrita")
  void theReceivedPluginsStageRunsThePluginsAfterAHostWrite ()
  // ---------------------------------------------------------------------------------//
  {
    Screen screen = screen (null);
    byte[] buffer = { ERASE_WRITE, WCC_RESET_KEYBOARD, SF, UNPROTECTED, (byte) 0xC1 };

    JavaFxToolkit.onFxThread ( () ->
    {
      Command.getCommand (buffer, 0, buffer.length).process (screen);
      return null;
    });

    assertEquals (1, screen.getFieldCount ());
    assertEquals (List.of ("setScreen", "processPluginAuto"), pluginsStage.calls);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("o FieldManager abre o DatasetStore durante a construcao")
  void theFieldManagerOpensTheStore ()
  // ---------------------------------------------------------------------------------//
  {
    screen (null);

    assertEquals (List.of ("open"), datasetStore.calls);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("a Screen se registra no TelnetState durante a construcao")
  void registersItselfWithTheTelnetState ()
  // ---------------------------------------------------------------------------------//
  {
    RecordingTelnetState recording = new RecordingTelnetState ();
    telnetState = recording;

    Screen screen = screen (null);

    assertEquals (1, recording.listenersAdded);
    assertSame (screen, recording.lastListener);
  }

  // Protocolo 3270 inicia sempre na particao padrao ate receber Erase Write Alternate.
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("com dimensao alternativa, a corrente AINDA e a default")
  void theAlternateDimensionsAreNotTheCurrentOnes ()
  // ---------------------------------------------------------------------------------//
  {
    Screen screen = screen (new ScreenDimensions (27, 132));

    assertSame (MODEL_2, screen.getScreenDimensions ());
    assertEquals (80, screen.getScreenDimensions ().columns);
  }

  // Canvas usa a maior geometria para evitar resize; acessor segue a particao ativa.
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("ASSIMETRIA: o canvas usa a alternativa, o acessor reporta a default")
  void theCanvasIsSizedByTheAlternateWhileTheAccessorReportsTheDefault ()
  // ---------------------------------------------------------------------------------//
  {
    double narrow = screen (null).getWidth ();

    double wide = screen (new ScreenDimensions (27, 132)).getWidth ();

    assertTrue (wide > narrow,
        "o canvas foi dimensionado com 132 colunas, e getScreenDimensions () diz 80");
  }

  // FontManager chama screen.fontChanged na construcao enquanto screenPositions ainda e null.
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("o FontManager le as preferencias e chama a tela de volta, semiconstruida")
  void theFontManagerCallsBackIntoTheHalfBuiltScreen ()
  // ---------------------------------------------------------------------------------//
  {
    prefs.put ("FontName", "Monospaced");
    prefs.put ("FontSize", "20");

    Screen screen = screen (null);

    assertEquals ("Monospaced", screen.getFontManager ().getFontName ());
    assertEquals (20, screen.getFontManager ().getFontSize ());
  }

  // screenPositions nasce apos FontManager, entao fontChanged reentrante nao dispara draw().
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("a tela NAO e desenhada durante a construcao")
  void theScreenIsNotDrawnWhileItIsBeingBuilt ()
  // ---------------------------------------------------------------------------------//
  {
    RecordingScreen.calls.clear ();

    RecordingScreen screen = JavaFxToolkit.onFxThread ( () -> new RecordingScreen (MODEL_2,
        prefs, pluginsStage, telnetState, datasetStore));

    assertEquals (List.of (), RecordingScreen.calls,
        "draw () rodou durante a construcao - as linhas 146 e 159 foram reordenadas?");

    JavaFxToolkit.onFxThread ( () ->
    {
      screen.draw ();
      return null;
    });

    assertEquals (List.of ("draw"), RecordingScreen.calls);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("close () e chamavel logo apos a construcao")
  void closesCleanly ()
  // ---------------------------------------------------------------------------------//
  {
    Screen screen = screen (null);

    JavaFxToolkit.onFxThread ( () ->
    {
      screen.close ();
      return null;
    });

    assertEquals (List.of ("open", "close"), datasetStore.calls);
  }

  // Lista estatica porque super() pode chamar draw() antes da inicializacao dos campos.
  // ---------------------------------------------------------------------------------//
  private static final class RecordingScreen extends Screen
  // ---------------------------------------------------------------------------------//
  {
    private static final List<String> calls = new ArrayList<> ();

    // -------------------------------------------------------------------------------//
    RecordingScreen (ScreenDimensions dimensions, Preferences prefs,
        RecordingPluginsStage pluginsStage, TelnetState telnetState, DatasetStore store)
    // -------------------------------------------------------------------------------//
    {
      super (dimensions, null, prefs, TerminalFunction.TERMINAL, pluginsStage, null,
          telnetState, store);
    }

    // -------------------------------------------------------------------------------//
    @Override
    public void draw ()
    // -------------------------------------------------------------------------------//
    {
      calls.add ("draw");
      super.draw ();
    }
  }

  // ---------------------------------------------------------------------------------//
  private static final class RecordingTelnetState extends TelnetState
  // ---------------------------------------------------------------------------------//
  {
    private int listenersAdded;
    private Object lastListener;

    // -------------------------------------------------------------------------------//
    @Override
    public void addTelnetStateListener (TelnetStateListener listener)
    // -------------------------------------------------------------------------------//
    {
      listenersAdded++;
      lastListener = listener;

      super.addTelnetStateListener (listener);
    }
  }

  // ---------------------------------------------------------------------------------//
  private static final class RecordingDatasetStore implements DatasetStore
  // ---------------------------------------------------------------------------------//
  {
    private final List<String> calls = new ArrayList<> ();

    // -------------------------------------------------------------------------------//
    @Override
    public void open (StoreListener listener)
    // -------------------------------------------------------------------------------//
    {
      calls.add ("open");
    }

    // -------------------------------------------------------------------------------//
    @Override
    public void close (StoreListener listener)
    // -------------------------------------------------------------------------------//
    {
      calls.add ("close");
    }

    // -------------------------------------------------------------------------------//
    @Override
    public void update (Dataset dataset)
    // -------------------------------------------------------------------------------//
    {
      calls.add ("update dataset");
    }

    // -------------------------------------------------------------------------------//
    @Override
    public void update (Member member)
    // -------------------------------------------------------------------------------//
    {
      calls.add ("update member");
    }
  }
}
