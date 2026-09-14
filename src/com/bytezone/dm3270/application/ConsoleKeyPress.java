package com.bytezone.dm3270.application;

import java.util.EnumMap;
import java.util.Map;

import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.screen.Cursor;
import com.bytezone.dm3270.screen.Cursor.Direction;
import com.bytezone.dm3270.screen.KeyboardTarget;

import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Uma tabela de despacho POR MODO, e nao uma tabela so.
 *
 * O plano deste passo pedia um Map<KeyCombination, TerminalAction> unico. A medicao mostrou que
 * nao serve, porque tres coisas deste tratador sao comportamento observavel e um mapa global
 * nao exprime nenhuma delas:
 *
 *   1. a ORDEM das guardas. O atalho decide antes dos dois blocos de modificador, o teclado
 *      travado antes de todo o resto, e o shift+ENTER antes do bloco Control;
 *
 *   2. o ENGOLIMENTO do modo historico. Com o teclado travado so LEFT e RIGHT fazem algo, e
 *      todo o resto retorna sem consumir. Nao ha como dizer "neste modo, nada mais vale" numa
 *      tabela plana;
 *
 *   3. os FALLBACKS, que sao cinco coisas diferentes: o default vazio do Meta e do Control, o
 *      logger.warn do bloco das setas, a varredura de teclas de funcao do modo normal e o
 *      caminho de atalho nao mapeado, que limpa a selecao e NAO consome.
 *
 * Dai a forma: a cadeia de guardas continua escrita como cadeia, cada modo tem a sua tabela, e
 * o fallback de cada modo fica ao lado dela, fora da tabela. Dentro de uma tabela a regra e
 * uniforme e por isso o consume () saiu das acoes e foi para o dispatch: TODA tecla mapeada
 * consome, em todos os modos. Quem nao consome e sempre um fallback.
 *
 * As quatro acoes alcancaveis por mais de uma combinacao - home por tres, eraseEOL por tres,
 * newLine por duas e toggleInsertMode por duas - sao a MESMA KeyAction referenciada de tabelas
 * diferentes, nunca bindings fundidos. E o que o ConsoleKeyPressTest conta, caso a caso.
 */
class ConsoleKeyPress implements EventHandler<KeyEvent>
{
  private static final Logger logger = LoggerFactory.getLogger (ConsoleKeyPress.class);
  private static final KeyCode[] PFKeyCodes =
      { KeyCode.F1, KeyCode.F2, KeyCode.F3, KeyCode.F4, KeyCode.F5, KeyCode.F6,
        KeyCode.F7, KeyCode.F8, KeyCode.F9, KeyCode.F10, KeyCode.F11, KeyCode.F12 };

  private final KeyboardTarget screen;
  private final ConsoleKeyTarget consolePane;
  private final Cursor cursor;

  private final Map<KeyCode, KeyAction> shortcutBindings = new EnumMap<> (KeyCode.class);
  private final Map<KeyCode, KeyAction> historyBindings = new EnumMap<> (KeyCode.class);
  private final Map<KeyCode, KeyAction> metaBindings = new EnumMap<> (KeyCode.class);
  private final Map<KeyCode, KeyAction> controlBindings = new EnumMap<> (KeyCode.class);
  private final Map<KeyCode, KeyAction> arrowBindings = new EnumMap<> (KeyCode.class);
  private final Map<KeyCode, KeyAction> plainBindings = new EnumMap<> (KeyCode.class);

  /*
   * A acao recebe o evento porque duas delas leem o shift DE DENTRO: o TAB, que o passa como
   * argumento para cursor.tab (boolean), e a varredura de teclas de funcao, que soma 12 ao
   * numero. Um Runnable nao daria conta das duas.
   */
  @FunctionalInterface
  private interface KeyAction
  {
    void perform (KeyEvent keyEvent);
  }

  public ConsoleKeyPress (ConsoleKeyTarget consolePane, KeyboardTarget screen)
  {
    this.consolePane = consolePane;
    this.screen = screen;
    this.cursor = screen.getScreenCursor ();

    // as quatro acoes que aparecem em mais de um binding, cada uma declarada UMA vez
    KeyAction home = keyEvent -> cursor.home ();
    KeyAction eraseEOL = keyEvent -> cursor.eraseEOL ();
    KeyAction newLine = keyEvent -> cursor.newLine ();
    KeyAction toggleInsertMode = keyEvent -> screen.toggleInsertMode ();

    shortcutBindings.put (KeyCode.C, keyEvent -> screen.copySelection ());
    shortcutBindings.put (KeyCode.V, keyEvent ->                  // limpa ANTES de colar
    {
      screen.clearSelection ();
      screen.pasteText ();
    });

    historyBindings.put (KeyCode.LEFT, keyEvent -> consolePane.back ());
    historyBindings.put (KeyCode.RIGHT, keyEvent -> consolePane.forward ());

    metaBindings.put (KeyCode.ENTER, newLine);
    metaBindings.put (KeyCode.BACK_SPACE, eraseEOL);
    metaBindings.put (KeyCode.DELETE, eraseEOL);
    metaBindings.put (KeyCode.H, home);      // OSX ctrl-h conflicts with Hide Windows command
    metaBindings.put (KeyCode.I, toggleInsertMode);
    metaBindings.put (KeyCode.F1, sendAID (AIDCommand.AID_PA1, "PA1"));
    metaBindings.put (KeyCode.F2, sendAID (AIDCommand.AID_PA2, "PA2"));
    metaBindings.put (KeyCode.F3, sendAID (AIDCommand.AID_PA3, "PA3"));

    controlBindings.put (KeyCode.H, home);                     // OSX has to share ctrl-h

    arrowBindings.put (KeyCode.LEFT, keyEvent -> cursor.move (Direction.LEFT));
    arrowBindings.put (KeyCode.RIGHT, keyEvent -> cursor.move (Direction.RIGHT));
    arrowBindings.put (KeyCode.UP, keyEvent -> cursor.move (Direction.UP));
    arrowBindings.put (KeyCode.DOWN, keyEvent -> cursor.move (Direction.DOWN));

    plainBindings.put (KeyCode.ENTER, sendAID (AIDCommand.AID_ENTER, "ENTR"));
    plainBindings.put (KeyCode.TAB, keyEvent -> cursor.tab (keyEvent.isShiftDown ()));
    plainBindings.put (KeyCode.BACK_SPACE, keyEvent -> cursor.backspace ());  // UM caractere
    plainBindings.put (KeyCode.DELETE, keyEvent -> cursor.delete ());         // UM caractere
    plainBindings.put (KeyCode.END, eraseEOL);                       // a linha toda, esta sim
    plainBindings.put (KeyCode.INSERT, toggleInsertMode);
    plainBindings.put (KeyCode.HOME, home);
    plainBindings.put (KeyCode.ESCAPE, keyEvent -> logger.debug ("escape"));      // CLR key?
  }

