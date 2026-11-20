package com.bytezone.reporter.reports;

import java.util.List;

import com.bytezone.reporter.record.RecordMaker;
import com.bytezone.reporter.text.TextMaker;

// Contexto de paginacao e formatacao para relatorios, desacoplado de file.ReportScore.
// -----------------------------------------------------------------------------------//
public interface ReportContext
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  List<Page> getPages ();

  Page addPage (int firstRecord, int lastRecord);

  RecordMaker getRecordMaker ();

  TextMaker getTextMaker ();
}
