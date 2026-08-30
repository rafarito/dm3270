package com.bytezone.reporter.application;

import com.bytezone.reporter.file.ReportScore;

import javafx.scene.Node;
import javafx.scene.control.Pagination;
import javafx.scene.control.TextArea;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/*
 * Os widgets que mostram um ReportScore: a Pagination que numera as paginas e o TextArea onde
 * o texto de cada uma aparece.
 *
 * Os tres campos daqui - font, pagination e textArea - moravam dentro do ReportScore, que e
 * um objeto de pontuacao e paginacao. Eram 16 das violacoes da regra que diz que so a
 * interface conhece JavaFX, e a razao de a montagem de pagina do subsistema so poder ser
 * exercitada atraves de um Node.
 *
 * DUAS COISAS PRECISAM SER PRESERVADAS AQUI, e as duas tem teste:
 *
 * 1. O MESMO TextArea a cada pagina. A Pagination guarda getFormattedPage como fabrica e
 *    troca o Node do centro a cada virada; devolver uma instancia nova a cada chamada mudaria
 *    o que a janela faz. Por isso o campo e final e so o texto dele muda.
 *
 * 2. UMA VIEW POR ReportScore, e nao uma por exibicao. A Pagination lembra em que pagina o
 *    usuario estava. Como o FormatBox pede a paginacao de novo toda vez que os botoes de
 *    formato mudam, e o ReportData devolve a MESMA instancia de ReportScore para uma
 *    combinacao ja vista, alternar formato de ida e volta devolvia o usuario a pagina em que
 *    ele estava. Isso era consequencia de o ReportScore guardar a propria Pagination; agora
 *    depende de o FormatBox guardar a view, e e por isso que ele tem um mapa por identidade.
 */
// -----------------------------------------------------------------------------------//
public class ReportScoreView
// -----------------------------------------------------------------------------------//
{
  private static Font font;

  private final ReportScore reportScore;
  private final TextArea textArea = new TextArea ();
  private Pagination pagination;

  // ---------------------------------------------------------------------------------//
  static
  // ---------------------------------------------------------------------------------//
  {
    String[] fontNames = { "Ubuntu Mono", "Menlo", "Courier New", "Monospaced", };
    for (String fontName : fontNames)
    {
      font = Font.font (fontName, FontWeight.NORMAL, 14);
      if (font.getName ().startsWith (fontName))
        break;
    }
  }

  // ---------------------------------------------------------------------------------//
  public ReportScoreView (ReportScore reportScore)
  // ---------------------------------------------------------------------------------//
  {
    this.reportScore = reportScore;

    textArea.setFont (font);
    textArea.setEditable (false);
    textArea.setMinHeight (50);
  }

  /*
   * A criacao e preguicosa e TEM EFEITO: a primeira chamada manda o ReportScore montar as
   * paginas, e so entao ha quantas contar. Era assim quando este metodo morava no
   * ReportScore, e a guarda continua sendo a mesma - o pagination nulo.
   */
  // ---------------------------------------------------------------------------------//
  public Pagination getPagination ()
  // ---------------------------------------------------------------------------------//
  {
    if (pagination == null)
    {
      reportScore.createPages ();

      pagination = new Pagination ();
      pagination.setPageCount (reportScore.getPages ().size ());
      pagination.setPageFactory (this::getFormattedPage);
    }
    return pagination;
  }

  // ---------------------------------------------------------------------------------//
  public Node getFormattedPage (int pageNumber)
  // ---------------------------------------------------------------------------------//
  {
    textArea.setText (reportScore.getFormattedText (pageNumber));
    return textArea;
  }
}
