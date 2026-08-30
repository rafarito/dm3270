package com.bytezone.dm3270.watch;

import java.util.List;

import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.screen.Field;

/*
 * Uma linha por dataset, com espaco e dispositivo. Era o screenType 1.
 *
 * A linha de titulos tem tres campos e o do meio comeca com "Tracks".
 *
 * Os deslocamentos sao 6/11/15, e NAO os 6/10/14 dos layouts de varias linhas, que leem a
 * mesma informacao em colunas diferentes. Era essa diferenca - quatro conjuntos de numeros
 * para duas informacoes - que ficava invisivel enquanto os dois switch estavam separados.
 *
 * Este ramo nao le disposicao nenhuma: dsorg, recfm, lrecl e blksize ficam como estao, nos
 * dois destinos.
 */
// -----------------------------------------------------------------------------------//
final class TracksLayout implements DatasetListLayout
// -----------------------------------------------------------------------------------//
{
  private static final int TRACKS = 6;
  private static final int PERCENT_USED = 11;
  private static final int EXTENTS = 15;

  // ---------------------------------------------------------------------------------//
  @Override
  public int linesPerDataset ()
  // ---------------------------------------------------------------------------------//
  {
    return 1;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public int firstDataRow ()
  // ---------------------------------------------------------------------------------//
  {
    return 7;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void read (DatasetSummary summary, List<Field> rowFields, Dataset delta)
  // ---------------------------------------------------------------------------------//
  {
    if (rowFields.size () != 2)
      return;

    DatasetDetails.setSpace (summary, rowFields.get (1).getText (), TRACKS, PERCENT_USED,
        EXTENTS);

    delta.setSpace (summary.getTracks (), summary.getCylinders (), summary.getExtents (),
        summary.getPercentUsed ());
    delta.setDevice (summary.getDevice ());
  }
}
