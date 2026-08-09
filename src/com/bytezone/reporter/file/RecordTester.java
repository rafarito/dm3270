package com.bytezone.reporter.file;

import java.util.ArrayList;
import java.util.List;

import com.bytezone.reporter.record.Record;
import com.bytezone.reporter.record.RecordMaker;
import com.bytezone.reporter.reports.ReportMaker;
import com.bytezone.reporter.text.TextMaker;

// -----------------------------------------------------------------------------------//
public class RecordTester
// -----------------------------------------------------------------------------------//
{
  private final RecordMaker recordMaker;
  private final List<Record> sampleRecords;

  private final List<TextTester> textTesters = new ArrayList<> ();
  private final List<ReportTester> reportTesters = new ArrayList<> ();

  // ---------------------------------------------------------------------------------//
  public RecordTester (RecordMaker recordMaker, int testSize)
  // ---------------------------------------------------------------------------------//
  {
    this.recordMaker = recordMaker;
    sampleRecords = recordMaker.createSampleRecords (testSize);
  }

  // ---------------------------------------------------------------------------------//
  public int countSampleRecords ()
  // ---------------------------------------------------------------------------------//
  {
    return sampleRecords.size ();
  }

  // ---------------------------------------------------------------------------------//
  public void testTextMaker (TextMaker textMaker)
  // ---------------------------------------------------------------------------------//
  {
    TextTester textTester = new TextTester (textMaker);
    textTesters.add (textTester);

    textTester.testRecords (sampleRecords);
  }

  // ---------------------------------------------------------------------------------//
  public TextMaker getPreferredTextMaker ()
  // ---------------------------------------------------------------------------------//
  {
    if (textTesters.isEmpty ())
      throw new IllegalStateException (
          "testTextMaker () precisa ser chamado antes de escolher a codificacao");

    double max = -1.0;
    TextTester bestTextTester = null;

    for (TextTester textTester : textTesters)
    {
      double ratio = textTester.getAlphanumericRatio ();

      // uma amostra vazia devolve NaN, que nunca e maior que max: sem esse teste
      // nenhum candidato seria escolhido e a comparacao caia no fallback
      if (Double.isNaN (ratio))
        continue;

      if (ratio > max)
      {
        max = ratio;
        bestTextTester = textTester;
      }
    }

    // amostra vazia em todos os candidatos: nao ha o que comparar, fica o primeiro
    return bestTextTester == null ? textTesters.get (0).getTextMaker ()
        : bestTextTester.getTextMaker ();
  }

  // ---------------------------------------------------------------------------------//
  public ReportScore testReportMaker (ReportMaker reportMaker, TextMaker textMaker)
  // ---------------------------------------------------------------------------------//
  {
    ReportTester reportTester = new ReportTester (reportMaker, textMaker);
    reportTesters.add (reportTester);

    reportTester.testRecords (sampleRecords);

    return new ReportScore (recordMaker, textMaker, reportMaker, reportTester.getRatio (),
        sampleRecords.size ());
  }
}