package com.bytezone.dm3270.display;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.bytezone.dm3270.attributes.ColorAttribute;
import com.bytezone.dm3270.attributes.TerminalColor;
import com.bytezone.dm3270.screen.FontMetrics;
import com.bytezone.dm3270.screen.ScreenCanvas;
import com.bytezone.dm3270.screen.ScreenContext;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.screen.ScreenPosition;

/*
 * A sequencia de desenho de uma posicao de tela, congelada.
 *
 * ScreenPosition desenhava chamando javafx.scene.canvas.GraphicsContext direto e media a
 * fonte por um FontDetails, que instancia um no Text do JavaFX. Agora fala com ScreenCanvas
 * e le um FontMetrics - os dois neutros. Nada disso exige toolkit grafico, e por isso este
 * teste existe: antes nao havia como executar draw fora de uma aplicacao JavaFX.
 *
 * A extracao foi mecanica, mas "mecanica" nao e prova. Estes testes gravam a sequencia exata
 * de operacoes que draw produz, COM os argumentos, e passam a cobrar essa sequencia. Sao o
 * que impede que a decomposicao de Screen, mais adiante, mude o desenho sem que ninguem
 * perceba - nao existe teste de pixel neste projeto.
 *
 * As metricas sao fixas e inventadas (10 x 20, base em 16) justamente para que as
 * coordenadas sejam exatas e iguais em qualquer maquina. Com FontDetails de verdade elas
 * dependeriam das fontes instaladas.
 *
 * Detalhes que precisam sobreviver e por isso estao verificados aqui:
 *
 *   - a ordem: fundo (setFill + fillRect) antes do primeiro plano
 *   - o offset de 0,5 aplicado antes de qualquer stroke ("stroke commands need to be offset
 *     for Windows"), tanto no sublinhado quanto nos caracteres graficos
 *   - a inversao por XOR entre cursor, video reverso e selecao, que decide qual cor vai no
 *     fundo e qual no primeiro plano
 *   - uma posicao invisivel (campo de senha) pinta o fundo e nao desenha o caractere
 *   - a traducao EBCDIC do byte para o caractere exibido
 */
