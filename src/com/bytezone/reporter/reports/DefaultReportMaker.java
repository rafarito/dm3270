package com.bytezone.reporter.reports;

import com.bytezone.reporter.record.Record;
import com.bytezone.reporter.text.TextMaker;

// -----------------------------------------------------------------------------------//
public abstract class DefaultReportMaker implements ReportMaker
// -----------------------------------------------------------------------------------//
{
  protected final String name;
  protected final boolean newlineBetweenRecords;
  protected final boolean allowSplitRecords;
  protected double weight = 1.0;

  protected int pageSize = 66;

  // ---------------------------------------------------------------------------------//
  public DefaultReportMaker (String name, boolean newLine, boolean split)
  // ---------------------------------------------------------------------------------//
  {
    this.name = name;
    this.newlineBetweenRecords = newLine;
    this.allowSplitRecords = split;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String getFormattedRecord (ReportContext context, Record record)
  // ---------------------------------------------------------------------------------//
  {
    return "Not possible";
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String getFormattedRecord (ReportContext context, Record record, int offset,
      int length)
  // ---------------------------------------------------------------------------------//
  {
    return "Not possible";
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public boolean test (Record record, TextMaker textMaker)
  // ---------------------------------------------------------------------------------//
  {
    return false;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public boolean newlineBetweenRecords ()
  // ---------------------------------------------------------------------------------//
  {
    return newlineBetweenRecords;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public boolean allowSplitRecords ()
  // ---------------------------------------------------------------------------------//
  {
    return allowSplitRecords;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public double weight ()
  // ---------------------------------------------------------------------------------//
  {
    return weight;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String toString ()
  // ---------------------------------------------------------------------------------//
  {
    return name;
  }
}