package com.bytezone.reporter.file;

import java.nio.charset.StandardCharsets;

import com.bytezone.reporter.record.LfRecordMaker;
import com.bytezone.reporter.record.RecordMaker;
import com.bytezone.reporter.reports.ReportMaker;
import com.bytezone.reporter.reports.TextReport;
import com.bytezone.reporter.text.AsciiTextMaker;
import com.bytezone.reporter.text.TextMaker;

/*
 * Fabrica de ReportScore para os testes, e existe por um motivo de visibilidade.
 *
 * O construtor do ReportScore e de pacote - so o RecordTester o chama em producao -, entao um
 * teste fora de com.bytezone.reporter.file nao consegue montar um. O ReportScoreViewTest mora
 * em com.bytezone.reporter.application, junto da classe que ele testa, e precisa de um.
 *
 * Esta classe e o unico ponto que atravessa essa fronteira, e e de teste: nada em src/ mudou
 * de visibilidade para que a view pudesse ser testada.
 */
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

  // Um RecordMaker que quebra o texto em registros nos avancos de linha.
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

  // O caso comum: relatorio de texto, um registro por linha, sem linha em branco entre eles.
  // ---------------------------------------------------------------------------------//
  public static ReportScore text (String text)
  // ---------------------------------------------------------------------------------//
  {
    return of (text, new TextReport (false, true));
  }
}
