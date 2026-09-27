package com.bytezone.dm3270.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.bytezone.dm3270.attributes.ColorAttribute;
import com.bytezone.dm3270.attributes.TerminalColor;
import com.bytezone.dm3270.screen.FontMetrics;
import com.bytezone.dm3270.screen.ScreenCanvas;
import com.bytezone.dm3270.screen.ScreenContext;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.screen.ScreenPosition;
import com.bytezone.dm3270.testing.JavaFxToolkit;
import com.bytezone.dm3270.utilities.Dm3270Utility;

/*
 * A PRIMEIRA REDE DA ScreenSelection, e ela so existe porque a porta SelectionHost existe.
 *
 * Antes do passo 13 a selecao recebia a Screen concreta, e a unica forma de exercita-la era
 * construir uma Screen inteira - o que arrasta TransfersStage, ReporterNode e o diretorio
 * pessoal de quem roda a suite (ScreenConstructionTest). Contra um SelectionHost que grava, ela
 * roda SEM TOOLKIT, e cada caso afirma a lista completa de redesenhos, na ordem.
 *
 * 24x80 de proposito: o construtor de ScreenDimensions escreve BufferAddress.setScreenWidth,
 * estado estatico global. 80 e o valor que as outras classes restauram, entao esta nao suja
 * ninguem.
 *
 * So flashSelection precisa do toolkit, porque a PauseTransition pede o timer da plataforma na
 * construcao. Ele tem classe aninhada propria, e so a metade sincrona e afirmada.
 */
