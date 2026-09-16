package com.bytezone.dm3270.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.bytezone.dm3270.testing.JavaFxToolkit;

import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

/*
 * A primeira rede do PluginsStage - 750 linhas que ate aqui nao tinham teste nenhum, e que
 * estao fora do <targetClasses> do PIT.
 *
 * O QUE ESTA CLASSE CONGELA, e por que importa para o passo 9:
 *
 *   doesAuto () e perguntado A CADA TELA, por plugin ativado, dentro de processAll. Nao e
 *   propriedade do plugin: cinco dos seis plugins reais ligam e desligam esse campo durante
 *   a execucao - sao 39 transicoes medidas -, e no FanLogon esse vaivem E a maquina de
 *   estados do logon automatico. O plano escrito do passo 9 pedia despacho por
 *   "instanceof AutoPlugin com fallback para doesAuto ()"; instanceof e uma resposta
 *   ESTATICA para uma pergunta que muda a cada tela, e trocar uma pela outra quebraria os
 *   cinco sem que teste nenhum acusasse. O caso doesAutoEPerguntadoACadaTela e o que torna
 *   essa troca uma build vermelha.
 *
 * COMO O TESTE OBSERVA, e este detalhe e reusavel: o host instancia cada plugin por
 * reflexao, dentro de PluginEntry.instantiate (), e nunca devolve a instancia - nao ha
 * referencia para segurar. Os dubles falam pelo PluginProbe, que e estatico por isso. E o
 * teste alcanca o menu por getMenu ().getItems (), NUNCA pela lista privada plugins nem por
 * reflexao: e a disciplina do OptionStageTest, que por causa dela atravessou sem uma linha
 * mudada o commit que fechou dez campos. Os blocos 1 e 3 vao mexer justamente aqui dentro.
 *
 * As Preferences sao globais por JVM e o PluginsStage le e GRAVA as dez posicoes ja no
 * construtor, entao cada teste recebe um no proprio. E a pasta de plugins aponta para um
 * @TempDir: sem isso a suite varreria a pasta real de quem a roda e auto-registraria os JARs
 * instalados nas preferencias do teste.
 */
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

  /*
   * O closeClassLoader e obrigatorio, e nao higiene: no Windows um URLClassLoader aberto
   * mantem o JAR mapeado e a remocao do @TempDir pelo JUnit falha com
   * DirectoryNotEmptyException. O TESTING.md ja registra uma falha intermitente de @TempDir
   * por handle aberto; esta seria uma segunda, e reprodutivel.
   */
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
    /*
     * O caso central do bloco 0. Roteiro [true, false, true] em tres telas consecutivas: o
     * plugin e perguntado NAS TRES e processa em duas. Um despacho resolvido uma vez so -
     * por instanceof, por cache, por campo lido na ativacao - produziria tres processAuto ou
     * nenhum, e falharia aqui.
     */
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

    /*
     * Os dois dubles escrevem na MESMA lista, entao este caso afirma tambem a ordem ENTRE
     * eles - que e a ordem das posicoes nas Preferences.
     */
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

    /*
     * A sequencia exata da montagem, e ela desmentiu a expectativa escrita antes de rodar:
     * doesRequest () e perguntado DUAS vezes, nao uma. A primeira esta em
     * PluginEntry.select (), logo depois do activate () e guardada por
     * requestMenuItem == null; a segunda em setMenu (), quando o menu decide se acrescenta a
     * secao de request. Nenhum documento do projeto registra isso.
     *
     * E note a ordem: activate () roda ANTES da primeira pergunta. E o que permite ao
     * FanLogon real armar o campo doesRequest dentro do proprio activate () e ainda assim
     * ganhar item de menu.
     *
     * doesAuto (), por outro lado, nao e perguntado nenhuma vez aqui - so em processAll.
     */
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

      assertEquals (List.of ("ScriptedPlugin.activate",        // select (), :470
                             "ScriptedPlugin.doesRequest->false",   // select (), :474
                             "ScriptedPlugin.doesRequest->false"),  // setMenu (), :250
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

  /*
   * A TRAVA DO doesRequest, que e a outra metade da razao pela qual o plano escrito do passo
   * 9 nao pode ser implementado como esta.
   *
   * doesRequest () NAO e perguntado a cada passo, ao contrario do doesAuto (). Sao dois
   * sitios, com vidas diferentes:
   *
   *   PluginEntry.select () :474 - guardado por requestMenuItem == null. Quem responde false
   *   na PRIMEIRA ativacao nunca ganha item de menu, por mais que responda true depois;
   *
   *   setMenu () :250 - decide se o item ja criado entra na secao de request do menu.
   *
   * E rebuildMenu (), que e o que roda a cada clique no menu, NAO PERGUNTA doesRequest () -
   * ele gateia em isActivated && requestMenuItem != null. Ou seja: depois da montagem, a
   * presenca do item depende do campo travado e nao mais da resposta do plugin.
   *
   * Isso importa porque doesRequest tambem muda durante a execucao - 10 transicoes vivas em
   * cinco dos seis plugins, e nenhum documento do projeto registra isso. O FanLogoff real
   * zera o proprio doesRequest no meio do fluxo (linha 115) e mesmo assim mantem o item.
   */
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

    /*
     * A TRAVA NAO E "na primeira ativacao", e esta medicao desmentiu tanto o plano escrito
     * quanto a expectativa que eu tinha escrito neste mesmo arquivo.
     *
     * O guarda e requestMenuItem == null, e ele e reavaliado em TODA chamada de select () -
     * nao so na primeira. Um plugin que responde false enquanto o item nao existe continua
     * sendo perguntado, e ganha o item na hora em que responder true, seja em que ativacao
     * for. Depois disso, nunca mais e perguntado por select ().
     *
     * E o item pode nascer numa DESATIVACAO: select (false) chama deactivate () e logo em
     * seguida cai no mesmo guarda. E o que este caso mostra - o item so aparece no menu na
     * religada, porque rebuildMenu () exige isActivated.
     */
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

    /*
     * A trava pelo outro lado: criado o item, ele volta ao menu num rebuild mesmo com
     * doesRequest () ja respondendo false - porque rebuildMenu () nao pergunta. O log prova
     * que nao pergunta: depois da montagem nao ha mais nenhum doesRequest.
     */
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

      // o toggle limpa o log antes de disparar, entao o que sobra e so a religada: nenhum
      // doesRequest, que e exatamente o ponto - rebuildMenu nao pergunta
      assertEquals (List.of ("ScriptedPlugin.activate"), PluginProbe.calls ());
    }

    /*
     * Desligar o plugin tira o item de request do menu, e religar traz de volta - pelo campo
     * isActivated, nao por nova consulta.
     */
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

    /*
     * Os aceleradores saem de uma lista fixa, na ordem em que os itens sao criados. A tecla
     * de atalho e medida do proprio toolkit, nunca suposta: e control no Windows e no Linux
     * e meta no macOS, e um teste que fixe uma das duas passa numa plataforma e quebra na
     * outra. O precedente e o helper shortcutIsMeta () do ConsoleKeyPressTest.
     */
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

    /*
     * getMenu () chamado duas vezes re-instancia todo plugin - instantiate () zera o campo e
     * constroi de novo -, entao activate () roda outra vez, num objeto NOVO, enquanto o
     * requestMenuItem sobrevive do anterior. Em producao getMenu () e chamado uma vez so, em
     * ConsolePane:107, entao isto e latente. Congelado aqui e registrado no backlog.
     *
     * E repare na contagem, que tambem desmentiu a expectativa escrita antes: na SEGUNDA
     * montagem doesRequest () e perguntado UMA vez, nao duas. O sitio de select () :474 sai
     * pelo curto-circuito, porque requestMenuItem ja nao e null; sobra o de setMenu () :250.
     * E a trava se mostrando de novo, agora pela reducao do numero de perguntas.
     */
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

      assertEquals (List.of ("ScriptedPlugin.activate",            // select (), :470
                             "ScriptedPlugin.doesRequest->true"),  // setMenu (), :250
                    PluginProbe.calls ());
    }
  }

  /*
   * O ISOLAMENTO DO processAll, e o que ele NAO isola.
   *
   * O laco de processAll envolve cada plugin num try/catch e loga "Error processing auto",
   * entao um plugin que quebra nao impede os seguintes. Mas o catch e de Exception, nao de
   * Throwable: um Error escapa do laco, cancela os plugins que faltavam e sobe ate
   * WriteCommand.process (), que e quem chama processPluginAuto () depois de destravar o
   * teclado.
   *
   * Os dois casos estao aqui juntos de proposito - o que se esta congelando e a FRONTEIRA
   * entre o que e isolado e o que nao e. O bloco 1 mexe neste laco.
   */
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

    /*
     * A assimetria. Nao ha nada a corrigir aqui sob a Regra 1 - o defeito vai para o
     * BACKLOG-DEFEITOS.md e o teste o congela como esta.
     */
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

  // ---------------------------------------------------------------------------------//
  //  Apoio
  // ---------------------------------------------------------------------------------//

  // ---------------------------------------------------------------------------------//
  private void register (int slot, String name, Class<?> type, boolean active)
  // ---------------------------------------------------------------------------------//
  {
    prefs.put (String.format ("PluginName-%02d", slot), name);
    prefs.put (String.format ("PluginClass-%02d", slot), type.getName ());
    prefs.putBoolean (String.format ("PluginActivate-%02d", slot), active);
  }

  /*
   * O construtor de uma Stage exige a thread do JavaFX. O PluginsStage le as Preferences e
   * monta o class loader aqui dentro, entao as posicoes tem de estar gravadas ANTES.
   */
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

  /*
   * Monta o menu e apaga o log, deixando os roteiros de pe: getMenu () instancia, ativa e ja
   * pergunta doesRequest () antes de o teste chegar ao que quer medir. Quem afirma essa
   * sequencia de montagem e o caso activateRodaNaMontagem, que nao limpa nada.
   */
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

  /*
   * Reproduz o CLIQUE inteiro num CheckMenuItem, e nao so o fire (). CheckMenuItem.fire ()
   * NAO inverte o selected - quem inverte e o skin do menu, antes de disparar a acao -, e
   * como itemSelected () le justamente isSelected (), um teste que chamasse so fire ()
   * afirmaria o contrario do que o usuario ve, e passaria. E a mesma armadilha que derrubou
   * tres casos do OptionStageTest de uma vez.
   */
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

  /*
   * O item de request e um MenuItem simples com o mesmo texto do CheckMenuItem que liga o
   * plugin - por isso a contagem por nome, e nao a busca pelo primeiro.
   */
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

  // ---------------------------------------------------------------------------------//
  private static PluginData data (int sequence)
  // ---------------------------------------------------------------------------------//
  {
    return new PluginData (sequence, new ScreenLocation (0), new ArrayList<> ());
  }
}
