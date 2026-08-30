package com.bytezone.reporter.reports;

import java.awt.print.Printable;

import com.bytezone.reporter.record.Record;
import com.bytezone.reporter.text.TextMaker;

// -----------------------------------------------------------------------------------//
public interface ReportMaker extends Printable
// -----------------------------------------------------------------------------------//
{
  public boolean test (Record record, TextMaker textMaker);

  public void createPages (ReportContext context);

  public String getFormattedRecord (ReportContext context, Record record);

  public String getFormattedRecord (ReportContext context, Record record, int offset,
      int length);

  public boolean newlineBetweenRecords ();

  public boolean allowSplitRecords ();

  public double weight ();
}