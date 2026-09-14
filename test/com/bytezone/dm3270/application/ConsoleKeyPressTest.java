package com.bytezone.dm3270.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.display.HeadlessScreenTarget;
import com.bytezone.dm3270.screen.Cursor;
import com.bytezone.dm3270.screen.KeyboardTarget;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.testing.JavaFxToolkit;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/*
 * A rede do ConsoleKeyPress, que ate este commit tinha ZERO testes - 213 linhas de despacho,
 * seis guardas sequenciais e quatro switch, no caminho por onde passa cada tecla que o usuario
 * aperta num terminal 3270.
 *
 * COMO ELE ALCANCA A CLASSE, e por que assim. O precedente e o OptionStageTest: um teste de
 * janela que le os campos da classe vira refem do refactor que deveria vigiar. Aqui o
 * equivalente e nao tocar em nada de dentro do ConsoleKeyPress - nem no handle, nem nos campos,
 * nem nas tabelas que ele vai ganhar no proximo commit. Ele monta um KeyEvent, chama handle e
 * afirma DUAS coisas: o que os colaboradores receberam, na ordem, e se o evento foi consumido.
 * Os dois lados sao comportamento observavel; as tabelas de despacho sao detalhe.
 *
 * O LOG COMPARTILHADO. Os tres dubles escrevem na MESMA lista, e por isso cada caso afirma
 * tambem a ORDEM entre colaboradores diferentes - que a selecao e limpa ANTES de o AID ser
 * mandado, por exemplo. Uma lista por duble perderia exatamente isso.
 *
 * POR QUE PRECISA DO TOOLKIT. O CLAUDE.md dizia que um KeyEvent se monta a mao sem toolkit.
 * Montar, sim. Mas isShortcutDown () chama com.sun.javafx.tk.Toolkit.getToolkit () - conferido
 * com javap no javafx-graphics-21.0.7 - e ela e a PRIMEIRA guarda do handle. Todo caso deste
 * arquivo passa por ela, entao a extensao nao e opcional.
 *
 * A PLATAFORMA E CARGA UTIL. isShortcutDown () e controlDown no Windows e no Linux, e metaDown
 * no macOS. Como a guarda de copiar e colar roda antes das de Meta e de Control e retorna para
 * toda tecla que nao seja C ou V, em cada plataforma um dos dois blocos e inalcancavel: o Meta
 * inteiro no macOS, o Ctrl+H no Windows e no Linux. E o item 17 do BACKLOG-DEFEITOS.md, e este
 * arquivo o CONGELA em vez de esconde-lo - as asserticoes ramificam por SHORTCUT_IS_META, que e
 * medido do proprio toolkit. Um teste que fixasse um dos dois passaria numa plataforma e
 * quebraria na outra.
 */
