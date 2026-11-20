package com.bytezone.dm3270.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.testing.JavaFxToolkit;

import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

// Plugins sao instanciados por reflexao sem retorno, entao os dubles usam PluginProbe.
// Cada teste isola Preferences e @TempDir para nao ler JARs reais da maquina.
// -----------------------------------------------------------------------------------//
@ExtendWith (JavaFxToolkit.class)
@DisplayName ("PluginsStage - o despacho para os plugins")
class PluginsStageDispatchTest
// -----------------------------------------------------------------------------------//
{
  @TempDir
  private Path pluginsDirectory;

  private Preferences prefs;
  private PluginsStage stage;

  // ---------------------------------------------------------------------------------//
  @BeforeEach
  void createPreferencesNode ()
  // ---------------------------------------------------------------------------------//
  {
    prefs = Preferences.userRoot ().node ("dm3270-test/" + UUID.randomUUID ());
    PluginProbe.reset ();
  }

  // Obrigatorio no Windows: URLClassLoader aberto impede a remocao do @TempDir pelo JUnit.
  // ---------------------------------------------------------------------------------//
  @AfterEach
  void closeAndRemove () throws Exception
  // ---------------------------------------------------------------------------------//
  {
    if (stage != null)
      stage.closeClassLoader ();

    prefs.removeNode ();
    prefs.flush ();
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("O despacho automatico, em processAll")
  class AutoDispatch
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("doesAuto e perguntado a cada tela, e a resposta nao vale para a seguinte")
    void doesAutoEPerguntadoACadaTela ()
    // -------------------------------------------------------------------------------//
    {
      PluginProbe.scriptAuto ("ScriptedPlugin", true, false, true);
      register (0, "Scripted", ScriptedPlugin.class, true);
      buildMenu ();

      stage.processAll (data (0));
      stage.processAll (data (1));
      stage.processAll (data (2));

      assertEquals (List.of ("ScriptedPlugin.doesAuto->true", "ScriptedPlugin.processAuto:0",
                             "ScriptedPlugin.doesAuto->false",
                             "ScriptedPlugin.doesAuto->true",
                             "ScriptedPlugin.processAuto:2"),
                    PluginProbe.calls ());
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("um plugin registrado mas nao ativado nao chega a ser perguntado")
    void pluginNaoAtivadoNaoEPerguntado ()
    // -------------------------------------------------------------------------------//
    {
      PluginProbe.scriptAuto ("ScriptedPlugin", true);
      register (0, "Scripted", ScriptedPlugin.class, false);
      buildMenu ();

      stage.processAll (data (0));

      assertEquals (List.of (), PluginProbe.calls ());
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("dois plugins sao perguntados na ordem das posicoes, e um nao pula o outro")
    void doisPluginsNaOrdemDasPosicoes ()
    // -------------------------------------------------------------------------------//
    {
      PluginProbe.scriptAuto ("ScriptedPlugin", false);
      PluginProbe.scriptAuto ("QuietPlugin", true);
      register (0, "Scripted", ScriptedPlugin.class, true);
      register (1, "Quiet", QuietPlugin.class, true);
      buildMenu ();

      stage.processAll (data (7));

      assertEquals (List.of ("ScriptedPlugin.doesAuto->false", "QuietPlugin.doesAuto->true",
                             "QuietPlugin.processAuto:7"),
                    PluginProbe.calls ());
    }

    // activate() roda antes de doesRequest(), permitindo ao plugin habilitar request na ativacao.
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("na montagem: activate primeiro, e doesRequest perguntado duas vezes")
    void activateRodaNaMontagem ()
    // -------------------------------------------------------------------------------//
    {
      PluginProbe.scriptAuto ("ScriptedPlugin", true);
      register (0, "Scripted", ScriptedPlugin.class, true);
      stage ();
      menu ();

      assertEquals (List.of ("ScriptedPlugin.activate",              // select ()
                             "ScriptedPlugin.doesRequest->false",     // select ()
                             "ScriptedPlugin.doesRequest->false"),    // setMenu ()
                    PluginProbe.calls ());
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("activePlugins conta so os ativados com plugin instanciado")
    void activePluginsContaOsAtivados ()
    // -------------------------------------------------------------------------------//
    {
      register (0, "Scripted", ScriptedPlugin.class, true);
      register (1, "Quiet", QuietPlugin.class, false);
      buildMenu ();

      assertEquals (1, stage.activePlugins ());
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("uma posicao com classe inexistente entra desabilitada e nao e despachada")
    void classeInexistenteNaoEDespachada ()
    // -------------------------------------------------------------------------------//
    {
      prefs.put ("PluginName-00", "Fantasma");
      prefs.put ("PluginClass-00", "com.bytezone.dm3270.plugins.NaoExiste");
      prefs.putBoolean ("PluginActivate-00", true);
      Menu menu = buildMenu ();

      stage.processAll (data (0));

      assertEquals (List.of (), PluginProbe.calls ());
      assertEquals (0, stage.activePlugins ());
      assertTrue (itemNamed (menu, "Fantasma").isDisable ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("A trava do doesRequest")
  class RequestLatch
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("quem responde true na primeira ativacao ganha item de request")
    void requestTrueGanhaItem ()
    // -------------------------------------------------------------------------------//
    {
      PluginProbe.scriptRequest ("ScriptedPlugin", true);
      register (0, "Scripted", ScriptedPlugin.class, true);
      Menu menu = buildMenu ();

      assertTrue (hasItemNamed (menu, "Scripted", 2));
    }

    // select() cria o item mesmo ao desativar; rebuildMenu() exige isActivated para exibi-lo.
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("o item de request pode nascer numa ativacao posterior, e ate numa desativacao")
    void itemDeRequestPodeNascerNumaAtivacaoPosterior ()
    // -------------------------------------------------------------------------------//
    {
      PluginProbe.scriptRequest ("ScriptedPlugin", false, false, true);
      register (0, "Scripted", ScriptedPlugin.class, true);
      Menu menu = buildMenu ();

      assertEquals (1, countItemsNamed (menu, "Scripted"));

      toggle (menu, "Scripted", false);
      toggle (menu, "Scripted", true);

      assertEquals (2, countItemsNamed (menu, "Scripted"));
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("criado o item, ele sobrevive a doesRequest virar false - rebuildMenu nao pergunta")
    void itemCriadoSobreviveADoesRequestVirarFalse ()
    // -------------------------------------------------------------------------------//
    {
      PluginProbe.scriptRequest ("ScriptedPlugin", true, false);
      register (0, "Scripted", ScriptedPlugin.class, true);
      Menu menu = buildMenu ();

      toggle (menu, "Scripted", false);
      toggle (menu, "Scripted", true);

      assertEquals (2, countItemsNamed (menu, "Scripted"));

      // toggle() limpa o log antes de disparar; sobra apenas a religada, sem doesRequest().
      assertEquals (List.of ("ScriptedPlugin.activate"), PluginProbe.calls ());
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("desligar o plugin tira o item de request, religar traz de volta")
    void desligarTiraOItemReligarTraz ()
    // -------------------------------------------------------------------------------//
    {
      PluginProbe.scriptRequest ("ScriptedPlugin", true);
      register (0, "Scripted", ScriptedPlugin.class, true);
      Menu menu = buildMenu ();

      toggle (menu, "Scripted", false);
      assertEquals (1, countItemsNamed (menu, "Scripted"));

      toggle (menu, "Scripted", true);
      assertEquals (2, countItemsNamed (menu, "Scripted"));
    }

    // Usa SHORTCUT_DOWN para funcionar tanto com Ctrl (Windows/Linux) quanto Meta (macOS).
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("os dois primeiros plugins de request ficam com os digitos 1 e 2")
    void aceleradoresSaoOsDigitosEmOrdem ()
    // -------------------------------------------------------------------------------//
    {
      PluginProbe.scriptRequest ("ScriptedPlugin", true);
      PluginProbe.scriptRequest ("QuietPlugin", true);
      register (0, "Scripted", ScriptedPlugin.class, true);
      register (1, "Quiet", QuietPlugin.class, true);
      Menu menu = buildMenu ();

      assertEquals (new KeyCodeCombination (KeyCode.DIGIT1, KeyCombination.SHORTCUT_DOWN),
                    requestItem (menu, "Scripted").getAccelerator ());
      assertEquals (new KeyCodeCombination (KeyCode.DIGIT2, KeyCombination.SHORTCUT_DOWN),
                    requestItem (menu, "Quiet").getAccelerator ());
    }

    // Na segunda montagem, requestMenuItem ja existe, entao select() nao rechama doesRequest().
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("getMenu chamado de novo re-instancia o plugin e ativa outra vez")
    void getMenuDeNovoReinstanciaEAtivaOutraVez ()
    // -------------------------------------------------------------------------------//
    {
      PluginProbe.scriptRequest ("ScriptedPlugin", true);
      register (0, "Scripted", ScriptedPlugin.class, true);
      buildMenu ();

      menu ();

      assertEquals (List.of ("ScriptedPlugin.activate",              // select ()
                             "ScriptedPlugin.doesRequest->true"),     // setMenu ()
                    PluginProbe.calls ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("O isolamento do processAll")
  class AutoIsolation
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("um plugin que lanca Exception nao impede os seguintes")
    void umaExceptionNaoImpedeOsSeguintes ()
    // -------------------------------------------------------------------------------//
    {
      PluginProbe.scriptAuto ("ThrowingAutoPlugin", true);
      PluginProbe.scriptAuto ("ScriptedPlugin", true);
      register (0, "Throwing", ThrowingAutoPlugin.class, true);
      register (1, "Scripted", ScriptedPlugin.class, true);
      buildMenu ();

      stage.processAll (data (3));

      assertEquals (List.of ("ThrowingAutoPlugin.doesAuto->true",
                             "ThrowingAutoPlugin.processAuto:3",
                             "ScriptedPlugin.doesAuto->true", "ScriptedPlugin.processAuto:3"),
                    PluginProbe.calls ());
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("um Error escapa do laco e cancela os plugins seguintes")
    void umErrorEscapaEAbortaOsSeguintes ()
    // -------------------------------------------------------------------------------//
    {
      PluginProbe.scriptAuto ("ErroringAutoPlugin", true);
      PluginProbe.scriptAuto ("ScriptedPlugin", true);
      register (0, "Erroring", ErroringAutoPlugin.class, true);
      register (1, "Scripted", ScriptedPlugin.class, true);
      buildMenu ();

      assertThrows (ErroringAutoPlugin.DubleError.class, () -> stage.processAll (data (4)));

      assertEquals (List.of ("ErroringAutoPlugin.doesAuto->true",
                             "ErroringAutoPlugin.processAuto:4"),
                    PluginProbe.calls ());
    }
  }

  // Diferente de processPluginAuto() (que devolve AIDCommand), o request trava e envia direto.
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("O caminho de request")
  class RequestPath
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("processRequest roda sem que ninguem pergunte doesRequest")
    void processRequestRodaSemGuarda ()
    // -------------------------------------------------------------------------------//
    {
      PluginProbe.scriptRequest ("ScriptedPlugin", false);
      wireHost ();

      stage.processPluginRequest (new ScriptedPlugin ());

      assertEquals (List.of ("ScriptedPlugin.processRequest:0", "cursor.moveTo:0"),
                    PluginProbe.calls ());
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("uma excecao no processRequest sobe, porque nao ha try/catch")
    void excecaoNoRequestSobe ()
    // -------------------------------------------------------------------------------//
    {
      wireHost ();

      assertThrows (IllegalStateException.class,
                    () -> stage.processPluginRequest (new ThrowingRequestPlugin ()));
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("sem tecla marcada, nao trava o teclado nem envia nada")
    void semTeclaNaoEnviaNada ()
    // -------------------------------------------------------------------------------//
    {
      wireHost ();

      stage.processPluginRequest (new QuietPlugin ());

      assertEquals (List.of ("QuietPlugin.processRequest:0", "cursor.moveTo:0"),
                    PluginProbe.calls ());
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("com tecla marcada: setAID, leitura, trava e envio, nesta ordem")
    void comTeclaTravaEEnvia ()
    // -------------------------------------------------------------------------------//
    {
      RecordingPluginHost host = wireHost ();
      host.setReply (enterCommand ());

      stage.processPluginRequest (new EnterKeyPlugin ());

      assertEquals (List.of ("EnterKeyPlugin.processRequest:0", "cursor.moveTo:0",
                             "host.setAID:125", "host.readModifiedFields",
                             // AIDCommand abrevia ENTER para "ENTR" na barra de status.
                             "host.lockKeyboard:ENTR", "consolePane.sendAID:ENTR"),
                    PluginProbe.calls ());
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("com tecla marcada mas leitura vazia, nao trava nem envia")
    void comTeclaELeituraVaziaParaAntesDaTrava ()
    // -------------------------------------------------------------------------------//
    {
      wireHost ();

      stage.processPluginRequest (new EnterKeyPlugin ());

      assertEquals (List.of ("EnterKeyPlugin.processRequest:0", "cursor.moveTo:0",
                             "host.setAID:125", "host.readModifiedFields"),
                    PluginProbe.calls ());
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("processPluginAuto devolve null quando nao ha plugin ativo")
    void processPluginAutoSemPluginAtivoDevolveNull ()
    // -------------------------------------------------------------------------------//
    {
      wireHost ();

      assertNull (stage.processPluginAuto ());
      assertEquals (List.of (), PluginProbe.calls ());
    }
  }

  // ---------------------------------------------------------------------------------//
  private void register (int slot, String name, Class<?> type, boolean active)
  // ---------------------------------------------------------------------------------//
  {
    prefs.put (String.format ("PluginName-%02d", slot), name);
    prefs.put (String.format ("PluginClass-%02d", slot), type.getName ());
    prefs.putBoolean (String.format ("PluginActivate-%02d", slot), active);
  }

  // Exige FX thread; as posicoes nas Preferences devem ser gravadas antes desta chamada.
  // ---------------------------------------------------------------------------------//
  private PluginsStage stage ()
  // ---------------------------------------------------------------------------------//
  {
    stage = JavaFxToolkit.onFxThread ( () -> new PluginsStage (prefs, pluginsDirectory));
    return stage;
  }

  // ---------------------------------------------------------------------------------//
  private Menu menu ()
  // ---------------------------------------------------------------------------------//
  {
    return JavaFxToolkit.onFxThread ( () -> stage.getMenu ());
  }

  // Limpa chamadas da montagem (activate/doesRequest), mantendo os roteiros configurados.
  // ---------------------------------------------------------------------------------//
  private Menu buildMenu ()
  // ---------------------------------------------------------------------------------//
  {
    stage ();
    Menu menu = menu ();
    PluginProbe.clearCalls ();
    return menu;
  }

  // ---------------------------------------------------------------------------------//
  private static MenuItem itemNamed (Menu menu, String text)
  // ---------------------------------------------------------------------------------//
  {
    return menu.getItems ().stream ().filter (item -> text.equals (item.getText ()))
        .findFirst ()
        .orElseThrow ( () -> new AssertionError ("item de menu nao encontrado: " + text));
  }

  // CheckMenuItem.fire() nao altera selected (papel do skin), por isso setSelected() antes.
  // ---------------------------------------------------------------------------------//
  private void toggle (Menu menu, String text, boolean selected)
  // ---------------------------------------------------------------------------------//
  {
    CheckMenuItem item = (CheckMenuItem) menu.getItems ().stream ()
        .filter (candidate -> candidate instanceof CheckMenuItem)
        .filter (candidate -> text.equals (candidate.getText ())).findFirst ()
        .orElseThrow ( () -> new AssertionError ("nao achei o CheckMenuItem " + text));

    PluginProbe.clearCalls ();
    onFx ( () ->
    {
      item.setSelected (selected);
      item.fire ();
    });
  }

  // ---------------------------------------------------------------------------------//
  private static void onFx (Runnable action)
  // ---------------------------------------------------------------------------------//
  {
    JavaFxToolkit.onFxThread ( () ->
    {
      action.run ();
      return null;
    });
  }

  // Plugins com request tem um CheckMenuItem e um MenuItem com o mesmo texto.
  // ---------------------------------------------------------------------------------//
  private static long countItemsNamed (Menu menu, String text)
  // ---------------------------------------------------------------------------------//
  {
    return menu.getItems ().stream ().filter (item -> text.equals (item.getText ())).count ();
  }

  // ---------------------------------------------------------------------------------//
  private static boolean hasItemNamed (Menu menu, String text, long times)
  // ---------------------------------------------------------------------------------//
  {
    return countItemsNamed (menu, text) == times;
  }

  // ---------------------------------------------------------------------------------//
  private static MenuItem requestItem (Menu menu, String text)
  // ---------------------------------------------------------------------------------//
  {
    return menu.getItems ().stream ()
        .filter (item -> !(item instanceof CheckMenuItem))
        .filter (item -> text.equals (item.getText ())).findFirst ()
        .orElseThrow ( () -> new AssertionError ("nao achei o item de request " + text));
  }

  // Ambos obrigatorios: processPluginRequest() valida assert consolePane != null (-ea).
  // ---------------------------------------------------------------------------------//
  private RecordingPluginHost wireHost ()
  // ---------------------------------------------------------------------------------//
  {
    stage ();
    RecordingPluginHost host = new RecordingPluginHost ();
    stage.setScreen (host);
    stage.setConsolePane (new RecordingAidSender ());
    PluginProbe.clearCalls ();
    return host;
  }

  // Formato 3270 de entrada AID: byte 0 = AID, bytes 1-2 = endereco do cursor.
  // ---------------------------------------------------------------------------------//
  private static AIDCommand enterCommand ()
  // ---------------------------------------------------------------------------------//
  {
    byte[] buffer = { AIDCommand.AID_ENTER, 0x40, 0x40 };
    return new AIDCommand (buffer, 0, buffer.length);
  }

  // ---------------------------------------------------------------------------------//
  private static PluginData data (int sequence)
  // ---------------------------------------------------------------------------------//
  {
    return new PluginData (sequence, new ScreenLocation (0), new ArrayList<> ());
  }
}
