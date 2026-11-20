package com.bytezone.dm3270.watch;

import java.util.List;

import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.screen.Field;

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

    // Volume nao e gravado no delta aqui (comportamento legado preservado intencionalmente).
    summary.setVolume (rowFields.get (2).getText ().trim ());

    if (rowFields.size () < 6)
      return;

    readDetails (summary, rowFields, delta);
  }
}