  // ---------------------------------------------------------------------------------//
  //  A cadeia de guardas
  // ---------------------------------------------------------------------------------//

  @Override
  public void handle (KeyEvent keyEvent)
  {
    if (keyEvent.getEventType () != KeyEvent.KEY_PRESSED)
      return;

    KeyCode keyCodePressed = keyEvent.getCode ();

    // Handle copy/paste shortcuts before clearing selection
    if (keyEvent.isShortcutDown ())
    {
      handleShortcut (keyEvent, keyCodePressed);
      return;
    }

    // Clear selection for all other non-modifier key presses
    if (!keyCodePressed.isModifierKey ())
      screen.clearSelection ();

    if (screen.isKeyboardLocked ())           // could be in screen history mode
    {
      dispatch (historyBindings, keyEvent, keyCodePressed);
      return;
    }

    if (keyEvent.isMetaDown ())
    {
      dispatch (metaBindings, keyEvent, keyCodePressed);
      return;
    }

    if (keyEvent.isShiftDown () && keyCodePressed == KeyCode.ENTER)
    {
      cursor.newLine ();
      keyEvent.consume ();
      return;
    }

    if (keyEvent.isControlDown ())
    {
      dispatch (controlBindings, keyEvent, keyCodePressed);
      return;
    }

    if (keyCodePressed.isArrowKey ())
      handleArrowKey (keyEvent, keyCodePressed);
    else
      handlePlainKey (keyEvent, keyCodePressed);
  }

  /*
   * Devolve true se a tecla tinha binding neste modo. Todo binding consome; os caminhos que NAO
   * consomem sao os fallbacks, e ficam fora da tabela de proposito.
   */
  private boolean dispatch (Map<KeyCode, KeyAction> bindings, KeyEvent keyEvent,
      KeyCode keyCodePressed)
  {
    KeyAction action = bindings.get (keyCodePressed);
    if (action == null)
      return false;

    action.perform (keyEvent);
    keyEvent.consume ();
    return true;
  }

  // ---------------------------------------------------------------------------------//
  //  Os tres modos que tem fallback com comportamento proprio
  // ---------------------------------------------------------------------------------//

  private void handleShortcut (KeyEvent keyEvent, KeyCode keyCodePressed)
  {
    // Ignore modifier keys pressed alone (Ctrl, Meta, etc.)
    if (keyCodePressed.isModifierKey ())
      return;

    if (dispatch (shortcutBindings, keyEvent, keyCodePressed))
      return;

    // For other shortcut combos, clear selection - e NAO consome, o evento segue adiante
    screen.clearSelection ();
  }

  /*
   * O aviso nao e impossivel, ao contrario do que diz: KeyCode.isArrowKey () e (mask & 4) != 0,
   * e KP_UP tem o bit ligado tanto quanto UP. As quatro setas do teclado numerico, com NumLock
   * desligado, chegam aqui - nao movem o cursor, nao consomem e logam. Item 16 do
   * BACKLOG-DEFEITOS.md, preservado.
   */
  private void handleArrowKey (KeyEvent keyEvent, KeyCode keyCodePressed)
  {
    if (!dispatch (arrowBindings, keyEvent, keyCodePressed))
      logger.warn ("Impossible arrow key");
  }

  private void handlePlainKey (KeyEvent keyEvent, KeyCode keyCodePressed)
  {
    if (dispatch (plainBindings, keyEvent, keyCodePressed))
      return;

    sendProgramFunctionKey (keyEvent, keyCodePressed);
  }

  /*
   * PF1 a PF24 num teclado de doze teclas: a posicao no vetor da o numero, e o shift soma 12.
   * Fica fora da tabela porque nao e um binding por tecla, e sim uma varredura - e porque uma
   * tecla que nao esteja no vetor nao faz nada NEM consome.
   */
  private void sendProgramFunctionKey (KeyEvent keyEvent, KeyCode keyCodePressed)
  {
    int pfKey = 1;

    for (KeyCode keyCode : PFKeyCodes)
    {
      if (keyCode == keyCodePressed)
      {
        if (keyEvent.isShiftDown ())
          pfKey += 12;

        String keyName = "PF" + pfKey;
        consolePane.sendAID (AIDCommand.getKey (keyName), keyName);
        keyEvent.consume ();
        return;
      }
      ++pfKey;
    }
  }

  private KeyAction sendAID (byte aid, String name)
  {
    return keyEvent -> consolePane.sendAID (aid, name);
  }
}
