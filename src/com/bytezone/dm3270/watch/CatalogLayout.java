package com.bytezone.dm3270.watch;

import java.util.List;

import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.screen.Field;

// -----------------------------------------------------------------------------------//
final class CatalogLayout extends MultiLineDatasetLayout
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @Override
  public int linesPerDataset ()
  // ---------------------------------------------------------------------------------//
  {
    return 3;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public int firstDataRow ()
  // ---------------------------------------------------------------------------------//
  {
    return 9;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void read (DatasetSummary summary, List<Field> rowFields, Dataset delta)
  // ---------------------------------------------------------------------------------//
  {
    if (rowFields.size () != 7)
      return;

    summary.setVolume (rowFields.get (2).getText ().trim ());
    readDetails (summary, rowFields, delta);

    // O catalogo so entra se parecer nome de dataset - a coluna tambem carrega mensagens.
    String catalog = rowFields.get (6).getText ().trim ();
    if (ScreenWatcher.isDatasetName (catalog))
    {
      summary.setCatalog (catalog);
      delta.setCatalog (catalog);
    }

    delta.setVolume (summary.getVolume ());
    delta.setDevice (summary.getDevice ());
  }
}
