package com.bytezone.dm3270.watch;

import java.util.List;

import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.screen.Field;

/*
 * Uma linha por dataset, com a disposicao. Era o screenType 2.
 *
 * A linha de titulos tem tres campos e o do meio comeca com "Dsorg".
 *
 * Os deslocamentos sao 5/11/18, contra os 5/10/16 dos layouts de varias linhas.
 *
 * O espelho do TracksLayout: este ramo le disposicao e nao le espaco nem dispositivo.
 */
// -----------------------------------------------------------------------------------//
final class DsorgLayout implements DatasetListLayout
// -----------------------------------------------------------------------------------//
{
  private static final int DSORG = 5;
  private static final int RECFM = 11;
  private static final int LRECL = 18;

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

    DatasetDetails.setDisposition (summary, rowFields.get (1).getText (), DSORG, RECFM, LRECL);

    delta.setDisposition (summary.getDsorg (), summary.getRecfm (), summary.getLrecl (),
        summary.getBlksize ());
  }
}
