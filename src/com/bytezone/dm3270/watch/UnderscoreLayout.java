package com.bytezone.dm3270.watch;

import java.util.List;

import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.screen.Field;

/*
 * Duas linhas por dataset, sem coluna de catalogo. Era o screenType 5.
 *
 * Reconhecido pela mesma linha de titulos de seis campos do CatalogLayout, mas com a linha 7
 * trazendo tracos em vez da palavra "Catalog". Duas linhas por dataset mais a linha de tracos
 * entre eles fazem a lista comecar na linha 8.
 *
 * As duas guardas sao escalonadas, e nao uma so: com tres campos le o volume e para ali; so
 * com seis entra em espaco, disposicao e datas. Uma tela truncada cai no primeiro caso.
 */
// -----------------------------------------------------------------------------------//
final class UnderscoreLayout extends MultiLineDatasetLayout
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @Override
  public int linesPerDataset ()
  // ---------------------------------------------------------------------------------//
  {
    return 2;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public int firstDataRow ()
  // ---------------------------------------------------------------------------------//
  {
    return 8;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void read (DatasetSummary summary, List<Field> rowFields, Dataset delta)
  // ---------------------------------------------------------------------------------//
  {
    if (rowFields.size () < 3)
      return;

    /*
     * O volume entra so no summary. Dos tres layouts que leem volume, este e o unico que nao o
     * grava no delta: o dado aparece na tabela do assistant e nunca chega ao banco. E o item 9
     * do BACKLOG-DEFEITOS.md, preservado de proposito - Regra 1 -, e ha dois testes que
     * quebram se alguem "consertar" isso sem querer.
     */
    summary.setVolume (rowFields.get (2).getText ().trim ());

    if (rowFields.size () < 6)
      return;

    readDetails (summary, rowFields, delta);
  }
}
