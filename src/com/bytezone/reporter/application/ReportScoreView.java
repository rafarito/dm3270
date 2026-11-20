package com.bytezone.reporter.application;

import com.bytezone.reporter.file.ReportScore;

import javafx.scene.Node;
import javafx.scene.control.Pagination;
import javafx.scene.control.TextArea;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

// View JavaFX de ReportScore; reutiliza o mesmo TextArea entre trocas de pagina.
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

  // Cria a Pagination sob demanda, disparando reportScore.createPages() na primeira chamada.
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
