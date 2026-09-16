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

/*
 * A PRIMEIRA REDE DO CONSTRUTOR DA Screen - 1.109 linhas que ate este commit nao tinham um
 * teste sequer, e que sao o unico alvo que resta da refatoracao estrutural.
 *
 * NAO HOUVE COSTURA NENHUMA. Nenhuma linha de src/ mudou para esta classe existir, e esse e
 * o achado que decidiu o passo: o construtor da Screen e publico, recebe oito parametros, e
 * um teste consegue fornecer os oito - ScreenDimensions, um no de Preferences descartavel,
 * TerminalFunction, o PluginsStage pelo construtor de pacote que o passo 9 abriu, um Site que
 * pode ser nulo, um TelnetState headless e um DatasetStore. O documento de handoff dizia que
 * a Screen "nao se instancia num teste hoje"; ela se instancia.
 *
 * E NENHUMA JANELA ABRE. Era a duvida real, porque o construtor monta duas Stage -
 * TransfersStage (:149) e ConsoleLogStage (:151). Conferido: nenhum dos dois construtores
 * chama show (); o do ConsoleLogStage chama em setConsoleLog (...), que este teste nao
 * alcanca.
 *
 * O QUE CUSTA, e esta escrito aqui porque nao esta em documento nenhum: construir uma Screen
 * TOCA O DIRETORIO PESSOAL de quem roda a suite. A cadeia e Screen -> TransfersStage (:149)
 * -> FilesTab -> new ReporterNode (prefs) -> TreePanel.getTree (path), com
 * Paths.get (System.getProperty ("user.home"), "dm3270", "files") HARD-CODED em
 * ReporterNode:61 - nao vem de preferencia, e portanto o no descartavel deste teste NAO o
 * isola. E o getTree CRIA o diretorio se faltar (TreePanel:52) e o percorre recursivamente
 * (:188). Numa maquina com muitos arquivos baixados esta classe fica lenta.
 *
 * COMO ELE ALCANCA A CLASSE: pela superficie publica, nunca por campo nem reflexao. E a
 * disciplina do OptionStageTest e do PluginsStageDispatchTest - um teste que le os campos
 * vira refem do refactor que deveria vigiar, e o refactor que vem a seguir e justamente a
 * desmontagem deste construtor.
 *
 * O QUE ESTA REDE **NAO** COBRE, e o plano deste passo errou sobre isso. O construtor faz
 * NOVE registros de ouvinte, em :165-177, e o plano dizia que os nove seriam "verificaveis
 * pelo efeito". Medido: OITO nao sao. Os registros acontecem entre objetos que o proprio
 * construtor cria - screenPacker, transfersStage, transferMenu, fieldManager -, e os
 * conjuntos que os guardam sao privados e sem acessor (FieldManager:270,
 * Screen:1060). Alcanca-los exigiria abrir superficie, que e exatamente o que este passo se
 * proibiu de fazer na Screen.
 *
 * O unico observavel e o nono - telnetState.addTelnetStateListener (this) (:177) -, porque o
 * TelnetState e INJETADO e portanto pode ser um gravador. Os outros oito so ficam
 * observaveis quando a construcao sair para um colaborador atras de uma porta, que e o passo
 * seguinte. Tambem fica de fora a injecao tardia transfersStage.setTransferManager (:157),
 * pela mesma razao.
 */