// -----------------------------------------------------------------------------------//
@ExtendWith (JavaFxToolkit.class)
@DisplayName ("ConsoleKeyPress - o despacho de tecla do terminal")
class ConsoleKeyPressTest
// -----------------------------------------------------------------------------------//
{
  private static final boolean SHORTCUT_IS_META = shortcutIsMeta ();

  private final List<String> log = new ArrayList<> ();

  private RecordingConsole console;
  private RecordingScreen screen;
  private ConsoleKeyPress keyPress;

  // ---------------------------------------------------------------------------------//
  @BeforeEach
  void buildHandler ()
  // ---------------------------------------------------------------------------------//
  {
    log.clear ();
    console = new RecordingConsole (log);
    screen = new RecordingScreen (log);
    keyPress = new ConsoleKeyPress (console, screen);
  }

  // ---------------------------------------------------------------------------------//
  //  Os eventos
  // ---------------------------------------------------------------------------------//

  // ---------------------------------------------------------------------------------//
  private static KeyEvent press (KeyCode code, boolean shift, boolean control, boolean alt,
      boolean meta)
  // ---------------------------------------------------------------------------------//
  {
    return new KeyEvent (KeyEvent.KEY_PRESSED, "", "", code, shift, control, alt, meta);
  }

  // ---------------------------------------------------------------------------------//
  private static KeyEvent plain (KeyCode code)
  // ---------------------------------------------------------------------------------//
  {
    return press (code, false, false, false, false);
  }

  // ---------------------------------------------------------------------------------//
  private static KeyEvent withShift (KeyCode code)
  // ---------------------------------------------------------------------------------//
  {
    return press (code, true, false, false, false);
  }

  // ---------------------------------------------------------------------------------//
  private static KeyEvent withMeta (KeyCode code)
  // ---------------------------------------------------------------------------------//
  {
    return press (code, false, false, false, true);
  }

  // ---------------------------------------------------------------------------------//
  private static KeyEvent withControl (KeyCode code)
  // ---------------------------------------------------------------------------------//
  {
    return press (code, false, true, false, false);
  }

  /*
   * A tecla de atalho da plataforma: Cmd no macOS, Ctrl no resto. E o que o usuario aperta para
   * copiar e colar, e e por isso que ela colide com um dos dois blocos de modificador.
   */
  // ---------------------------------------------------------------------------------//
  private static KeyEvent withShortcut (KeyCode code)
  // ---------------------------------------------------------------------------------//
  {
    return SHORTCUT_IS_META ? withMeta (code) : withControl (code);
  }

  // isShortcutDown () chama Toolkit.getToolkit (), entao o toolkit tem de estar de pe antes -
  // este metodo roda no inicializador estatico, ANTES do beforeAll da extensao.
  // ---------------------------------------------------------------------------------//
  private static boolean shortcutIsMeta ()
  // ---------------------------------------------------------------------------------//
  {
    JavaFxToolkit.start ();
    return withMeta (KeyCode.A).isShortcutDown ();
  }

  // ---------------------------------------------------------------------------------//
  //  As asserticoes
  // ---------------------------------------------------------------------------------//

  // ---------------------------------------------------------------------------------//
  private void assertConsumed (KeyEvent event, String... calls)
  // ---------------------------------------------------------------------------------//
  {
    keyPress.handle (event);
    assertEquals (List.of (calls), log, "as chamadas aos colaboradores, na ordem");
    assertTrue (event.isConsumed (), "este caminho consome o evento");
  }

  /*
   * Nem todo caminho tratado consome, e isso e comportamento observavel: o evento segue para
   * quem estiver ouvindo depois. Afirmar so a acao deixaria passar um consume () esquecido.
   */
  // ---------------------------------------------------------------------------------//
  private void assertNotConsumed (KeyEvent event, String... calls)
  // ---------------------------------------------------------------------------------//
  {
    keyPress.handle (event);
    assertEquals (List.of (calls), log, "as chamadas aos colaboradores, na ordem");
    assertFalse (event.isConsumed (), "este caminho NAO consome - ver o mapa do handle");
  }

  // No macOS a guarda de atalho engole o bloco Meta inteiro. Item 17 do backlog.
  // ---------------------------------------------------------------------------------//
  private void assertMetaBinding (KeyCode code, String expected)
  // ---------------------------------------------------------------------------------//
  {
    if (SHORTCUT_IS_META)
      assertNotConsumed (withMeta (code), "clearSelection");
    else
      assertConsumed (withMeta (code), "clearSelection", expected);
  }

  // No Windows e no Linux a guarda de atalho engole o Ctrl+H. Item 17 do backlog.
  // ---------------------------------------------------------------------------------//
  private void assertControlBinding (KeyCode code, String expected)
  // ---------------------------------------------------------------------------------//
  {
    if (SHORTCUT_IS_META)
      assertConsumed (withControl (code), "clearSelection", expected);
    else
      assertNotConsumed (withControl (code), "clearSelection");
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("antes de qualquer despacho")
  class AntesDoDespacho
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("um evento que nao e KEY_PRESSED sai sem tocar em nada e sem consumir")
    void soTrataKeyPressed ()
    // -------------------------------------------------------------------------------//
    {
      KeyEvent released = new KeyEvent (KeyEvent.KEY_RELEASED, "", "", KeyCode.ENTER, false,
          false, false, false);

      assertNotConsumed (released);
    }

    /*
     * A guarda da linha 67 e !isModifierKey (): apertar Ctrl sozinho NAO limpa a selecao. Se
     * limpasse, segurar Ctrl para clicar apagaria a selecao que o usuario acabou de fazer.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("um modificador sozinho nao limpa a selecao nem consome")
    void oModificadorSozinhoNaoLimpaASelecao ()
    // -------------------------------------------------------------------------------//
    {
      assertNotConsumed (plain (KeyCode.CONTROL));
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("toda tecla nao-modificadora limpa a selecao antes de ser despachada")
    void aSelecaoELimpaAntesDoDespacho ()
    // -------------------------------------------------------------------------------//
    {
      assertConsumed (plain (KeyCode.ENTER), "clearSelection",
          "sendAID ENTR " + AIDCommand.AID_ENTER);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("a guarda de atalho - copiar e colar")
  class Atalhos
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("atalho+C copia a selecao")
    void copia ()
    // -------------------------------------------------------------------------------//
    {
      assertConsumed (withShortcut (KeyCode.C), "copySelection");
    }

    // limpa ANTES de colar: o texto colado nao pode herdar o realce da selecao anterior
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("atalho+V limpa a selecao e so entao cola")
    void cola ()
    // -------------------------------------------------------------------------------//
    {
      assertConsumed (withShortcut (KeyCode.V), "clearSelection", "pasteText");
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("o modificador de atalho sozinho nao faz nada")
    void oModificadorDeAtalhoSozinho ()
    // -------------------------------------------------------------------------------//
    {
      KeyCode modifier = SHORTCUT_IS_META ? KeyCode.META : KeyCode.CONTROL;
      assertNotConsumed (withShortcut (modifier));
    }

    /*
     * Correcao 2 do mapa do handle: este caminho muda estado e NAO consome. Um despacho por
     * tabela que consumisse tudo o quebraria, e o atalho deixaria de chegar a quem ouve depois.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("qualquer outro atalho limpa a selecao e NAO consome")
    void outroAtalhoNaoConsome ()
    // -------------------------------------------------------------------------------//
    {
      assertNotConsumed (withShortcut (KeyCode.X), "clearSelection");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("o modo historico - teclado travado")
  class TecladoTravado
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @BeforeEach
    void travarOTeclado ()
    // -------------------------------------------------------------------------------//
    {
      screen.locked = true;
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("a seta para a esquerda volta uma tela")
    void esquerdaVolta ()
    // -------------------------------------------------------------------------------//
    {
      assertConsumed (plain (KeyCode.LEFT), "clearSelection", "back");
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("a seta para a direita avanca uma tela")
    void direitaAvanca ()
    // -------------------------------------------------------------------------------//
    {
      assertConsumed (plain (KeyCode.RIGHT), "clearSelection", "forward");
    }

    /*
     * A guarda do teclado travado retorna SEMPRE, e engole todo o resto sem consumir. E isto
     * que uma tabela unica de KeyCombination nao consegue exprimir: "neste modo, nada mais
     * vale".
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("todo o resto e engolido, sem consumir")
    void oRestoEEngolido ()
    // -------------------------------------------------------------------------------//
    {
      assertNotConsumed (plain (KeyCode.ENTER), "clearSelection");
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("o travamento vem antes das setas normais")
    void oTravamentoVemAntesDasSetas ()
    // -------------------------------------------------------------------------------//
    {
      assertNotConsumed (plain (KeyCode.UP), "clearSelection");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("o bloco Meta - inalcancavel no macOS, item 17 do backlog")
  class Meta
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("meta+ENTER insere uma linha")
    void metaEnter ()
    // -------------------------------------------------------------------------------//
    {
      assertMetaBinding (KeyCode.ENTER, "newLine");
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("meta+BACK_SPACE apaga ate o fim da linha")
    void metaBackSpace ()
    // -------------------------------------------------------------------------------//
    {
      assertMetaBinding (KeyCode.BACK_SPACE, "eraseEOL");
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("meta+DELETE apaga ate o fim da linha")
    void metaDelete ()
    // -------------------------------------------------------------------------------//
    {
      assertMetaBinding (KeyCode.DELETE, "eraseEOL");
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("meta+H volta ao inicio")
    void metaH ()
    // -------------------------------------------------------------------------------//
    {
      assertMetaBinding (KeyCode.H, "home");
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("meta+I alterna o modo de insercao")
    void metaI ()
    // -------------------------------------------------------------------------------//
    {
      assertMetaBinding (KeyCode.I, "toggleInsertMode");
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("meta+F1 e a tecla PA1")
    void metaF1 ()
    // -------------------------------------------------------------------------------//
    {
      assertMetaBinding (KeyCode.F1, "sendAID PA1 " + AIDCommand.AID_PA1);
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("meta+F2 e a tecla PA2")
    void metaF2 ()
    // -------------------------------------------------------------------------------//
    {
      assertMetaBinding (KeyCode.F2, "sendAID PA2 " + AIDCommand.AID_PA2);
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("meta+F3 e a tecla PA3")
    void metaF3 ()
    // -------------------------------------------------------------------------------//
    {
      assertMetaBinding (KeyCode.F3, "sendAID PA3 " + AIDCommand.AID_PA3);
    }

    // o default do switch de Meta retorna sem consumir
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("meta+tecla sem binding nao faz nada e nao consome")
    void metaSemBinding ()
    // -------------------------------------------------------------------------------//
    {
      assertNotConsumed (withMeta (KeyCode.X), "clearSelection");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("o bloco Control - inalcancavel no Windows e no Linux, item 17 do backlog")
  class Control
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("ctrl+H volta ao inicio")
    void controlH ()
    // -------------------------------------------------------------------------------//
    {
      assertControlBinding (KeyCode.H, "home");
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("ctrl+tecla sem binding nao faz nada e nao consome")
    void controlSemBinding ()
    // -------------------------------------------------------------------------------//
    {
      assertNotConsumed (withControl (KeyCode.Z), "clearSelection");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("as setas")
  class Setas
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @ParameterizedTest (name = "{0} move o cursor para {1}")
    @CsvSource ({ "LEFT,LEFT", "RIGHT,RIGHT", "UP,UP", "DOWN,DOWN" })
    void asQuatroSetasMovemOCursor (String key, String direction)
    // -------------------------------------------------------------------------------//
    {
      assertConsumed (plain (KeyCode.valueOf (key)), "clearSelection", "move " + direction);
    }

    /*
     * Item 16 do BACKLOG-DEFEITOS.md. KeyCode.isArrowKey () e (mask & 4) != 0, e KP_LEFT tem o
     * bit ligado tanto quanto LEFT - medido com javap no javafx-graphics-21.0.7. Entao a seta
     * do teclado numerico PASSA pela guarda, ERRA os quatro case e cai no default, que loga
     * "Impossible arrow key" e nao consome. O cursor nao se move.
     *
     * O aviso no log e o terceiro efeito disto, e nao esta afirmado aqui - o que esta afirmado
     * sao os dois que o usuario ve: o cursor parado e o evento seguindo adiante.
     */
    // -------------------------------------------------------------------------------//
    @ParameterizedTest (name = "{0} do teclado numerico nao move o cursor e nao consome")
    @CsvSource ({ "KP_LEFT", "KP_RIGHT", "KP_UP", "KP_DOWN" })
    void asSetasDoTecladoNumericoNaoMovemNada (String key)
    // -------------------------------------------------------------------------------//
    {
      assertNotConsumed (plain (KeyCode.valueOf (key)), "clearSelection");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("as teclas sem modificador")
  class TeclasSimples
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("ENTER manda o AID de ENTER")
    void enter ()
    // -------------------------------------------------------------------------------//
    {
      assertConsumed (plain (KeyCode.ENTER), "clearSelection",
          "sendAID ENTR " + AIDCommand.AID_ENTER);
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("shift+ENTER insere uma linha, e vem antes do bloco Control")
    void shiftEnter ()
    // -------------------------------------------------------------------------------//
    {
      assertConsumed (withShift (KeyCode.ENTER), "clearSelection", "newLine");
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("TAB avanca de campo")
    void tab ()
    // -------------------------------------------------------------------------------//
    {
      assertConsumed (plain (KeyCode.TAB), "clearSelection", "tab false");
    }

    // o shift nao e um binding separado: entra como ARGUMENTO do tab
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("shift+TAB volta de campo")
    void shiftTab ()
    // -------------------------------------------------------------------------------//
    {
      assertConsumed (withShift (KeyCode.TAB), "clearSelection", "tab true");
    }

    /*
     * Correcao 1 do mapa do handle. BACK_SPACE, DELETE e END sao TRES metodos diferentes, e so
     * COM Meta e que os dois primeiros viram eraseEOL. Uma tabela que unificasse os tres pelo
     * nome da tecla trocaria "apagar um caractere" por "apagar ate o fim da linha".
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("BACK_SPACE apaga UM caractere, nao a linha")
    void backSpace ()
    // -------------------------------------------------------------------------------//
    {
      assertConsumed (plain (KeyCode.BACK_SPACE), "clearSelection", "backspace");
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("DELETE apaga UM caractere, nao a linha")
    void delete ()
    // -------------------------------------------------------------------------------//
    {
      assertConsumed (plain (KeyCode.DELETE), "clearSelection", "delete");
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("END apaga ate o fim da linha")
    void end ()
    // -------------------------------------------------------------------------------//
    {
      assertConsumed (plain (KeyCode.END), "clearSelection", "eraseEOL");
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("INSERT alterna o modo de insercao")
    void insert ()
    // -------------------------------------------------------------------------------//
    {
      assertConsumed (plain (KeyCode.INSERT), "clearSelection", "toggleInsertMode");
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("HOME volta ao inicio")
    void home ()
    // -------------------------------------------------------------------------------//
    {
      assertConsumed (plain (KeyCode.HOME), "clearSelection", "home");
    }

    // consome e nao chama colaborador nenhum: so o logger.debug ("escape"), com o comentario
    // "// CLR key?" que registra uma duvida antiga
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("ESCAPE consome e nao faz mais nada")
    void escape ()
    // -------------------------------------------------------------------------------//
    {
      assertConsumed (plain (KeyCode.ESCAPE), "clearSelection");
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("uma tecla sem binding so limpa a selecao, e nao consome")
    void semBinding ()
    // -------------------------------------------------------------------------------//
    {
      assertNotConsumed (plain (KeyCode.A), "clearSelection");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("as teclas de funcao - PF1 a PF24")
  class TeclasDeFuncao
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @ParameterizedTest (name = "{0} e a tecla {1}")
    @CsvSource ({ "F1,PF1", "F2,PF2", "F3,PF3", "F4,PF4", "F5,PF5", "F6,PF6", "F7,PF7",
                  "F8,PF8", "F9,PF9", "F10,PF10", "F11,PF11", "F12,PF12" })
    void asDozeTeclasDeFuncao (String key, String name)
    // -------------------------------------------------------------------------------//
    {
      assertConsumed (plain (KeyCode.valueOf (key)), "clearSelection",
          "sendAID " + name + " " + AIDCommand.getKey (name));
    }

    // o shift soma 12 ao numero, e e assim que PF13 a PF24 existem num teclado de 12 teclas
    // -------------------------------------------------------------------------------//
    @ParameterizedTest (name = "shift+{0} e a tecla {1}")
    @CsvSource ({ "F1,PF13", "F2,PF14", "F3,PF15", "F4,PF16", "F5,PF17", "F6,PF18",
                  "F7,PF19", "F8,PF20", "F9,PF21", "F10,PF22", "F11,PF23", "F12,PF24" })
    void asDozeComShift (String key, String name)
    // -------------------------------------------------------------------------------//
    {
      assertConsumed (withShift (KeyCode.valueOf (key)), "clearSelection",
          "sendAID " + name + " " + AIDCommand.getKey (name));
    }
  }

  /*
   * Quatro acoes sao alcancaveis por mais de uma combinacao, e e isto que um despacho por
   * tabela precisa preservar sem fundir. Cada caso abaixo dispara TODAS as combinacoes e conta
   * quantas vezes a acao saiu - se alguem fundir dois bindings num so, a conta muda.
   *
   * As contas sao MENORES do que os bindings escritos no arquivo, e isso e o item 17: em cada
   * plataforma uma das combinacoes de modificador esta morta.
   */
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("as quatro acoes repetidas")
  class AcoesRepetidas
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    private long count (String action, KeyEvent... events)
    // -------------------------------------------------------------------------------//
    {
      for (KeyEvent event : events)
        keyPress.handle (event);

      return log.stream ().filter (action::equals).count ();
    }

    // meta+H, ctrl+H e HOME - e um dos dois primeiros esta morto, conforme a plataforma
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("home sai de tres combinacoes, das quais uma morre na plataforma")
    void home ()
    // -------------------------------------------------------------------------------//
    {
      assertEquals (2, count ("home", withMeta (KeyCode.H), withControl (KeyCode.H),
          plain (KeyCode.HOME)), "o item 17 mata uma das tres, e sempre a mesma por plataforma");
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("eraseEOL sai de tres combinacoes")
    void eraseEOL ()
    // -------------------------------------------------------------------------------//
    {
      long expected = SHORTCUT_IS_META ? 1 : 3;
      assertEquals (expected, count ("eraseEOL", withMeta (KeyCode.BACK_SPACE),
          withMeta (KeyCode.DELETE), plain (KeyCode.END)));
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("newLine sai de duas combinacoes")
    void newLine ()
    // -------------------------------------------------------------------------------//
    {
      long expected = SHORTCUT_IS_META ? 1 : 2;
      assertEquals (expected,
          count ("newLine", withMeta (KeyCode.ENTER), withShift (KeyCode.ENTER)));
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("toggleInsertMode sai de duas combinacoes")
    void toggleInsertMode ()
    // -------------------------------------------------------------------------------//
    {
      long expected = SHORTCUT_IS_META ? 1 : 2;
      assertEquals (expected,
          count ("toggleInsertMode", withMeta (KeyCode.I), plain (KeyCode.INSERT)));
    }
  }

  // ---------------------------------------------------------------------------------//
  //  Os dubles. Os tres escrevem na MESMA lista, e por isso a ordem entre eles e afirmavel.
  // ---------------------------------------------------------------------------------//

  // ---------------------------------------------------------------------------------//
  private static final class RecordingConsole implements ConsoleKeyTarget
  // ---------------------------------------------------------------------------------//
  {
    private final List<String> log;

    RecordingConsole (List<String> log)
    {
      this.log = log;
    }

    @Override
    public void sendAID (byte aid, String name)
    {
      log.add ("sendAID " + name + " " + aid);
    }

    @Override
    public void sendAID (AIDCommand command)
    {
      log.add ("sendAID command");
    }

    @Override
    public void back ()
    {
      log.add ("back");
    }

    @Override
    public void forward ()
    {
      log.add ("forward");
    }
  }

  // ---------------------------------------------------------------------------------//
  private static final class RecordingScreen implements KeyboardTarget
  // ---------------------------------------------------------------------------------//
  {
    private final List<String> log;
    private final Cursor cursor;

    boolean locked;

    RecordingScreen (List<String> log)
    {
      this.log = log;
      this.cursor = new RecordingCursor (log);
    }

    @Override
    public boolean isKeyboardLocked ()
    {
      return locked;
    }

    @Override
    public Cursor getScreenCursor ()
    {
      return cursor;
    }

    @Override
    public void clearSelection ()
    {
      log.add ("clearSelection");
    }

    @Override
    public void copySelection ()
    {
      log.add ("copySelection");
    }

    @Override
    public void pasteText ()
    {
      log.add ("pasteText");
    }

    @Override
    public void toggleInsertMode ()
    {
      log.add ("toggleInsertMode");
    }
  }

  /*
   * Cursor e classe, nao interface, e nenhum dos sete metodos e final - entao o duble e uma
   * subclasse que grava em vez de executar. E de proposito que ele NAO executa: esta rede
   * congela o DESPACHO, e a semantica do cursor ja tem o CursorTest, com 32 casos.
   *
   * O construtor da superclasse so guarda dois campos, mas recebe colaboradores de verdade -
   * um HeadlessScreenTarget e as dimensoes padrao - para que o duble quebre alto se algum dia
   * ele passar a usa-los.
   */
  // ---------------------------------------------------------------------------------//
  private static final class RecordingCursor extends Cursor
  // ---------------------------------------------------------------------------------//
  {
    private final List<String> log;

    RecordingCursor (List<String> log)
    {
      super (new HeadlessScreenTarget (), new ScreenDimensions (24, 80));
      this.log = log;
    }

    @Override
    public void newLine ()
    {
      log.add ("newLine");
    }

    @Override
    public void eraseEOL ()
    {
      log.add ("eraseEOL");
    }

    @Override
    public void home ()
    {
      log.add ("home");
    }

    @Override
    public void move (Direction direction)
    {
      log.add ("move " + direction);
    }

    @Override
    public void tab (boolean backTab)
    {
      log.add ("tab " + backTab);
    }

    @Override
    public void backspace ()
    {
      log.add ("backspace");
    }

    @Override
    public void delete ()
    {
      log.add ("delete");
    }
  }
}
