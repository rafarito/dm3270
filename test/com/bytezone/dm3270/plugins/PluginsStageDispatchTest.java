package com.bytezone.dm3270.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

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

  // ---------------------------------------------------------------------------------//
  private static PluginData data (int sequence)
  // ---------------------------------------------------------------------------------//
  {
    return new PluginData (sequence, new ScreenLocation (0), new ArrayList<> ());
  }
}
