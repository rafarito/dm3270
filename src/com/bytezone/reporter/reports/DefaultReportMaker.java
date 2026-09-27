package com.bytezone.reporter.reports;

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