// -----------------------------------------------------------------------------------//
@DisplayName ("ScreenSelection - a selecao com o mouse, vista pela porta")
class ScreenSelectionTest
// -----------------------------------------------------------------------------------//
{
  private static final ScreenDimensions DIMENSIONS = new ScreenDimensions (24, 80);

  private final RecordingHost host = new RecordingHost ();
  private final ScreenSelection selection = new ScreenSelection (host);

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("comecar marca e redesenha so a posicao do clique, e ainda nao ha selecao")
  void startMarksOnePosition ()
  // ---------------------------------------------------------------------------------//
  {
    selection.startSelection (10);

    assertEquals (List.of ("redrawRange(10, 10)"), host.calls);
    assertEquals (List.of (10), host.selected ());
    assertTrue (selection.isActive ());
    assertFalse (selection.hasSelection (), "inicio e fim coincidem");
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("arrastar sem ter comecado nao faz nada")
  void extendWithoutStartIsIgnored ()
  // ---------------------------------------------------------------------------------//
  {
    selection.extendSelection (20);
    selection.endSelection (20);

    assertEquals (List.of (), host.calls);
    assertEquals (List.of (), host.selected ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("arrastar para a frente marca o intervalo e pede o redesenho dos dois")
  void extendForward ()
  // ---------------------------------------------------------------------------------//
  {
    selection.startSelection (10);
    selection.extendSelection (13);

    assertEquals (List.of ("redrawRange(10, 10)", "redrawSelection(10, 10, 10, 13)"),
        host.calls);
    assertEquals (List.of (10, 11, 12, 13), host.selected ());
    assertTrue (selection.hasSelection ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("voltar o arrasto desmarca o que saiu do intervalo")
  void extendBackShrinks ()
  // ---------------------------------------------------------------------------------//
  {
    selection.startSelection (10);
    selection.extendSelection (14);
    selection.extendSelection (11);

    assertEquals (List.of (10, 11), host.selected ());
    assertEquals ("redrawSelection(10, 14, 10, 11)", host.calls.get (2));
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("arrastar para tras troca minimo e maximo")
  void extendBackwardsPastTheStart ()
  // ---------------------------------------------------------------------------------//
  {
    selection.startSelection (10);
    selection.extendSelection (7);

    assertEquals (List.of ("redrawRange(10, 10)", "redrawSelection(10, 10, 7, 10)"),
        host.calls);
    assertEquals (List.of (7, 8, 9, 10), host.selected ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("soltar encerra o arrasto, remarca e redesenha o intervalo final")
  void endFinishesTheDrag ()
  // ---------------------------------------------------------------------------------//
  {
    selection.startSelection (10);
    selection.endSelection (12);

    assertEquals (List.of ("redrawRange(10, 10)", "redrawRange(10, 12)"), host.calls);
    assertEquals (List.of (10, 11, 12), host.selected ());
    assertFalse (selection.isActive ());
    assertTrue (selection.hasSelection ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("limpar sem selecao nao redesenha nada")
  void clearWithoutSelection ()
  // ---------------------------------------------------------------------------------//
  {
    selection.clearSelection ();

    assertEquals (List.of (), host.calls);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("um clique novo limpa a selecao anterior ANTES de marcar a posicao nova")
  void startClearsThePreviousSelectionFirst ()
  // ---------------------------------------------------------------------------------//
  {
    selection.startSelection (10);
    selection.endSelection (12);
    host.calls.clear ();

    selection.startSelection (40);

    assertEquals (List.of ("redrawRange(10, 12)", "redrawRange(40, 40)"), host.calls);
    assertEquals (List.of (40), host.selected ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("o texto quebra linha na mudanca de fila e corta os espacos do fim de cada uma")
  void selectedTextAcrossRows ()
  // ---------------------------------------------------------------------------------//
  {
    host.write (76, "AB  CD");      // 76-79 na primeira fila, 80-81 na segunda

    selection.startSelection (76);
    selection.endSelection (81);

    assertEquals ("AB\nCD", selection.getSelectedText ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("arrastar para tras devolve o texto na ordem da tela")
  void selectedTextBackwards ()
  // ---------------------------------------------------------------------------------//
  {
    host.write (0, "HELLO");

    selection.startSelection (4);
    selection.endSelection (0);

    assertEquals ("HELLO", selection.getSelectedText ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("sem selecao o texto e vazio")
  void noSelectionNoText ()
  // ---------------------------------------------------------------------------------//
  {
    selection.startSelection (5);

    assertEquals ("", selection.getSelectedText ());
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @ExtendWith (JavaFxToolkit.class)
  @DisplayName ("flashSelection, a metade sincrona")
  class Flash
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("apaga o destaque e redesenha na hora; a volta fica para a PauseTransition")
    void clearsTheHighlightImmediately ()
    // -------------------------------------------------------------------------------//
    {
      selection.startSelection (10);
      selection.endSelection (12);
      host.calls.clear ();

      JavaFxToolkit.onFxThread ( () ->
      {
        selection.flashSelection ();
        return null;
      });

      assertEquals ("redrawRange(10, 12)", host.calls.get (0));
      assertTrue (selection.hasSelection (), "o estado da selecao nao muda, so o desenho");
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("sem selecao nao pisca")
    void noSelectionNoFlash ()
    // -------------------------------------------------------------------------------//
    {
      JavaFxToolkit.onFxThread ( () ->
      {
        selection.flashSelection ();
        return null;
      });

      assertEquals (List.of (), host.calls);
    }
  }

  /*
   * Grava os redesenhos em vez de executa-los. As posicoes sao ScreenPosition de verdade - a
   * classe e final -, sobre um canvas que nao faz nada: e o estado de "selecionada" delas que
   * os casos afirmam.
   */
  // ---------------------------------------------------------------------------------//
  private static final class RecordingHost implements SelectionHost
  // ---------------------------------------------------------------------------------//
  {
    private final List<String> calls = new ArrayList<> ();
    private final ScreenPosition[] positions = new ScreenPosition[DIMENSIONS.size];

    // -------------------------------------------------------------------------------//
    RecordingHost ()
    // -------------------------------------------------------------------------------//
    {
      TerminalColor green = ColorAttribute.colors[4];
      TerminalColor black = ColorAttribute.colors[8];
      ScreenContext context = new ScreenContext (green, black, (byte) 0, false,
          new FontMetrics ("Fonte de teste", 10, 20, 16));

      for (int i = 0; i < positions.length; i++)
        positions[i] = new ScreenPosition (i, new SilentCanvas (), DIMENSIONS, context);
    }

    // -------------------------------------------------------------------------------//
    void write (int start, String text)
    // -------------------------------------------------------------------------------//
    {
      for (int i = 0; i < text.length (); i++)
        positions[start + i].setChar ((byte) Dm3270Utility.asc2ebc[text.charAt (i)]);
    }

    // -------------------------------------------------------------------------------//
    List<Integer> selected ()
    // -------------------------------------------------------------------------------//
    {
      List<Integer> selected = new ArrayList<> ();
      for (int i = 0; i < positions.length; i++)
        if (positions[i].isSelected ())
          selected.add (i);
      return selected;
    }

    @Override
    public ScreenDimensions getScreenDimensions ()
    {
      return DIMENSIONS;
    }

    @Override
    public ScreenPosition getScreenPosition (int position)
    {
      return positions[position];
    }

    @Override
    public void redrawRange (int from, int to)
    {
      calls.add (String.format ("redrawRange(%d, %d)", from, to));
    }

    @Override
    public void redrawSelection (int oldMin, int oldMax, int newMin, int newMax)
    {
      calls.add (String.format ("redrawSelection(%d, %d, %d, %d)", oldMin, oldMax, newMin,
          newMax));
    }
  }

  // ---------------------------------------------------------------------------------//
  private static final class SilentCanvas implements ScreenCanvas
  // ---------------------------------------------------------------------------------//
  {
    @Override
    public void setFill (TerminalColor color)
    {
    }

    @Override
    public void setStroke (TerminalColor color)
    {
    }

    @Override
    public void fillRect (double x, double y, double width, double height)
    {
    }

    @Override
    public void fillText (String text, double x, double y)
    {
    }

    @Override
    public void strokeLine (double x1, double y1, double x2, double y2)
    {
    }
  }
}
