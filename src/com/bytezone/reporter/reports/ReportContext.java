package com.bytezone.reporter.reports;

import java.util.List;

import com.bytezone.reporter.record.RecordMaker;
import com.bytezone.reporter.text.TextMaker;

/*
 * O contexto em que um relatorio e paginado: as paginas ja montadas, o meio de acrescentar
 * mais uma, e os dois makers que dizem como os bytes viram registros e como os registros
 * viram texto.
 *
 * Isto e uma PORTA, declarada no pacote que CONSOME - e e isso, e so isso, que inverte a
 * dependencia. Ate este commit, os seis arquivos de reports que importavam reporter.file
 * importavam o ReportScore, e nada mais dele: uma classe de 226 linhas, com uma Pagination e
 * um TextArea dentro, guardada para chamar quatro coisas. Como o ReportScore precisa de
 * ReportMaker e de Page para existir, reports e file se referenciavam nos dois sentidos.
 *
 * A medicao, feita antes de escrever qualquer linha, foi o que definiu os quatro metodos
 * abaixo - sao exatamente os membros que os relatorios usavam:
 *
 *   getPages ()          AsaReport, HexReport, NatloadReport, TextReport
 *   addPage ()           AsaReport, HexReport
 *   getRecordMaker ()    os quatro
 *   getTextMaker ()      os quatro
 *
 * Nenhum deles tocava em pagination, textArea, score, sampleSize, weight, matches,
 * isPerfectScore, getFormattedPage ou compareTo. O ReportScore continua tendo tudo isso; ele
 * so deixou de entregar tudo isso a quem pediu quatro coisas.
 *
 * Quem implementa: file.ReportScore, e ninguem mais.
 */
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
