package com.bytezone.reporter.file;

import java.util.ArrayList;
import java.util.List;

import com.bytezone.reporter.record.Record;
import com.bytezone.reporter.record.RecordMaker;
import com.bytezone.reporter.reports.Page;
import com.bytezone.reporter.reports.ReportContext;
import com.bytezone.reporter.reports.ReportMaker;
import com.bytezone.reporter.text.TextMaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// -----------------------------------------------------------------------------------//
public class ReportScore implements Comparable<ReportScore>, ReportContext
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (ReportScore.class);

  private final RecordMaker recordMaker;
  private final TextMaker textMaker;
  private final ReportMaker reportMaker;

  private final double score;
  private final int sampleSize;
  private final double weight;

  private final List<Page> pages = new ArrayList<> ();

  // ---------------------------------------------------------------------------------//
  ReportScore (RecordMaker recordMaker, TextMaker textMaker, ReportMaker reportMaker,
      double score, int sampleSize)
  // ---------------------------------------------------------------------------------//
  {
    this.recordMaker = recordMaker;
    this.textMaker = textMaker;
    this.reportMaker = reportMaker;

    this.score = score;
    this.sampleSize = sampleSize;
    this.weight = recordMaker.weight () * reportMaker.weight ();
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public RecordMaker getRecordMaker ()
  // ---------------------------------------------------------------------------------//
  {
    return recordMaker;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public TextMaker getTextMaker ()
  // ---------------------------------------------------------------------------------//
  {
    return textMaker;
  }

  // ---------------------------------------------------------------------------------//
  public ReportMaker getReportMaker ()
  // ---------------------------------------------------------------------------------//
  {
    return reportMaker;
  }

  // ---------------------------------------------------------------------------------//
  public boolean matches (RecordMaker recordMaker, TextMaker textMaker,
      ReportMaker reportMaker)
  // ---------------------------------------------------------------------------------//
  {
    return this.recordMaker == recordMaker && this.textMaker == textMaker
        && this.reportMaker == reportMaker;
  }

  // ---------------------------------------------------------------------------------//
  public boolean isPerfectScore ()
  // ---------------------------------------------------------------------------------//
  {
    return score == 100.0;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public List<Page> getPages ()
  // ---------------------------------------------------------------------------------//
  {
    return pages;
  }

  /*
   * Manda o relatorio dividir os registros em paginas.
   *
   * Era a primeira coisa que o antigo getPagination () fazia, e a preguica que o guardava
   * mudou de lugar junto com a Pagination: hoje quem chama uma vez so e o ReportScoreView.
   * Chamar duas vezes tambem nao quebraria - as quatro implementacoes de createPages
   * comecam limpando a lista.
   */
  // ---------------------------------------------------------------------------------//
  public void createPages ()
  // ---------------------------------------------------------------------------------//
  {
    reportMaker.createPages (this);
  }

  /*
   * O texto de uma pagina, sem widget nenhum.
   *
   * Saiu de dentro do getFormattedPage, que fazia duas coisas: montar o texto a partir dos
   * registros, das paginas e do reportMaker, e depois escreve-lo num TextArea. A primeira
   * metade e dominio e agora e testavel sem toolkit grafico; a segunda ficou logo abaixo, e
   * sai desta classe no proximo commit.
   *
   * A GUARDA DE PAGINA INVALIDA E O AVISO FICAM AQUI, e nao na parte visual, por dois
   * motivos. O primeiro e que decidir se um numero de pagina existe e a pergunta que este
   * lado sabe responder - e ele que tem a lista. O segundo e o §5.13: o logback imprime
   * %logger{36}, entao mudar a classe que emite o aviso mudaria o texto de toda linha que ele
   * produz.
   */
  // ---------------------------------------------------------------------------------//
  public String getFormattedText (int pageNumber)
  // ---------------------------------------------------------------------------------//
  {
    if (pageNumber < 0 || pageNumber >= pages.size ())
    {
      logger.warn ("impossible pageNumber requested: {}", pageNumber);
      return "";
    }

    List<Record> records = recordMaker.getRecords ();
    StringBuilder text = new StringBuilder ();

    Page page = pages.get (pageNumber);
    int firstRecord = page.getFirstRecordIndex ();
    int firstRecordOffset = page.getFirstRecordOffset ();
    int lastRecord = page.getLastRecordIndex ();
    int lastRecordOffset = page.getLastRecordOffset ();

    if (firstRecord == lastRecord)
    {
      Record record = records.get (firstRecord);
      text.append (getSubrecord (record, firstRecordOffset, lastRecordOffset));
    }
    else
    {
      for (int i = firstRecord; i <= lastRecord; i++)
      {
        Record record = records.get (i);
        String formattedRecord = null;

        if (firstRecordOffset > 0 && i == firstRecord)
          formattedRecord = getSubrecord (record, firstRecordOffset, 0);
        else if (lastRecordOffset > 0 && i == lastRecord)
          formattedRecord = getSubrecord (record, 0, lastRecordOffset);
        else
          formattedRecord = (reportMaker.getFormattedRecord (this, record));

        if (formattedRecord == null)
          continue;

        text.append (formattedRecord);
        text.append ('\n');

        if (reportMaker.newlineBetweenRecords ())
          text.append ('\n');
      }
    }

    // remove trailing newlines
    while (text.length () > 0 && text.charAt (text.length () - 1) == '\n')
      text.deleteCharAt (text.length () - 1);

    return text.toString ();
  }

  // ---------------------------------------------------------------------------------//
  private String getSubrecord (Record record, int from, int to)
  // ---------------------------------------------------------------------------------//
  {
    int offset = 0;
    int length = 0;

    if (from > 0 && to > 0)
    {
      offset = from;
      length = to - from;
    }
    else if (from > 0)
    {
      offset = from;
      length = record.length - from;
    }
    else if (to > 0)
      length = to;
    else
      length = record.length;

    return reportMaker.getFormattedRecord (this, record, offset, length);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public Page addPage (int firstRecord, int lastRecord)
  // ---------------------------------------------------------------------------------//
  {
    Page page = new Page (recordMaker.getRecords (), firstRecord, lastRecord);
    pages.add (page);

    if (pages.size () > 1)
    {
      Page previousPage = pages.get (pages.size () - 2);
      page.setFirstRecordOffset (previousPage.getLastRecordOffset ());
    }

    return page;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public int compareTo (ReportScore o)
  // ---------------------------------------------------------------------------------//
  {
    if (this.score == o.score)
      return Double.compare (this.weight, o.weight);
    return Double.compare (this.score, o.score);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String toString ()
  // ---------------------------------------------------------------------------------//
  {
    return String.format ("%-10s %-10s %-10s %6.2f %3d  %4.2f", recordMaker, textMaker,
        reportMaker, score, sampleSize, weight);
  }
}