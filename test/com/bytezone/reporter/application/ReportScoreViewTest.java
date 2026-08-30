package com.bytezone.reporter.application;

import static com.bytezone.reporter.file.ReportScores.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.bytezone.dm3270.testing.JavaFxToolkit;
import com.bytezone.reporter.file.ReportScore;

import javafx.scene.control.TextArea;

/*
 * O que sobrou de visual quando o ReportScore virou dominio puro: a Pagination e o TextArea.
 *
 * Estes testes vieram do ReportScoreTest, que precisava do toolkit grafico so porque o
 * ReportScore construia um TextArea no proprio construtor. Agora o toolkit e necessario aqui,
 * onde ele faz sentido, e o ReportScoreTest roda headless.
 *
 * As duas propriedades que estao aqui e nao no dominio:
 *
 *   - o MESMO TextArea a cada pagina. A Pagination guarda getFormattedPage como fabrica e
 *     troca o Node do centro a cada virada; devolver instancia nova mudaria o que a janela faz;
 *   - a criacao preguicosa da Pagination, que TEM EFEITO: a primeira chamada manda o
 *     ReportScore montar as paginas, e so entao ha quantas contar.
 */
// -----------------------------------------------------------------------------------//
@ExtendWith (JavaFxToolkit.class)
@DisplayName ("ReportScoreView - a Pagination e o TextArea de um ReportScore")
class ReportScoreViewTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  private static String shown (ReportScoreView view, int pageNumber)
  // ---------------------------------------------------------------------------------//
  {
    return ((TextArea) view.getFormattedPage (pageNumber)).getText ();
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("getPagination")
  class Paginating
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("a primeira chamada monta as paginas e conta quantas sao")
    void creationPopulatesThePages ()
    {
      ReportScore reportScore = text ("AAA\nBBB\nCCC\n");
      ReportScoreView view = new ReportScoreView (reportScore);

      assertTrue (reportScore.getPages ().isEmpty (), "antes, nao ha pagina nenhuma");

      assertNotNull (view.getPagination ());

      assertEquals (1, reportScore.getPages ().size ());
      assertEquals (1, view.getPagination ().getPageCount ());
    }

    @Test
    @DisplayName ("a segunda chamada devolve a mesma Pagination, sem repaginar")
    void secondCallIsCached ()
    {
      ReportScoreView view = new ReportScoreView (text ("AAA\nBBB\nCCC\n"));

      assertSame (view.getPagination (), view.getPagination ());
    }

    @Test
    @DisplayName ("acima de 66 registros a paginacao conta mais de uma pagina")
    void countsEveryPage ()
    {
      StringBuilder buffer = new StringBuilder ();
      for (int i = 0; i < 70; i++)
        buffer.append ("linha ").append (i).append ('\n');

      ReportScoreView view = new ReportScoreView (text (buffer.toString ()));

      assertEquals (2, view.getPagination ().getPageCount ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("getFormattedPage")
  class Pages
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("o TextArea recebe o texto que o ReportScore montou")
    void showsTheDomainText ()
    {
      ReportScore reportScore = text ("AAA\nBBB\nCCC\n");
      ReportScoreView view = new ReportScoreView (reportScore);
      view.getPagination ();

      assertEquals ("AAA\nBBB\nCCC", shown (view, 0));
      assertEquals (reportScore.getFormattedText (0), shown (view, 0));
    }

    @Test
    @DisplayName ("devolve sempre a MESMA instancia de TextArea, mutada")
    void reusesTheSameWidget ()
    {
      ReportScoreView view = new ReportScoreView (text ("AAA\nBBB\nCCC\n"));
      view.getPagination ();

      assertSame (view.getFormattedPage (0), view.getFormattedPage (0));
    }

    @Test
    @DisplayName ("pagina fora da faixa nao lanca: o widget fica vazio")
    void outOfRangeEmptiesTheWidget ()
    {
      ReportScoreView view = new ReportScoreView (text ("AAA\nBBB\nCCC\n"));
      view.getPagination ();

      assertEquals ("AAA\nBBB\nCCC", shown (view, 0), "primeiro com conteudo");

      assertEquals ("", shown (view, 1), "uma pagina alem do fim");
      assertEquals ("", shown (view, -1), "e antes do inicio");
    }

    @Test
    @DisplayName ("o widget esvaziado e o mesmo de sempre, e nao um novo")
    void keepsTheSharedWidgetWhenEmpty ()
    {
      ReportScoreView view = new ReportScoreView (text ("AAA\nBBB\nCCC\n"));
      view.getPagination ();

      assertSame (view.getFormattedPage (0), view.getFormattedPage (99));
    }
  }
}