// -----------------------------------------------------------------------------------//
@ExtendWith (JavaFxToolkit.class)
@DisplayName ("Screen - o construtor, e a ordem que ele exige")
class ScreenConstructionTest
// -----------------------------------------------------------------------------------//
{
  private static final ScreenDimensions MODEL_2 = new ScreenDimensions (24, 80);

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
    // As Preferences sao globais por JVM, e o construtor do PluginsStage GRAVA dez posicoes
    // ja na construcao. Um no descartavel por caso e a disciplina do OptionStageTest:93.
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
    // O PluginsStage abre um class loader por JAR. A pasta aqui e vazia, entao nao ha loader
    // nenhum - mas fechar e a simetria com Console.stop ():294, e evita que isto vire
    // armadilha se alguem depositar um JAR de fixture na pasta.
    pluginsStage.closeClassLoader ();

    prefs.removeNode ();
    prefs.flush ();

    // O construtor de ScreenDimensions chama BufferAddress.setScreenWidth (columns), que e
    // estado estatico GLOBAL (relatorio, 5.22). Os casos com 27x132 o deixam em 132, e o
    // Surefire roda as classes numa JVM so, em ordem nao especificada. Restaurar aqui e o que
    // impede esta classe de quebrar outra que nao tem nada a ver com ela.
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
    assertSame (pluginsStage, screen.getPluginsStage ());
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

  /*
   * Esta e a unica restricao de ORDEM que o construtor tem, e nada no codigo a declara.
   *
   * Screen:181 termina com pluginsStage.setScreen (this), e PluginsStage.setScreen:143 chama
   * de volta screen.getScreenDimensions () - o host consome a tela ainda dentro do construtor
   * dela. getScreenDimensions () (:399) le o campo currentScreen, que so e atribuido em :178,
   * tres linhas antes. Inverter as duas linhas devolveria null aqui, e nenhuma outra prova
   * pegaria isso.
   */
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

  /*
   * COM dimensao alternativa a corrente CONTINUA sendo a default, e isso surpreende.
   *
   * O construtor escolhe "alternate ?? default" para dimensionar cursor, campos, historico e
   * o vetor de ScreenPosition (:137-138) - mas termina em setCurrentScreen (DEFAULT) (:178),
   * e getScreenDimensions () (:399) responde pelo currentScreen, nao pelo campo. Quem le "a
   * tela foi construida com 27x132" e pergunta as dimensoes recebe 24x80.
   */
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

  /*
   * A ASSIMETRIA: duas regras de "qual e a dimensao corrente" dentro do MESMO construtor.
   *
   * fontChanged (:732-734) dimensiona o canvas com "alternate ?? default" - 132 colunas -,
   * enquanto getScreenDimensions () reporta 80. A largura e fontWidth * columns + xOffset * 2,
   * e o xOffset e sempre 4 (ScreenDimensions:20), entao a diferenca e exatamente
   * fontWidth * 52.
   *
   * Isto e caracterizacao, nao aprovacao - esta anotado no BACKLOG-DEFEITOS.md.
   */
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

  /*
   * A REENTRANCIA DO FontManager, que Screen:720 declara mas nenhum documento registra.
   *
   * FontManagerType1 recebe a Screen CONCRETA (:146) e, dentro do proprio construtor, chama
   * setFont (...) -> screen.fontChanged (...) (:728), que le consolePane e screenPositions -
   * os dois NULOS naquele instante. Ela so sobrevive por causa das guardas de :740 e :743, e
   * essas nao estao declaradas em lugar nenhum. Que o tamanho semeado chegue ao acessor prova
   * que a chamada reentrante aconteceu e completou.
   */
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

  /*
   * O CASO MAIS VALIOSO DESTA CLASSE, e o unico que vigia o refactor que vem a seguir.
   *
   * fontChanged termina com eraseScreen () e draw () DENTRO de "if (screenPositions != null)"
   * (:743), e o vetor so nasce na linha 159 - depois da chamada reentrante da linha 146. Logo
   * a tela NAO e desenhada durante a construcao.
   *
   * Quem for desmontar este construtor no composition root vai querer reordenar as linhas.
   * Mover a criacao do vetor para antes da fabrica de fonte faria eraseScreen () e draw ()
   * passarem a rodar na construcao - mudanca de comportamento observavel, e portanto Regra 1.
   * Nenhuma outra prova do projeto pega isso.
   */
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

    // e o gravador funciona: depois da construcao, draw () e alcancavel e registrado
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

  /*
   * O gravador do desenho. E estatico porque a lista precisa existir ANTES de super (...)
   * terminar - um campo de instancia so seria atribuido depois, e o caso perderia justamente
   * a janela que ele existe para observar. E o idioma do PluginProbe do passo 9.
   */
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

  /*
   * Um TelnetState que conta os registros. E subclasse, e nao duble de interface, porque o
   * tipo e concreto e o construtor da Screen o recebe pronto - o idioma do RecordingCursor.
   */
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

  /*
   * Um DatasetStore que grava em vez de executar - o idioma do RecordingCursor. O
   * DatasetStore.NONE nao serviria aqui: ele nao faz nada E nao conta que nao fez.
   */
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
