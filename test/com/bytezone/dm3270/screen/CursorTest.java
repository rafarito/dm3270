package com.bytezone.dm3270.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.display.HeadlessScreenTarget;
import com.bytezone.dm3270.orders.BufferAddress;
import com.bytezone.dm3270.screen.Cursor.Direction;

// -----------------------------------------------------------------------------------//
@DisplayName ("Cursor - comportamento congelado")
class CursorTest
// -----------------------------------------------------------------------------------//
{
  private static final byte ERASE_WRITE = 0x05;
  private static final byte SBA = 0x11;
  private static final byte SF = 0x1D;

  private static final byte WCC_RESET_KEYBOARD = (byte) 0xC2;

  private static final int UNPROTECTED = 0x00;
  private static final int PROTECTED = 0x20;

  private static final int ROWS = 24;
  private static final int COLUMNS = 80;
  private static final int SIZE = ROWS * COLUMNS;

  private HeadlessScreenTarget screen;
  private Cursor cursor;

  // ---------------------------------------------------------------------------------//
  @BeforeEach
  void setUp ()
  // ---------------------------------------------------------------------------------//
  {
    screen = new HeadlessScreenTarget (new ScreenDimensions (ROWS, COLUMNS),
        new ScreenDimensions (43, COLUMNS));
    cursor = screen.getScreenCursor ();
  }

  // ---------------------------------------------------------------------------------//
  private void process (int... values)
  // ---------------------------------------------------------------------------------//
  {
    byte[] buffer = new byte[values.length];
    for (int i = 0; i < values.length; i++)
      buffer[i] = (byte) values[i];
    Command.getCommand (buffer, 0, buffer.length).process (screen);
  }

  // Codifica endereco de 12 bits (6 bits/byte) via tabela 3270 para evitar colisao com orders.
  // ---------------------------------------------------------------------------------//
  private int[] sba (int position)
  // ---------------------------------------------------------------------------------//
  {
    return new int[] { SBA, BufferAddress.address[(position >> 6) & 0x3F] & 0xFF,
                            BufferAddress.address[position & 0x3F] & 0xFF };
  }

  // Campos desprotegidos contiguos: 0..79 (atributo em 0) e 80..1919 (atributo em 80).
  // ---------------------------------------------------------------------------------//
  private void twoUnprotectedFields ()
  // ---------------------------------------------------------------------------------//
  {
    int[] a = sba (0);
    int[] b = sba (80);
    process (ERASE_WRITE, WCC_RESET_KEYBOARD, //
        a[0], a[1], a[2], SF, UNPROTECTED, 0xC1, 0xC2, 0xC3, //
        b[0], b[1], b[2], SF, UNPROTECTED, 0xC4, 0xC5);
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("movimento")
  class Movement
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("moveTo leva o cursor a posicao pedida")
    void moveToGoesToPosition ()
    {
      cursor.moveTo (5);

      assertEquals (5, cursor.getLocation ());
    }

    @Test
    @DisplayName ("RIGHT avanca uma posicao e LEFT recua uma")
    void rightAndLeftMoveByOne ()
    {
      cursor.moveTo (5);

      cursor.move (Direction.RIGHT);
      assertEquals (6, cursor.getLocation ());

      cursor.move (Direction.LEFT);
      assertEquals (5, cursor.getLocation ());
    }

    @Test
    @DisplayName ("DOWN desce uma linha e UP sobe uma")
    void upAndDownMoveByOneRow ()
    {
      cursor.moveTo (5);

      cursor.move (Direction.DOWN);
      assertEquals (5 + COLUMNS, cursor.getLocation ());

      cursor.move (Direction.UP);
      assertEquals (5, cursor.getLocation ());
    }

    @Test
    @DisplayName ("LEFT na posicao 0 da a volta para a ultima posicao da tela")
    void leftWrapsAroundFromOrigin ()
    {
      cursor.moveTo (0);

      cursor.move (Direction.LEFT);

      assertEquals (SIZE - 1, cursor.getLocation (),
          "Pen.validate soma o tamanho da tela em vez de saturar em zero");
    }

    @Test
    @DisplayName ("RIGHT na ultima posicao da a volta para zero")
    void rightWrapsAroundFromTheEnd ()
    {
      cursor.moveTo (SIZE - 1);

      cursor.move (Direction.RIGHT);

      assertEquals (0, cursor.getLocation ());
    }

    @Test
    @DisplayName ("UP na primeira linha da a volta para a ultima")
    void upWrapsToTheLastRow ()
    {
      cursor.moveTo (5);

      cursor.move (Direction.UP);

      assertEquals (SIZE - COLUMNS + 5, cursor.getLocation ());
    }

    @Test
    @DisplayName ("moveTo com posicao fora da tela e normalizado")
    void moveToNormalisesOutOfRangePositions ()
    {
      cursor.moveTo (SIZE + 3);
      assertEquals (3, cursor.getLocation ());

      cursor.moveTo (-1);
      assertEquals (SIZE - 1, cursor.getLocation ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("avisos de movimento")
  class Notifications
  // ---------------------------------------------------------------------------------//
  {
    private final List<Integer> moves = new ArrayList<> ();

    @Test
    @DisplayName ("cada movimento efetivo avisa o ouvinte")
    void everyRealMoveNotifies ()
    {
      cursor.addCursorMoveListener ( (o, n, f) -> moves.add (n));

      cursor.moveTo (1);
      cursor.moveTo (2);
      cursor.moveTo (3);

      assertEquals (List.of (1, 2, 3), moves);
    }

    @Test
    @DisplayName ("mover para onde o cursor ja esta nao avisa ninguem")
    void movingToTheSamePositionIsSilent ()
    {
      cursor.moveTo (7);
      cursor.addCursorMoveListener ( (o, n, f) -> moves.add (n));

      cursor.moveTo (7);

      assertTrue (moves.isEmpty (), "avisou " + moves);
    }

    @Test
    @DisplayName ("o ouvinte removido para de ser avisado")
    void removedListenerStopsBeingNotified ()
    {
      CursorMoveListener listener = (o, n, f) -> moves.add (n);
      cursor.addCursorMoveListener (listener);

      cursor.moveTo (1);
      cursor.removeCursorMoveListener (listener);
      cursor.moveTo (2);

      assertEquals (List.of (1), moves);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("campos")
  class Fields
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("home leva o cursor a primeira posicao de dados do primeiro campo")
    void homeGoesToTheFirstDataPosition ()
    {
      twoUnprotectedFields ();
      cursor.moveTo (500);

      cursor.home ();

      assertEquals (1, cursor.getLocation (),
          "o atributo ocupa a posicao 0, entao os dados comecam em 1");
    }

    @Test
    @DisplayName ("sem campo desprotegido, home nao move o cursor")
    void homeDoesNothingWithoutAnUnprotectedField ()
    {
      int[] a = sba (0);
      process (ERASE_WRITE, WCC_RESET_KEYBOARD, a[0], a[1], a[2], SF, PROTECTED, 0xC1);
      cursor.moveTo (500);

      cursor.home ();

      assertEquals (500, cursor.getLocation ());
    }

    @Test
    @DisplayName ("o campo corrente e o que contem a posicao do cursor")
    void currentFieldCoversTheCursor ()
    {
      twoUnprotectedFields ();

      cursor.moveTo (90);

      Field field = cursor.getCurrentField ();
      assertTrue (field != null && field.contains (90), "campo = " + field);
    }

    // Na aplicacao, setVisible(true) no ConsolePane resolve currentField antes de tab().
    @Test
    @DisplayName ("numa tela recem-montada, tab nao faz nada")
    void tabDoesNothingBeforeTheCurrentFieldIsResolved ()
    {
      twoUnprotectedFields ();
      cursor.moveTo (81);

      cursor.tab (true);

      assertEquals (81, cursor.getLocation (),
          "currentField ainda e nulo, e tab () comeca desistindo nesse caso");
    }

    @Test
    @DisplayName ("getCurrentField resolve o campo corrente, apesar do nome")
    void getCurrentFieldResolvesItLazily ()
    {
      twoUnprotectedFields ();
      cursor.moveTo (81);

      assertTrue (cursor.getCurrentField () != null, "o getter resolveu o campo");

      cursor.tab (true);

      assertEquals (1, cursor.getLocation (), "e agora tab funciona");
    }

    @Test
    @DisplayName ("resolvido o campo, backTab volta ao campo anterior")
    void backTabReturnsToThePreviousField ()
    {
      twoUnprotectedFields ();
      cursor.moveTo (81);
      cursor.getCurrentField ();

      cursor.tab (true);

      assertEquals (1, cursor.getLocation (),
          "a cadeia de campos desprotegidos e circular: o anterior do segundo e o primeiro");
    }

    @Test
    @DisplayName ("resolvido o campo, tab vai para o campo seguinte")
    void tabMovesToTheNextField ()
    {
      twoUnprotectedFields ();
      cursor.moveTo (1);
      cursor.getCurrentField ();

      cursor.tab (false);

      assertEquals (81, cursor.getLocation (),
          "o segundo campo tem o atributo em 80, entao os dados comecam em 81");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("edicao")
  class Editing
  // ---------------------------------------------------------------------------------//
  {
    private static final byte D = (byte) 0xC4;                 // 'D' em EBCDIC
    private static final byte X = (byte) 0xE7;                 // 'X' em EBCDIC

    private Field ready (int position)
    {
      twoUnprotectedFields ();
      cursor.moveTo (position);
      return cursor.getCurrentField ();
    }

    @Test
    @DisplayName ("typeChar escreve o byte e avanca o cursor")
    void typeCharWritesAndAdvances ()
    {
      Field field = ready (4);

      cursor.typeChar (D);

      assertTrue (field.getText ().startsWith ("ABCD"), "texto = [" + field.getText () + "]");
      assertEquals (5, cursor.getLocation ());
    }

    @Test
    @DisplayName ("typeChar marca o campo como modificado")
    void typeCharMarksTheFieldModified ()
    {
      Field field = ready (4);
      assertFalse (field.isModified (), "o campo comeca sem modificacao");

      cursor.typeChar (D);

      assertTrue (field.isModified ());
    }

    @Test
    @DisplayName ("typeChar num campo protegido nao escreve nada")
    void typeCharIsIgnoredOnAProtectedField ()
    {
      int[] a = sba (0);
      int[] b = sba (80);
      process (ERASE_WRITE, WCC_RESET_KEYBOARD, //
          a[0], a[1], a[2], SF, PROTECTED, 0xC1, 0xC2, 0xC3, //
          b[0], b[1], b[2], SF, PROTECTED, 0xC4);
      cursor.moveTo (4);
      Field field = cursor.getCurrentField ();
      String before = field.getText ();

      cursor.typeChar (D);

      assertEquals (before, field.getText (), "campo protegido nao aceita digitacao");
      assertEquals (4, cursor.getLocation (), "e o cursor nao anda");
    }

    @Test
    @DisplayName ("typeText escreve a cadeia inteira")
    void typeTextWritesTheWholeString ()
    {
      Field field = ready (4);

      cursor.typeText ("DEF");

      assertTrue (field.getText ().startsWith ("ABCDEF"), "texto = [" + field.getText () + "]");
      assertEquals (7, cursor.getLocation ());
    }

    @Test
    @DisplayName ("typeText com nulo ou vazio nao faz nada")
    void typeTextIgnoresNullAndEmpty ()
    {
      ready (4);

      cursor.typeText (null);
      cursor.typeText ("");

      assertEquals (4, cursor.getLocation ());
    }

    @Test
    @DisplayName ("typeText descarta os caracteres nao imprimiveis")
    void typeTextSkipsNonPrintableCharacters ()
    {
      Field field = ready (4);

      cursor.typeText ("D\nE");

      assertTrue (field.getText ().startsWith ("ABCDE"), "texto = [" + field.getText () + "]");
      assertEquals (6, cursor.getLocation (), "avancou duas posicoes, nao tres");
    }

    @Test
    @DisplayName ("backspace recua o cursor e puxa os bytes para a esquerda")
    void backspacePullsCharactersLeft ()
    {
      Field field = ready (4);

      cursor.backspace ();

      assertEquals (3, cursor.getLocation ());
      assertTrue (field.getText ().startsWith ("AB"), "texto = [" + field.getText () + "]");
      assertFalse (field.getText ().startsWith ("ABC"), "o C tinha de sair");
    }

    @Test
    @DisplayName ("backspace na primeira posicao de dados nao move o cursor")
    void backspaceAtTheFirstDataPositionDoesNotMove ()
    {
      ready (1);

      cursor.backspace ();

      assertEquals (1, cursor.getLocation (),
          "cursorOffset e 1, e o corpo que puxa os bytes exige mais que 1");
    }

    @Test
    @DisplayName ("delete puxa os bytes para a esquerda sem mover o cursor")
    void deletePullsWithoutMoving ()
    {
      Field field = ready (2);

      cursor.delete ();

      assertEquals (2, cursor.getLocation ());
      assertTrue (field.getText ().startsWith ("AC"), "texto = [" + field.getText () + "]");
    }

    @Test
    @DisplayName ("eraseEOL limpa do cursor ate o fim do campo")
    void eraseEolClearsToTheEndOfTheField ()
    {
      Field field = ready (2);

      cursor.eraseEOL ();

      assertEquals (2, cursor.getLocation ());
      assertTrue (field.getText ().trim ().equals ("A"), "texto = [" + field.getText () + "]");
    }

    @Test
    @DisplayName ("em modo de insercao, typeChar empurra os bytes para a direita")
    void insertModePushesCharactersRight ()
    {
      Field field = ready (2);
      screen.setInsertMode (true);

      cursor.typeChar (X);

      assertTrue (field.getText ().startsWith ("AXBC"), "texto = [" + field.getText () + "]");
      assertEquals (3, cursor.getLocation ());
    }

    @Test
    @DisplayName ("em modo de insercao, o campo cheio recusa a digitacao")
    void insertModeRefusesWhenTheFieldIsFull ()
    {
      Field field = ready (1);

      // Preenche ate o ultimo byte, pois insert mode so recusa se o fim nao for nulo/branco.
      StringBuilder filler = new StringBuilder ();
      for (int i = 0; i < field.getDisplayLength (); i++)
        filler.append ('Z');
      cursor.typeText (filler.toString ());

      String full = field.getText ();
      assertFalse (full.endsWith (" "), "o campo tem de estar cheio: [" + full + "]");

      cursor.moveTo (2);
      cursor.getCurrentField ();
      screen.setInsertMode (true);
      cursor.typeChar (X);

      assertEquals (full, field.getText (), "nao pode perder o byte do fim do campo");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("visibilidade")
  class Visibility
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("o cursor comeca invisivel")
    void startsHidden ()
    {
      assertFalse (cursor.isVisible ());
    }

    @Test
    @DisplayName ("setVisible liga e desliga")
    void visibilityToggles ()
    {
      cursor.setVisible (true);
      assertTrue (cursor.isVisible ());

      cursor.setVisible (false);
      assertFalse (cursor.isVisible ());
    }

    @Test
    @DisplayName ("tornar o cursor visivel avisa os ouvintes sem mover o cursor")
    void becomingVisibleNotifiesListeners ()
    {
      twoUnprotectedFields ();
      cursor.moveTo (5);

      List<Integer> moves = new ArrayList<> ();
      cursor.addCursorMoveListener ( (o, n, f) -> moves.add (n));

      cursor.setVisible (true);

      assertEquals (List.of (5), moves, "avisa a posicao atual, sem ter movido");
      assertEquals (5, cursor.getLocation ());
    }
  }
}
