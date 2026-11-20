package com.bytezone.reporter.file;

import java.nio.charset.StandardCharsets;

import com.bytezone.reporter.record.LfRecordMaker;
import com.bytezone.reporter.record.RecordMaker;
import com.bytezone.reporter.reports.ReportMaker;
import com.bytezone.reporter.reports.TextReport;
import com.bytezone.reporter.text.AsciiTextMaker;
import com.bytezone.reporter.text.TextMaker;

// Fabrica de teste para expor o construtor package-private de ReportScore a outros pacotes.
// -----------------------------------------------------------------------------------//
public final class ReportScores
// -----------------------------------------------------------------------------------//
{
  public static final TextMaker ASCII = new AsciiTextMaker ();

  // ---------------------------------------------------------------------------------//
  private ReportScores ()
  // ---------------------------------------------------------------------------------//
  {
  }

  // ---------------------------------------------------------------------------------//
  public static RecordMaker lines (String text)
  // ---------------------------------------------------------------------------------//
  {
    LfRecordMaker recordMaker = new LfRecordMaker ();
    recordMaker.setBuffer (text.getBytes (StandardCharsets.ISO_8859_1));
    return recordMaker;
  }

  // ---------------------------------------------------------------------------------//
  public static ReportScore of (RecordMaker recordMaker, ReportMaker reportMaker,
      double score, int sampleSize)
  // ---------------------------------------------------------------------------------//
  {
    return new ReportScore (recordMaker, ASCII, reportMaker, score, sampleSize);
  }

  // ---------------------------------------------------------------------------------//
  public static ReportScore of (String text, ReportMaker reportMaker)
  // ---------------------------------------------------------------------------------//
  {
    return of (lines (text), reportMaker, 100.0, 1);
  }

  // ---------------------------------------------------------------------------------//
  public static ReportScore text (String text)
  // ---------------------------------------------------------------------------------//
  {
    return of (text, new TextReport (false, true));
  }
}
