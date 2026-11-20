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

  private static final TerminalColor GREEN = ColorAttribute.colors[4];
  private static final TerminalColor BLACK = ColorAttribute.colors[8];

  private static final String GREEN_FILL = "setFill(0x00ff00ff)";
  private static final String BLACK_FILL = "setFill(0x000000ff)";
  private static final String CELL = "fillRect(4.0, 4.0, 10.0, 20.0)";

  private static final byte EBCDIC_A = (byte) 0xC1;

  // Metricas fixas (w=10, h=20, ascent=16) para evitar variacao entre fontes do sistema.
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
      position.setChar ((byte) 0xF1);
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

  // Cursor, video reverso e selecao se combinam por XOR (dois se anulam).
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

  // Coordenadas graficas partem de (x+0.5, y+0.5) com passo dx=w/2 e dy=h/2.
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