// -----------------------------------------------------------------------------------//
@DisplayName ("ScreenPosition - sequencia de desenho")
class ScreenPositionDrawingTest
// -----------------------------------------------------------------------------------//
{
  private static final boolean CURSOR = true;
  private static final boolean NO_CURSOR = false;

  private static final byte NO_HIGHLIGHT = 0;
  private static final byte UNDERSCORE = (byte) 0xF4;
  private static final byte REVERSE_VIDEO = (byte) 0xF2;

  private static final TerminalColor GREEN = ColorAttribute.colors[4];     // 0x00ff00ff
  private static final TerminalColor BLACK = ColorAttribute.colors[8];     // 0x000000ff

  private static final String GREEN_FILL = "setFill(0x00ff00ff)";
  private static final String BLACK_FILL = "setFill(0x000000ff)";
  private static final String CELL = "fillRect(4.0, 4.0, 10.0, 20.0)";

  private static final byte EBCDIC_A = (byte) 0xC1;

  // largura 10, altura 20, base em 16: valores fixos para coordenadas reprodutiveis
  private static final FontMetrics METRICS = new FontMetrics ("Fonte de teste", 10, 20, 16);

  private final RecordingCanvas canvas = new RecordingCanvas ();

  // ---------------------------------------------------------------------------------//
  private ScreenPosition position (byte highlight)
  // ---------------------------------------------------------------------------------//
  {
    return new ScreenPosition (0, canvas, new ScreenDimensions (24, 80),
        new ScreenContext (GREEN, BLACK, highlight, false, METRICS));
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("texto")
  class Text
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("fundo primeiro, depois o caractere na linha de base")
    void backgroundThenCharacter ()
    {
      ScreenPosition position = position (NO_HIGHLIGHT);
      position.setChar (EBCDIC_A);
      position.draw (NO_CURSOR);

      // y da base = 4 (topo) + 16 (ascent)
      assertEquals (List.of (BLACK_FILL, CELL, GREEN_FILL, "fillText(A, 4.0, 20.0)"),
          canvas.calls);
    }

    @Test
    @DisplayName ("o byte EBCDIC e traduzido para o caractere exibido")
    void ebcdicIsTranslated ()
    {
      ScreenPosition position = position (NO_HIGHLIGHT);
      position.setChar ((byte) 0xF1);                 // '1' em EBCDIC
      position.draw (NO_CURSOR);

      assertEquals ("fillText(1, 4.0, 20.0)", canvas.calls.get (3));
    }

    @Test
    @DisplayName ("uma posicao invisivel pinta o fundo e nao desenha o caractere")
    void invisibleDrawsNoText ()
    {
      ScreenPosition position = position (NO_HIGHLIGHT);
      position.setChar (EBCDIC_A);
      position.setVisible (false);
      position.draw (NO_CURSOR);

      assertEquals (List.of (BLACK_FILL, CELL), canvas.calls,
          "campo de senha: fundo sim, texto nao");
    }
  }

  /*
   * Cursor, video reverso e selecao se combinam por XOR: cada um inverte, dois se anulam.
   */
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("inversao de cores")
  class Inversion
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("o cursor troca fundo e primeiro plano")
    void cursorInverts ()
    {
      ScreenPosition position = position (NO_HIGHLIGHT);
      position.setChar (EBCDIC_A);
      position.draw (CURSOR);

      assertEquals (List.of (GREEN_FILL, CELL, BLACK_FILL, "fillText(A, 4.0, 20.0)"),
          canvas.calls);
    }

    @Test
    @DisplayName ("a selecao inverte igual ao cursor")
    void selectionInverts ()
    {
      ScreenPosition position = position (NO_HIGHLIGHT);
      position.setChar (EBCDIC_A);
      position.setSelected (true);
      position.draw (NO_CURSOR);

      assertEquals (GREEN_FILL, canvas.calls.get (0));
      assertEquals (BLACK_FILL, canvas.calls.get (2));
    }

    @Test
    @DisplayName ("o video reverso inverte igual ao cursor")
    void reverseVideoInverts ()
    {
      ScreenPosition position = position (REVERSE_VIDEO);
      position.setChar (EBCDIC_A);
      position.draw (NO_CURSOR);

      assertEquals (GREEN_FILL, canvas.calls.get (0));
      assertEquals (BLACK_FILL, canvas.calls.get (2));
    }

    @Test
    @DisplayName ("cursor sobre selecao se anula")
    void cursorAndSelectionCancelOut ()
    {
      ScreenPosition position = position (NO_HIGHLIGHT);
      position.setChar (EBCDIC_A);
      position.setSelected (true);
      position.draw (CURSOR);

      assertEquals (BLACK_FILL, canvas.calls.get (0), "duas inversoes voltam ao normal");
    }

    @Test
    @DisplayName ("cursor sobre video reverso se anula")
    void cursorAndReverseVideoCancelOut ()
    {
      ScreenPosition position = position (REVERSE_VIDEO);
      position.setChar (EBCDIC_A);
      position.draw (CURSOR);

      assertEquals (BLACK_FILL, canvas.calls.get (0));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("sublinhado")
  class Underscore
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("desenha a linha na base da celula, com o offset de 0,5")
    void drawsTheLine ()
    {
      ScreenPosition position = position (UNDERSCORE);
      position.setChar (EBCDIC_A);
      position.draw (NO_CURSOR);

      // x e y ganham 0,5 antes do stroke; y2 = 4,5 + 20 - 1
      assertEquals (List.of (BLACK_FILL, CELL, GREEN_FILL, "fillText(A, 4.0, 20.0)",
          "setStroke(0x00ff00ff)", "strokeLine(4.5, 23.5, 14.5, 23.5)"), canvas.calls);
    }
  }

  /*
   * Os caracteres graficos (GraphicsEscape) desenham linhas em vez de texto. Todas as
   * coordenadas partem de x e y ja deslocados em 0,5, com dx e dy sendo metade da celula.
   */
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("caracteres graficos")
  class Graphics
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("linha horizontal: uma linha no meio da celula")
    void horizontalLine ()
    {
      ScreenPosition position = position (NO_HIGHLIGHT);
      position.setGraphicsChar (ScreenPosition.HORIZONTAL_LINE);
      position.draw (NO_CURSOR);

      assertEquals (List.of (BLACK_FILL, CELL, "setStroke(0x00ff00ff)",
          "strokeLine(4.5, 14.5, 14.5, 14.5)"), canvas.calls);
    }

    @Test
    @DisplayName ("linha vertical: uma linha no meio da celula")
    void verticalLine ()
    {
      ScreenPosition position = position (NO_HIGHLIGHT);
      position.setGraphicsChar (ScreenPosition.VERTICAL_LINE);
      position.draw (NO_CURSOR);

      assertEquals ("strokeLine(9.5, 4.5, 9.5, 24.5)", canvas.calls.get (3));
    }

    @Test
    @DisplayName ("canto superior esquerdo: vertical para baixo e horizontal para a direita")
    void topLeft ()
    {
      ScreenPosition position = position (NO_HIGHLIGHT);
      position.setGraphicsChar (ScreenPosition.TOP_LEFT);
      position.draw (NO_CURSOR);

      assertEquals (List.of ("strokeLine(9.5, 14.5, 9.5, 24.5)",
          "strokeLine(9.5, 14.5, 14.5, 14.5)"), canvas.calls.subList (3, 5));
    }

    @Test
    @DisplayName ("canto inferior direito: vertical para cima e horizontal para a esquerda")
    void bottomRight ()
    {
      ScreenPosition position = position (NO_HIGHLIGHT);
      position.setGraphicsChar (ScreenPosition.BOTTOM_RIGHT);
      position.draw (NO_CURSOR);

      assertEquals (List.of ("strokeLine(9.5, 4.5, 9.5, 14.5)",
          "strokeLine(4.5, 14.5, 9.5, 14.5)"), canvas.calls.subList (3, 5));
    }

    @Test
    @DisplayName ("um grafico desconhecido cai no ponto")
    void unknownGraphicFallsBackToDot ()
    {
      ScreenPosition position = position (NO_HIGHLIGHT);
      position.setGraphicsChar ((byte) 0x01);
      position.draw (NO_CURSOR);

      assertEquals ("fillText(., 4.5, 20.5)", canvas.calls.get (3));
    }
  }

  /*
   * Dublê que so anota. Nao ha assercao nenhuma dentro dele: o valor esta em registrar a
   * sequencia com fidelidade e deixar cada teste comparar.
   */
  // ---------------------------------------------------------------------------------//
  private static final class RecordingCanvas implements ScreenCanvas
  // ---------------------------------------------------------------------------------//
  {
    final List<String> calls = new ArrayList<> ();

    @Override
    public void setFill (TerminalColor color)
    {
      calls.add (String.format ("setFill(%s)", color));
    }

    @Override
    public void setStroke (TerminalColor color)
    {
      calls.add (String.format ("setStroke(%s)", color));
    }

    @Override
    public void fillRect (double x, double y, double width, double height)
    {
      calls.add (String.format ("fillRect(%s, %s, %s, %s)", x, y, width, height));
    }

    @Override
    public void fillText (String text, double x, double y)
    {
      calls.add (String.format ("fillText(%s, %s, %s)", text, x, y));
    }

    @Override
    public void strokeLine (double x1, double y1, double x2, double y2)
    {
      calls.add (String.format ("strokeLine(%s, %s, %s, %s)", x1, y1, x2, y2));
    }
  }
}
